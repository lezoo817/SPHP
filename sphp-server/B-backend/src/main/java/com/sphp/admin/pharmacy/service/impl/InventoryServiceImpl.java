package com.sphp.admin.pharmacy.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.pharmacy.dto.InventoryAlertVO;
import com.sphp.admin.pharmacy.dto.InventoryListVO;
import com.sphp.admin.pharmacy.dto.InventoryUnlockRequest;
import com.sphp.admin.pharmacy.dto.InventoryUpdateRequest;
import com.sphp.admin.pharmacy.entity.DrugOrder;
import com.sphp.admin.pharmacy.entity.DrugOrderItem;
import com.sphp.admin.pharmacy.entity.Pharmacy;
import com.sphp.admin.pharmacy.entity.PharmacyDrugStock;

import com.sphp.admin.pharmacy.mapper.*;

import com.sphp.admin.pharmacy.service.InventoryService;
import com.sphp.admin.prescription.entity.Drug;
import com.sphp.admin.prescription.mapper.DrugMapper;
import com.sphp.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 药品库存管理服务实现（管理员视角）。
 *
 * <p>按当前登录管理员所属医院（{@code hospital_id}）做数据隔离过滤；
 * 库存按药房归属（{@code pharmacy.hospital_id}）间接过滤（药房实体不在本模块）。
 *
 * <p>库存派生状态机（由 {@code available_count} 与 {@code safety_stock} 比值计算）：
 * <ul>
 *   <li>NORMAL：{@code safety_stock <= 0} 或 {@code available >= safety * NORMAL_RATIO}</li>
 *   <li>LOW：{@code available >= safety} 且 {@code available < safety * NORMAL_RATIO}</li>
 *   <li>ALERT：{@code available < safety}</li>
 * </ul>
 *
 * <p>释放锁定采用"近似预校验"策略：仅校验订单存在、归属同药房、含该药品明细，
 * 不锁单条订单行；最终释放量取订单数量与 {@code locked_count} 较小者兜底。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    /** 通用业务冲突（A0401：请求参数或业务前置条件不满足） */
    private static final String ERR_BUSINESS_CONFLICT = "A0401";
    /** 资源不存在 / 越权访问（A0402：通用资源未找到） */
    private static final String ERR_RESOURCE_NOT_FOUND = "A0402";

    /** 库存派生状态：充足 */
    private static final String INVENTORY_STATUS_NORMAL = "NORMAL";
    /** 库存派生状态：偏低（介于安全库存与 2 倍之间） */
    private static final String INVENTORY_STATUS_LOW = "LOW";
    /** 库存派生状态：告警（低于安全库存） */
    private static final String INVENTORY_STATUS_ALERT = "ALERT";

    /** NORMAL 阈值：{@code available / safety >= NORMAL_RATIO} 视为充足 */
    private static final double NORMAL_RATIO = 2.0;
    /** LOW 阈值：{@code available / safety >= 1.0} 视为偏低（低于此值即 ALERT） */
    private static final double LOW_RATIO = 1.0;

    private final PharmacyDrugStockMapper stockMapper;
    private final PharmacyMapper pharmacyMapper;

    private final BDrugOrderMapper drugOrderMapper;
    private final BDrugOrderItemMapper drugOrderItemMapper;

    private final DrugMapper drugMapper;
    private final CurrentUserService currentUserService;

    @Override
    public PageResult<InventoryListVO> page(Long drugId, Long pharmacyId, int page, int size) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Map<Long, String> pharmacyNameMap = loadPharmacyNameMap(hospitalId);

        // pharmacyId 为空时，按药品汇总全部药房库存
        if (pharmacyId == null) {
            List<Map<String, Object>> rows = stockMapper.selectAggregatedByHospital(hospitalId);
            // 手动分页
            int total = rows.size();
            int from = (page - 1) * size;
            int to = Math.min(from + size, total);
            List<InventoryListVO> list = rows.subList(from, to).stream()
                    .map(this::toInventoryListVOFromMap)
                    .toList();
            return PageResult.of(total, list, page, size);
        }

        // 指定药房时，按药房查询
        Page<PharmacyDrugStock> result = stockMapper.selectPage(new Page<>(page, size),
                Wrappers.<PharmacyDrugStock>lambdaQuery()
                        .apply("pharmacy_id IN (SELECT id FROM pharmacy WHERE hospital_id = {0} AND deleted_at IS NULL)",
                                hospitalId)
                        .eq(drugId != null, PharmacyDrugStock::getDrugId, drugId)
                        .eq(PharmacyDrugStock::getPharmacyId, pharmacyId)
                        .orderByDesc(PharmacyDrugStock::getId));
        Map<Long, Drug> drugMap = loadDrugMap(result.getRecords().stream()
                .map(PharmacyDrugStock::getDrugId).toList());
        String pharmacyName = pharmacyNameMap.getOrDefault(pharmacyId, null);
        List<InventoryListVO> list = result.getRecords().stream()
                .map(s -> {
                    Drug d = drugMap.get(s.getDrugId());
                    return InventoryListVO.builder()
                            .id(s.getId())
                            .pharmacyId(pharmacyId)
                            .pharmacyName(pharmacyName)
                            .drugId(s.getDrugId())
                            .drugName(d != null ? d.getName() : null)
                            .specification(d != null ? d.getSpecification() : null)
                            .availableCount(s.getAvailableCount())
                            .lockedCount(s.getLockedCount())
                            .safetyStock(s.getSafetyStock())
                            .unitPriceCent(s.getUnitPriceCent())
                            .status(computeStatus(s.getAvailableCount(), s.getSafetyStock()))
                            .build();
                })
                .toList();
        return PageResult.of(result.getTotal(), list, page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, InventoryUpdateRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        PharmacyDrugStock stock = getStockInHospital(id, hospitalId);
        if (request.getAvailableCount() != null) {
            assertNonNegative(request.getAvailableCount(), "可售库存");
            stock.setAvailableCount(request.getAvailableCount());
        }
        if (request.getLockedCount() != null) {
            assertNonNegative(request.getLockedCount(), "锁定库存");
            stock.setLockedCount(request.getLockedCount());
        }
        if (request.getSafetyStock() != null) {
            assertNonNegative(request.getSafetyStock(), "安全库存");
            stock.setSafetyStock(request.getSafetyStock());
        }
        if (request.getUnitPriceCent() != null) {
            assertNonNegative(request.getUnitPriceCent(), "单价");
            stock.setUnitPriceCent(request.getUnitPriceCent());
        }
        stock.setUpdatedAt(OffsetDateTime.now());
        stockMapper.updateById(stock);
        log.info("更新库存 stockId={}", id);
    }

    @Override
    public List<InventoryAlertVO> alerts(Long pharmacyId) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Map<Long, String> pharmacyNameMap = loadPharmacyNameMap(hospitalId);
        List<PharmacyDrugStock> rows = stockMapper.selectList(
                Wrappers.<PharmacyDrugStock>lambdaQuery()
                        .apply("pharmacy_id IN (SELECT id FROM pharmacy WHERE hospital_id = {0} AND deleted_at IS NULL)",
                                hospitalId)
                        .eq(pharmacyId != null, PharmacyDrugStock::getPharmacyId, pharmacyId)
                        // 告警(ALERT) + 偏低(LOW) 都展示：available < safety * NORMAL_RATIO
                        // 阈值常量 NORMAL_RATIO 与 computeStatus 共用，SQL 表达式内联不可避免
                        .apply("available_count < safety_stock * {0}", NORMAL_RATIO)
                        .orderByAsc(PharmacyDrugStock::getAvailableCount));
        Map<Long, Drug> drugMap = loadDrugMap(rows.stream().map(PharmacyDrugStock::getDrugId).toList());
        return rows.stream()
                .map(s -> {
                    Drug d = drugMap.get(s.getDrugId());
                    Long pid = s.getPharmacyId();
                    return InventoryAlertVO.builder()
                            .id(s.getId())
                            .pharmacyId(pid)
                            .pharmacyName(pharmacyNameMap.get(pid))
                            .drugId(s.getDrugId())
                            .drugName(d != null ? d.getName() : null)
                            .specification(d != null ? d.getSpecification() : null)
                            .availableCount(s.getAvailableCount())
                            .lockedCount(s.getLockedCount())
                            .safetyStock(s.getSafetyStock())
                            .unitPriceCent(s.getUnitPriceCent())
                            .status(computeStatus(s.getAvailableCount(), s.getSafetyStock()))
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlock(Long id, InventoryUnlockRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        PharmacyDrugStock stock = getStockInHospital(id, hospitalId);
        if (stock.getLockedCount() == null || stock.getLockedCount() <= 0) {
            throw new BusinessException(ERR_RESOURCE_NOT_FOUND, "当前库存无锁定可释放");
        }

        // 近似预校验：订单存在且未软删、与库存同药房、含该药品明细
        DrugOrder order = drugOrderMapper.selectById(request.getDrugOrderId());
        if (order == null || order.getDeletedAt() != null) {
            throw new BusinessException(ERR_RESOURCE_NOT_FOUND, "购药订单不存在");
        }
        if (!order.getPharmacyId().equals(stock.getPharmacyId())) {
            throw new BusinessException(ERR_BUSINESS_CONFLICT, "购药订单与库存药房不一致，无法释放");
        }
        DrugOrderItem item = drugOrderItemMapper.selectOne(
                Wrappers.<DrugOrderItem>lambdaQuery()
                        .eq(DrugOrderItem::getDrugOrderId, order.getId())
                        .eq(DrugOrderItem::getDrugId, stock.getDrugId())
                        .last("LIMIT 1"));
        if (item == null) {
            throw new BusinessException(ERR_BUSINESS_CONFLICT, "购药订单未锁定该药品，无法释放");
        }

        // 释放量 = 订单该药品数量，上限 locked_count 兜底
        int release = Math.min(item.getQuantity(), stock.getLockedCount());
        stock.setLockedCount(stock.getLockedCount() - release);
        stock.setAvailableCount(stock.getAvailableCount() + release);
        stock.setUpdatedAt(OffsetDateTime.now());
        stockMapper.updateById(stock);
        log.info("释放锁定库存 stockId={}, drugOrderId={}, release={}, reason={}",
                id, request.getDrugOrderId(), release, request.getReason());
    }

    /**
     * 批量加载药房名称映射。包含全部药房（不限状态）以便按需在内存中按 id 取名。
     *
     * @param hospitalId 医院 ID
     * @return 药房 ID → 药房名称
     */
    private Map<Long, String> loadPharmacyNameMap(Long hospitalId) {
        List<Pharmacy> pharmacies = pharmacyMapper.selectList(
                Wrappers.<Pharmacy>lambdaQuery()
                        .eq(Pharmacy::getHospitalId, hospitalId)
                        .isNull(Pharmacy::getDeletedAt));
        Map<Long, String> map = new HashMap<>();
        for (Pharmacy p : pharmacies) {
            map.put(p.getId(), p.getName());
        }
        return map;
    }

    /**
     * 将按药品聚合查询的原生结果 Map 转为 {@link InventoryListVO}。
     *
     * <p>聚合视图不含具体库存行 ID 与药房 ID，列表仅展示汇总字段。
     *
     * @param row 聚合查询结果行
     * @return 列表行 VO
     */
    private InventoryListVO toInventoryListVOFromMap(Map<String, Object> row) {
        int available = toInt(row.get("available_count"));
        return InventoryListVO.builder()
                .id(null)
                .pharmacyId(null)
                .pharmacyName(null)
                .drugId(toLong(row.get("drug_id")))
                .drugName((String) row.get("drug_name"))
                .specification((String) row.get("specification"))
                .availableCount(available)
                .lockedCount(toInt(row.get("locked_count")))
                .safetyStock(toInt(row.get("safety_stock")))
                .unitPriceCent(toInt(row.get("unit_price_cent")))
                .status(computeStatus(available, toInt(row.get("safety_stock"))))
                .build();
    }

    private Long toLong(Object val) {
        return val instanceof Number ? ((Number) val).longValue() : null;
    }

    private int toInt(Object val) {
        return val instanceof Number ? ((Number) val).intValue() : 0;
    }

    /**
     * 计算库存派生状态。{@code safety_stock <= 0} 时视为"未设安全库存"→ NORMAL，
     * 避免历史脏数据触发误报警。
     *
     * @param availableCount 可售库存
     * @param safetyStock    安全库存
     * @return 库存状态码：{@link #INVENTORY_STATUS_NORMAL} / {@link #INVENTORY_STATUS_LOW} / {@link #INVENTORY_STATUS_ALERT}
     */
    private String computeStatus(int availableCount, int safetyStock) {
        if (safetyStock <= 0) return INVENTORY_STATUS_NORMAL;
        double ratio = (double) availableCount / safetyStock;
        if (ratio >= NORMAL_RATIO) return INVENTORY_STATUS_NORMAL;
        if (ratio >= LOW_RATIO) return INVENTORY_STATUS_LOW;
        return INVENTORY_STATUS_ALERT;
    }

    /**
     * 校验库存数值字段非负。
     *
     * @param value 待校验值
     * @param field 字段中文名（用于错误消息）
     * @throws BusinessException 当值为负时抛出 {@value #ERR_BUSINESS_CONFLICT}
     */
    private void assertNonNegative(Integer value, String field) {
        if (value < 0) throw new BusinessException(ERR_BUSINESS_CONFLICT, field + "不能为负");
    }

    /**
     * 批量加载药品信息映射（去重、剔除软删药品，重复 ID 保留首条）。
     *
     * @param drugIds 药品 ID 列表（允许 null 与重复）
     * @return 药品 ID → 药品实体；空入参返回空 Map
     */
    private Map<Long, Drug> loadDrugMap(List<Long> drugIds) {
        List<Long> ids = drugIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return drugMapper.selectBatchIds(ids).stream()
                .filter(d -> d.getDeletedAt() == null)
                .collect(Collectors.toMap(Drug::getId, d -> d, (a, b) -> a));
    }

    /**
     * 按 id + 医院范围查询库存记录（经 {@code pharmacy.hospital_id} 间接过滤），
     * 不存在、药房已软删或药房归属其他医院时统一返回 {@value #ERR_RESOURCE_NOT_FOUND}。
     *
     * @param id         库存 ID
     * @param hospitalId 当前管理员所属医院 ID
     * @return 有效且属于本院的库存记录
     * @throws BusinessException 当库存不存在、药房缺失/软删或药房归属其他医院时抛出
     */
    private PharmacyDrugStock getStockInHospital(Long id, Long hospitalId) {
        PharmacyDrugStock stock = stockMapper.selectById(id);
        if (stock == null) throw new BusinessException(ERR_RESOURCE_NOT_FOUND, "库存记录不存在");
        Pharmacy pharmacy = pharmacyMapper.selectById(stock.getPharmacyId());
        if (pharmacy == null || pharmacy.getDeletedAt() != null
                || !pharmacy.getHospitalId().equals(hospitalId)) {
            throw new BusinessException(ERR_RESOURCE_NOT_FOUND, "库存记录不存在");
        }
        return stock;
    }
}
