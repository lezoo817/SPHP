package com.sphp.admin.prescription.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.auth.entity.Doctor;
import com.sphp.admin.auth.mapper.DoctorMapper;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.DataScope;
import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.hospital.entity.Department;
import com.sphp.admin.hospital.mapper.DepartmentMapper;
import com.sphp.admin.prescription.dto.SaveTemplateRequest;
import com.sphp.admin.prescription.dto.TemplateItemDTO;
import com.sphp.admin.prescription.dto.TemplateListVO;
import com.sphp.admin.prescription.entity.Drug;
import com.sphp.admin.prescription.entity.PrescriptionTemplate;
import com.sphp.admin.prescription.mapper.DrugMapper;
import com.sphp.admin.prescription.mapper.PrescriptionTemplateMapper;
import com.sphp.admin.prescription.service.PrescriptionTemplateService;
import com.sphp.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 处方模板服务实现（系分 §5.6.6 ~ §5.6.7）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionTemplateServiceImpl implements PrescriptionTemplateService {

    private final PrescriptionTemplateMapper templateMapper;
    private final DrugMapper drugMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final CurrentUserService currentUserService;

    @Override
    public PageResult<TemplateListVO> page(String name, Long deptId, int page, int size) {
        DataScope scope = currentUserService.getCurrentDataScope();

        LambdaQueryWrapper<PrescriptionTemplate> wrapper = Wrappers.<PrescriptionTemplate>lambdaQuery()
                .eq(PrescriptionTemplate::getHospitalId, scope.hospitalId())
                .eq(PrescriptionTemplate::getStatus, "ENABLED")
                .isNull(PrescriptionTemplate::getDeletedAt)
                .like(StringUtils.hasText(name), PrescriptionTemplate::getName, name)
                // deptId 为空时返回全部（包括全院通用模板），指定时返回该科室模板 + 全院通用模板
                .and(deptId != null, w ->
                        w.eq(PrescriptionTemplate::getDeptId, deptId)
                                .or().isNull(PrescriptionTemplate::getDeptId));
        wrapper.orderByDesc(PrescriptionTemplate::getCreatedAt);

        Page<PrescriptionTemplate> result = templateMapper.selectPage(new Page<>(page, size), wrapper);
        List<TemplateListVO> list = result.getRecords().stream()
                .map(this::toTemplateListVO)
                .toList();
        return PageResult.of(result.getTotal(), list, page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TemplateListVO save(SaveTemplateRequest request) {
        DataScope scope = currentUserService.getCurrentDataScope();
        Long doctorId = scope.doctorId();
        if (doctorId == null) {
            throw new BusinessException("A0443", "当前用户无医生身份，无法创建模板");
        }

        if (!StringUtils.hasText(request.getName())) {
            throw new BusinessException("A0401", "模板名称不能为空");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException("A0401", "模板药品明细不能为空");
        }

        // 校验关联科室属于当前医院（空表示全院通用）
        if (request.getDeptId() != null) {
            Department dept = departmentMapper.selectById(request.getDeptId());
            if (dept == null || dept.getDeletedAt() != null
                    || !dept.getHospitalId().equals(scope.hospitalId())) {
                throw new BusinessException("A0401", "科室不存在或不属于当前医院");
            }
        }

        // 防同名模板重复（同医院、未删除）
        Long duplicate = templateMapper.selectCount(
                Wrappers.<PrescriptionTemplate>lambdaQuery()
                        .eq(PrescriptionTemplate::getHospitalId, scope.hospitalId())
                        .eq(PrescriptionTemplate::getName, request.getName().trim())
                        .isNull(PrescriptionTemplate::getDeletedAt));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException("A0401", "同医院已存在同名模板");
        }

        // 批量查询药品，校验存在且启用，并回填药品名称冗余存入 JSONB
        Set<Long> drugIds = request.getItems().stream()
                .map(SaveTemplateRequest.ItemDTO::getDrugId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        Map<Long, Drug> drugMap = drugMapper.selectBatchIds(drugIds).stream()
                .filter(d -> d.getDeletedAt() == null)
                .collect(Collectors.toMap(Drug::getId, d -> d, (a, b) -> a));

        List<TemplateItemDTO> itemDTOs = request.getItems().stream()
                .map(item -> {
                    Drug drug = drugMap.get(item.getDrugId());
                    if (drug == null || !"ENABLED".equals(drug.getStatus())) {
                        throw new BusinessException("3003", "药品不存在或已停用");
                    }
                    TemplateItemDTO dto = new TemplateItemDTO();
                    dto.setDrugId(item.getDrugId());
                    dto.setDrugName(drug.getName());
                    dto.setDosage(item.getDosage());
                    dto.setFrequency(item.getFrequency());
                    dto.setUsageMethod(item.getUsageMethod());
                    dto.setDays(item.getDays());
                    dto.setQuantity(item.getQuantity());
                    return dto;
                })
                .collect(Collectors.toList());

        PrescriptionTemplate entity = new PrescriptionTemplate();
        entity.setHospitalId(scope.hospitalId());
        entity.setDeptId(request.getDeptId());
        entity.setName(request.getName().trim());
        entity.setDoctorId(doctorId);
        entity.setItems(itemDTOs);
        entity.setStatus("ENABLED");
        templateMapper.insert(entity);

        log.info("创建处方模板 templateId={}, name={}, doctorId={}", entity.getId(), entity.getName(), doctorId);
        return toTemplateListVO(entity);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DataScope scope = currentUserService.getCurrentDataScope();
        PrescriptionTemplate template = templateMapper.selectById(id);
        if (template == null || template.getDeletedAt() != null) {
            throw new BusinessException("A0402", "模板不存在");
        }
        if (!template.getHospitalId().equals(scope.hospitalId())) {
            throw new BusinessException("3020", "无权删除该模板");
        }
        // 软删除
        template.setDeletedAt(OffsetDateTime.now());
        template.setStatus("DISABLED");
        templateMapper.updateById(template);
        log.info("删除处方模板 templateId={}", id);
    }

    private TemplateListVO toTemplateListVO(PrescriptionTemplate t) {
        Doctor doctor = doctorMapper.selectById(t.getDoctorId());
        Department dept = t.getDeptId() != null ? departmentMapper.selectById(t.getDeptId()) : null;
        return TemplateListVO.builder()
                .id(t.getId())
                .name(t.getName())
                .deptId(t.getDeptId())
                .deptName(dept != null ? dept.getName() : null)
                .doctorName(doctor != null ? doctor.getName() : null)
                .itemCount(t.getItems() != null ? t.getItems().size() : 0)
                .items(t.getItems())
                .createdAt(t.getCreatedAt())
                .build();
    }
}