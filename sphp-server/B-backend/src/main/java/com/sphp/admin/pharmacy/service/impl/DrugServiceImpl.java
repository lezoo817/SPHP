package com.sphp.admin.pharmacy.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.enums.BUserStatusEnum;
import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.pharmacy.dto.DrugCreateRequest;
import com.sphp.admin.pharmacy.dto.DrugListVO;
import com.sphp.admin.pharmacy.dto.DrugUpdateRequest;
import com.sphp.admin.pharmacy.entity.PharmacyDrugStock;
import com.sphp.admin.pharmacy.mapper.PharmacyDrugStockMapper;
import com.sphp.admin.pharmacy.service.DrugService;
import com.sphp.admin.prescription.entity.Drug;
import com.sphp.admin.prescription.mapper.DrugMapper;
import com.sphp.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 药品目录服务实现（管理员视角）。
 *
 * <p>按当前登录管理员所属医院（{@code hospital_id}）做数据隔离过滤；
 * 药品状态字段与 {@link BUserStatusEnum} 复用（仅 ENABLED / DISABLED 两态，
 * 与 {@code b_user.status} / {@code doctor.status} 语义一致），
 * 业务代码严禁直接使用字面量比较。
 *
 * <p>状态机：
 * <ul>
 *   <li>新增：默认 {@link BUserStatusEnum#ENABLED}</li>
 *   <li>删除：先软删（{@code deleted_at}），再将 status 同步置为 {@link BUserStatusEnum#DISABLED}，
 *       避免历史关联出现"幽灵启用"药品</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrugServiceImpl implements DrugService {

    /** 通用业务冲突（A0401：请求参数或业务前置条件不满足） */
    private static final String ERR_BUSINESS_CONFLICT = "A0401";
    /** 资源不存在 / 越权访问（A0402：通用资源未找到） */
    private static final String ERR_DRUG_NOT_FOUND = "A0402";

    /** 药品单位默认值（前端缺省时使用） */
    private static final String DEFAULT_UNIT = "盒";

    private final DrugMapper drugMapper;
    private final PharmacyDrugStockMapper stockMapper;
    private final CurrentUserService currentUserService;

    @Override
    public PageResult<DrugListVO> page(String name, String status, int page, int size) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Page<Drug> result = drugMapper.selectPage(new Page<>(page, size),
                Wrappers.<Drug>lambdaQuery()
                        .eq(Drug::getHospitalId, hospitalId)
                        .like(StringUtils.hasText(name), Drug::getName, name)
                        .eq(StringUtils.hasText(status), Drug::getStatus, status)
                        .isNull(Drug::getDeletedAt)
                        .orderByDesc(Drug::getId));
        List<DrugListVO> list = result.getRecords().stream()
                .map(this::toDrugListVO)
                .toList();
        return PageResult.of(result.getTotal(), list, page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(DrugCreateRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        String unit = StringUtils.hasText(request.getUnit()) ? request.getUnit().trim() : DEFAULT_UNIT;
        String status = StringUtils.hasText(request.getStatus()) ? request.getStatus().trim() : BUserStatusEnum.ENABLED.getCode();
        assertStatusValid(status);
        assertApprovalUnique(hospitalId, request.getApprovalNumber().trim(), null);

        Drug drug = new Drug();
        drug.setHospitalId(hospitalId);
        drug.setName(request.getName().trim());
        drug.setSpecification(request.getSpecification().trim());
        drug.setUnit(unit);
        drug.setIndication(request.getIndication());
        drug.setContraindication(request.getContraindication());
        drug.setManufacturer(request.getManufacturer());
        drug.setApprovalNumber(request.getApprovalNumber().trim());
        drug.setStatus(status);
        drugMapper.insert(drug);
        log.info("新增药品 drugId={}, name={}, hospitalId={}", drug.getId(), drug.getName(), hospitalId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DrugUpdateRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Drug drug = getDrugInHospital(id, hospitalId);
        if (StringUtils.hasText(request.getName())) drug.setName(request.getName().trim());
        if (StringUtils.hasText(request.getSpecification())) drug.setSpecification(request.getSpecification().trim());
        if (StringUtils.hasText(request.getUnit())) drug.setUnit(request.getUnit().trim());
        if (request.getIndication() != null) drug.setIndication(request.getIndication());
        if (request.getContraindication() != null) drug.setContraindication(request.getContraindication());
        if (request.getManufacturer() != null) drug.setManufacturer(request.getManufacturer());
        if (StringUtils.hasText(request.getApprovalNumber())) {
            assertApprovalUnique(hospitalId, request.getApprovalNumber().trim(), id);
            drug.setApprovalNumber(request.getApprovalNumber().trim());
        }
        if (StringUtils.hasText(request.getStatus())) {
            String status = request.getStatus().trim();
            assertStatusValid(status);
            drug.setStatus(status);
        }
        drug.setUpdatedAt(OffsetDateTime.now());
        drugMapper.updateById(drug);
        log.info("编辑药品 drugId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Drug drug = getDrugInHospital(id, hospitalId);
        // 存在库存记录时拒绝删除，避免库存出现孤儿药品
        Long stockCount = stockMapper.selectCount(
                Wrappers.<PharmacyDrugStock>lambdaQuery()
                        .eq(PharmacyDrugStock::getDrugId, id)
                        .apply("pharmacy_id IN (SELECT id FROM pharmacy WHERE hospital_id = {0} AND deleted_at IS NULL)",
                                hospitalId));
        if (stockCount != null && stockCount > 0) {
            throw new BusinessException(ERR_BUSINESS_CONFLICT, "该药品存在库存记录，无法删除");
        }
        drug.setDeletedAt(OffsetDateTime.now());
        drug.setStatus(BUserStatusEnum.DISABLED.getCode());
        drug.setUpdatedAt(OffsetDateTime.now());
        drugMapper.updateById(drug);
        log.info("删除药品 drugId={}", id);
    }

    private DrugListVO toDrugListVO(Drug d) {
        return DrugListVO.builder()
                .id(d.getId())
                .name(d.getName())
                .specification(d.getSpecification())
                .unit(d.getUnit())
                .indication(d.getIndication())
                .contraindication(d.getContraindication())
                .manufacturer(d.getManufacturer())
                .approvalNumber(d.getApprovalNumber())
                .status(d.getStatus())
                .build();
    }

    /**
     * 按 id + 医院范围查询药品，不存在、已软删或越权返回 {@value #ERR_DRUG_NOT_FOUND}。
     *
     * @param id         药品 ID
     * @param hospitalId 当前管理员所属医院 ID
     * @return 有效且属于本院的药品实体
     * @throws BusinessException 当药品不存在、已软删或归属其他医院时抛出
     */
    private Drug getDrugInHospital(Long id, Long hospitalId) {
        Drug drug = drugMapper.selectById(id);
        if (drug == null || drug.getDeletedAt() != null || !drug.getHospitalId().equals(hospitalId)) {
            throw new BusinessException(ERR_DRUG_NOT_FOUND, "药品不存在");
        }
        return drug;
    }

    /**
     * 同医院 + 批准文号唯一性校验。编辑时需传入待排除的药品 ID。
     *
     * @param hospitalId     医院 ID
     * @param approvalNumber 批准文号
     * @param excludeId      编辑时排除自身 ID；新增时为 {@code null}
     * @throws BusinessException 当同医院存在相同批准文号时抛出 {@value #ERR_BUSINESS_CONFLICT}
     */
    private void assertApprovalUnique(Long hospitalId, String approvalNumber, Long excludeId) {
        Long dup = drugMapper.selectCount(
                Wrappers.<Drug>lambdaQuery()
                        .eq(Drug::getHospitalId, hospitalId)
                        .eq(Drug::getApprovalNumber, approvalNumber)
                        .ne(excludeId != null, Drug::getId, excludeId)
                        .isNull(Drug::getDeletedAt));
        if (dup != null && dup > 0) {
            throw new BusinessException(ERR_BUSINESS_CONFLICT, "同医院已存在相同批准文号的药品");
        }
    }

    /**
     * 校验药品状态字段合法性，仅允许 {@link BUserStatusEnum} 中的两个取值。
     *
     * @param status 待校验状态字符串
     * @throws BusinessException 状态不在白名单时抛出 {@value #ERR_BUSINESS_CONFLICT}
     */
    private void assertStatusValid(String status) {
        if (!BUserStatusEnum.ENABLED.getCode().equals(status) && !BUserStatusEnum.DISABLED.getCode().equals(status)) {
            throw new BusinessException(ERR_BUSINESS_CONFLICT,
                    "状态仅支持 " + BUserStatusEnum.ENABLED.getCode() + " / " + BUserStatusEnum.DISABLED.getCode());
        }
    }
}
