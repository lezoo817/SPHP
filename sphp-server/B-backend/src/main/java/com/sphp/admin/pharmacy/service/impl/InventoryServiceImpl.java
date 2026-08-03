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
import com.sphp.admin.pharmacy.mapper.DrugOrderItemMapper;
import com.sphp.admin.pharmacy.mapper.DrugOrderMapper;
import com.sphp.admin.pharmacy.mapper.PharmacyDrugStockMapper;
import com.sphp.admin.pharmacy.mapper.PharmacyMapper;
import com.sphp.admin.pharmacy.service.InventoryService;
import com.sphp.admin.prescription.entity.Drug;
import com.sphp.admin.prescription.mapper.DrugMapper;
import com.sphp.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 药品库存管理服务实现（系分 §5.7.3~5.7.6）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final PharmacyDrugStockMapper stockMapper;
    private final PharmacyMapper pharmacyMapper;
    private final DrugOrderMapper drugOrderMapper;
    private final DrugOrderItemMapper drugOrderItemMapper;
    private final DrugMapper drugMapper;
    private final CurrentUserService currentUserService;

    @Override
    public PageResult<InventoryListVO> page(Long drugId, Long pharmacyId, int page, int size) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Page<PharmacyDrugStock> result = stockMapper.selectPage(new Page<>(page, size),
                Wrappers.<PharmacyDrugStock>lambdaQuery()
                        .apply("pharmacy_id IN (SELECT id FROM pharmacy WHERE hospital_id = {0} AND deleted_at IS NULL)",
                                hospitalId)
                        .eq(drugId != null, PharmacyDrugStock::getDrugId, drugId)
                        .eq(pharmacyId != null, PharmacyDrugStock::getPharmacyId, pharmacyId)
                        .orderByDesc(PharmacyDrugStock::getId));
        Map<Long, Drug> drugMap = loadDrugMap(result.getRecords().stream()
                .map(PharmacyDrugStock::getDrugId).toList());
        List<InventoryListVO> list = result.getRecords().stream()
                .map(s -> {
                    Drug d = drugMap.get(s.getDrugId());
                    return InventoryListVO.builder()
                            .id(s.getId())
                            .drugId(s.getDrugId())
                            .drugName(d != null ? d.getName() : null)
                            .specification(d != null ? d.getSpecification() : null)
                            .availableCount(s.getAvailableCount())
                            .lockedCount(s.getLockedCount())
                            .safetyStock(s.getSafetyStock())
                            .unitPriceCent(s.getUnitPriceCent())
                            .status(computeStatus(s.getAvailableCount()))
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
        List<PharmacyDrugStock> rows = stockMapper.selectList(
                Wrappers.<PharmacyDrugStock>lambdaQuery()
                        .apply("pharmacy_id IN (SELECT id FROM pharmacy WHERE hospital_id = {0} AND deleted_at IS NULL)",
                                hospitalId)
                        .eq(pharmacyId != null, PharmacyDrugStock::getPharmacyId, pharmacyId)
                        .apply("available_count < safety_stock")
                        .orderByAsc(PharmacyDrugStock::getAvailableCount));
        Map<Long, Drug> drugMap = loadDrugMap(rows.stream().map(PharmacyDrugStock::getDrugId).toList());
        return rows.stream()
                .map(s -> {
                    Drug d = drugMap.get(s.getDrugId());
                    return InventoryAlertVO.builder()
                            .id(s.getId())
                            .drugId(s.getDrugId())
                            .drugName(d != null ? d.getName() : null)
                            .specification(d != null ? d.getSpecification() : null)
                            .availableCount(s.getAvailableCount())
                            .safetyStock(s.getSafetyStock())
                            .unitPriceCent(s.getUnitPriceCent())
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
            throw new BusinessException("A0402", "当前库存无锁定可释放");
        }

        // 近似预校验：订单存在且未软删、与库存同药房、含该药品明细
        DrugOrder order = drugOrderMapper.selectById(request.getDrugOrderId());
        if (order == null || order.getDeletedAt() != null) {
            throw new BusinessException("A0402", "购药订单不存在");
        }
        if (!order.getPharmacyId().equals(stock.getPharmacyId())) {
            throw new BusinessException("A0401", "购药订单与库存药房不一致，无法释放");
        }
        DrugOrderItem item = drugOrderItemMapper.selectOne(
                Wrappers.<DrugOrderItem>lambdaQuery()
                        .eq(DrugOrderItem::getDrugOrderId, order.getId())
                        .eq(DrugOrderItem::getDrugId, stock.getDrugId())
                        .last("LIMIT 1"));
        if (item == null) {
            throw new BusinessException("A0401", "购药订单未锁定该药品，无法释放");
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

    /** 库存状态：>=10 NORMAL；4~9 LOW；<=3 ALERT */
    private String computeStatus(int availableCount) {
        if (availableCount >= 10) return "NORMAL";
        if (availableCount >= 4) return "LOW";
        return "ALERT";
    }

    private void assertNonNegative(Integer value, String field) {
        if (value < 0) throw new BusinessException("A0401", field + "不能为负");
    }

    /** 批量加载药品信息（去重，剔除软删药品） */
    private Map<Long, Drug> loadDrugMap(List<Long> drugIds) {
        List<Long> ids = drugIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return drugMapper.selectBatchIds(ids).stream()
                .filter(d -> d.getDeletedAt() == null)
                .collect(Collectors.toMap(Drug::getId, d -> d, (a, b) -> a));
    }

    /** 按 id + 医院范围查询库存（经 pharmacy.hospital_id），不存在或越权返回 A0402 */
    private PharmacyDrugStock getStockInHospital(Long id, Long hospitalId) {
        PharmacyDrugStock stock = stockMapper.selectById(id);
        if (stock == null) throw new BusinessException("A0402", "库存记录不存在");
        Pharmacy pharmacy = pharmacyMapper.selectById(stock.getPharmacyId());
        if (pharmacy == null || pharmacy.getDeletedAt() != null
                || !pharmacy.getHospitalId().equals(hospitalId)) {
            throw new BusinessException("A0402", "库存记录不存在");
        }
        return stock;
    }
}
