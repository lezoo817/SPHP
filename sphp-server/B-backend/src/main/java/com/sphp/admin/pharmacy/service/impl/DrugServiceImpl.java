package com.sphp.admin.pharmacy.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.common.CurrentUserService;
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

/** 药品目录服务实现（系分 §5.7.1~5.7.2）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrugServiceImpl implements DrugService {

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
        String unit = StringUtils.hasText(request.getUnit()) ? request.getUnit().trim() : "盒";
        String status = StringUtils.hasText(request.getStatus()) ? request.getStatus().trim() : "ENABLED";
        assertStatusValid(status);
        assertApprovalUnique(hospitalId, request.getApprovalNumber().trim(), null);

        Drug drug = new Drug();
        drug.setHospitalId(hospitalId);
        drug.setName(request.getName().trim());
        drug.setSpecification(request.getSpecification().trim());
        drug.setUnit(unit);
        drug.setIndication(request.getIndication());
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
        if (request.getManufacturer() != null) drug.setManufacturer(request.getManufacturer());
        if (StringUtils.hasText(request.getApprovalNumber())) {
            assertApprovalUnique(hospitalId, request.getApprovalNumber().trim(), id);
            drug.setApprovalNumber(request.getApprovalNumber().trim());
        }
        if (StringUtils.hasText(request.getStatus())) {
            assertStatusValid(request.getStatus().trim());
            drug.setStatus(request.getStatus().trim());
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
            throw new BusinessException("A0401", "该药品存在库存记录，无法删除");
        }
        drug.setDeletedAt(OffsetDateTime.now());
        drug.setStatus("DISABLED");
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
                .manufacturer(d.getManufacturer())
                .approvalNumber(d.getApprovalNumber())
                .status(d.getStatus())
                .build();
    }

    /** 按 id + 医院范围查询药品，不存在或越权返回 A0402 */
    private Drug getDrugInHospital(Long id, Long hospitalId) {
        Drug drug = drugMapper.selectById(id);
        if (drug == null || drug.getDeletedAt() != null || !drug.getHospitalId().equals(hospitalId)) {
            throw new BusinessException("A0402", "药品不存在");
        }
        return drug;
    }

    /** 同医院 + 批准文号唯一性校验（编辑时排除自身） */
    private void assertApprovalUnique(Long hospitalId, String approvalNumber, Long excludeId) {
        Long dup = drugMapper.selectCount(
                Wrappers.<Drug>lambdaQuery()
                        .eq(Drug::getHospitalId, hospitalId)
                        .eq(Drug::getApprovalNumber, approvalNumber)
                        .ne(excludeId != null, Drug::getId, excludeId)
                        .isNull(Drug::getDeletedAt));
        if (dup != null && dup > 0) {
            throw new BusinessException("A0401", "同医院已存在相同批准文号的药品");
        }
    }

    private void assertStatusValid(String status) {
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw new BusinessException("A0401", "状态仅支持 ENABLED / DISABLED");
        }
    }
}
