package com.sphp.admin.prescription.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.auth.entity.Doctor;
import com.sphp.admin.auth.mapper.DoctorMapper;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.DataScope;
import com.sphp.admin.common.enums.BUserStatusEnum;
import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.hospital.entity.Department;
import com.sphp.admin.hospital.mapper.DepartmentMapper;
import com.sphp.admin.pharmacy.dto.DrugListVO;
import com.sphp.admin.pharmacy.mapper.PharmacyDrugStockMapper;
import com.sphp.admin.prescription.dto.PrescriptionSubmitRequest;
import com.sphp.admin.prescription.dto.PrescriptionSubmitVO;
import com.sphp.admin.prescription.dto.SaveTemplateRequest;
import com.sphp.admin.prescription.dto.TemplateItemDTO;
import com.sphp.admin.prescription.dto.TemplateListVO;
import com.sphp.admin.prescription.entity.Drug;
import com.sphp.admin.prescription.entity.PrescriptionTemplate;
import com.sphp.admin.prescription.mapper.DrugMapper;
import com.sphp.admin.prescription.mapper.PrescriptionTemplateMapper;
import com.sphp.admin.prescription.service.PrescriptionService;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 处方模板服务实现（管理员视角）。
 *
 * <p>按当前登录用户所属医院（{@code hospital_id}）做数据隔离过滤；
 * 仅创建/更新/删除需医生身份，查询不限角色。
 * 模板状态字段与 {@link BUserStatusEnum} 复用（仅 ENABLED / DISABLED 两态）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionTemplateServiceImpl implements PrescriptionTemplateService {

    /** 通用业务冲突（A0401：请求参数或业务前置条件不满足） */
    private static final String ERR_BUSINESS_CONFLICT = "A0401";
    /** 资源不存在 / 越权访问（A0402：通用资源未找到） */
    private static final String ERR_RESOURCE_NOT_FOUND = "A0402";
    /** 无权限（A0443：当前角色不允许操作） */
    private static final String ERR_NO_PERMISSION = "A0443";
    /** 越权访问（3020：跨医院/跨科室资源访问） */
    private static final String ERR_FORBIDDEN = "3020";
    /** 药品不可用（3003：药品不存在或已停用） */
    private static final String ERR_DRUG_INVALID = "3003";

    /** 数量单位默认值（前端缺省时使用） */
    private static final String DEFAULT_QUANTITY_UNIT = "盒";

    private final PrescriptionTemplateMapper templateMapper;
    private final DrugMapper drugMapper;
    private final PharmacyDrugStockMapper stockMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final CurrentUserService currentUserService;
    private final PrescriptionService prescriptionService;

    /** 数值提取：从用量/频次字符串中解析首个数字（如 "2粒"→2、"每日3次"→3） */
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\d+(\\.\\d+)?");

    /** 规格中单盒/单瓶数量结构（×、*、x），如 "0.25g*24粒" 提取 24 */
    private static final Pattern SPEC_COUNT_PATTERN = Pattern.compile("[×*xX]\\s*(\\d+(\\.\\d+)?)");

    @Override
    public PageResult<TemplateListVO> page(String name, Long deptId, int page, int size) {
        DataScope scope = currentUserService.getCurrentDataScope();

        LambdaQueryWrapper<PrescriptionTemplate> wrapper = Wrappers.<PrescriptionTemplate>lambdaQuery()
                .eq(PrescriptionTemplate::getHospitalId, scope.hospitalId())
                .eq(PrescriptionTemplate::getStatus, BUserStatusEnum.ENABLED.getCode())
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
            throw new BusinessException(ERR_NO_PERMISSION, "当前用户无医生身份，无法创建模板");
        }

        if (!StringUtils.hasText(request.getName())) {
            throw new BusinessException(ERR_BUSINESS_CONFLICT, "模板名称不能为空");
        }
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(ERR_BUSINESS_CONFLICT, "模板药品明细不能为空");
        }

        // 校验关联科室属于当前医院（空表示全院通用）
        if (request.getDeptId() != null) {
            Department dept = departmentMapper.selectById(request.getDeptId());
            if (dept == null || dept.getDeletedAt() != null
                    || !dept.getHospitalId().equals(scope.hospitalId())) {
                throw new BusinessException(ERR_BUSINESS_CONFLICT, "科室不存在或不属于当前医院");
            }
        }

        // 防同名模板重复（同医院、未删除）
        Long duplicate = templateMapper.selectCount(
                Wrappers.<PrescriptionTemplate>lambdaQuery()
                        .eq(PrescriptionTemplate::getHospitalId, scope.hospitalId())
                        .eq(PrescriptionTemplate::getName, request.getName().trim())
                        .isNull(PrescriptionTemplate::getDeletedAt));
        if (duplicate != null && duplicate > 0) {
            throw new BusinessException(ERR_BUSINESS_CONFLICT, "同医院已存在同名模板");
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
                    if (drug == null || !BUserStatusEnum.isEnabled(drug.getStatus())) {
                        throw new BusinessException(ERR_DRUG_INVALID, "药品不存在或已停用");
                    }
                    // 数量校验：需求(天数×频次×用量) 不得超过 发放(数量×规格单盒数)
                    assertQuantitySufficient(item, drug);
                    TemplateItemDTO dto = new TemplateItemDTO();
                    dto.setDrugId(item.getDrugId());
                    dto.setDrugName(drug.getName());
                    dto.setDosage(item.getDosage());
                    dto.setFrequency(item.getFrequency());
                    dto.setUsageMethod(item.getUsageMethod());
                    dto.setDays(item.getDays());
                    dto.setQuantity(item.getQuantity());
                    // 数量单位默认盒；不落表，仅存模板 items JSONB
                    dto.setQuantityUnit(StringUtils.hasText(item.getQuantityUnit()) ? item.getQuantityUnit() : DEFAULT_QUANTITY_UNIT);
                    return dto;
                })
                .collect(Collectors.toList());

        PrescriptionTemplate entity = new PrescriptionTemplate();
        entity.setHospitalId(scope.hospitalId());
        entity.setDeptId(request.getDeptId());
        entity.setName(request.getName().trim());
        entity.setDoctorId(doctorId);
        entity.setUpdatedBy(doctorId);
        entity.setItems(itemDTOs);
        entity.setStatus(BUserStatusEnum.ENABLED.getCode());
        templateMapper.insert(entity);

        log.info("创建处方模板 templateId={}, name={}, doctorId={}", entity.getId(), entity.getName(), doctorId);
        return toTemplateListVO(entity);
    }

    @Override
    public DrugListVO getDrug(Long id) {
        Long hospitalId = currentUserService.getCurrentDataScope().hospitalId();
        Drug drug = drugMapper.selectById(id);
        if (drug == null || drug.getDeletedAt() != null || !drug.getHospitalId().equals(hospitalId)) {
            throw new BusinessException(ERR_RESOURCE_NOT_FOUND, "药品不存在");
        }
        // 附带当前医院各药房可用库存之和，供新建模板校验数量是否超出库存
        Integer stock = stockMapper.sumAvailableByDrugId(id, hospitalId);
        return DrugListVO.builder()
                .id(drug.getId())
                .name(drug.getName())
                .specification(drug.getSpecification())
                .unit(drug.getUnit())
                .indication(drug.getIndication())
                .manufacturer(drug.getManufacturer())
                .approvalNumber(drug.getApprovalNumber())
                .status(drug.getStatus())
                .availableStock(stock == null ? 0L : stock.longValue())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public TemplateListVO update(Long id, SaveTemplateRequest request) {
        DataScope scope = currentUserService.getCurrentDataScope();
        Long doctorId = scope.doctorId();
        if (doctorId == null) {
            throw new BusinessException(ERR_NO_PERMISSION, "当前用户无医生身份，无法更新模板");
        }

        PrescriptionTemplate template = templateMapper.selectById(id);
        if (template == null || template.getDeletedAt() != null) {
            throw new BusinessException(ERR_RESOURCE_NOT_FOUND, "模板不存在");
        }
        if (!template.getHospitalId().equals(scope.hospitalId())) {
            throw new BusinessException(ERR_FORBIDDEN, "无权修改该模板");
        }

        // 校验药品明细非空
        if (request.getItems() == null || request.getItems().isEmpty()) {
            throw new BusinessException(ERR_BUSINESS_CONFLICT, "药品明细不能为空");
        }

        // 校验关联科室属于当前医院（空表示全院通用）
        if (request.getDeptId() != null) {
            Department dept = departmentMapper.selectById(request.getDeptId());
            if (dept == null || dept.getDeletedAt() != null
                    || !dept.getHospitalId().equals(scope.hospitalId())) {
                throw new BusinessException(ERR_BUSINESS_CONFLICT, "科室不存在或不属于当前医院");
            }
        }

        // 批量查询药品，校验存在且启用，并回填药品名称
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
                    if (drug == null || !BUserStatusEnum.isEnabled(drug.getStatus())) {
                        throw new BusinessException(ERR_DRUG_INVALID, "药品不存在或已停用");
                    }
                    assertQuantitySufficient(item, drug);
                    TemplateItemDTO dto = new TemplateItemDTO();
                    dto.setDrugId(item.getDrugId());
                    dto.setDrugName(drug.getName());
                    dto.setDosage(item.getDosage());
                    dto.setFrequency(item.getFrequency());
                    dto.setUsageMethod(item.getUsageMethod());
                    dto.setDays(item.getDays());
                    dto.setQuantity(item.getQuantity());
                    dto.setQuantityUnit(StringUtils.hasText(item.getQuantityUnit()) ? item.getQuantityUnit() : DEFAULT_QUANTITY_UNIT);
                    return dto;
                })
                .collect(Collectors.toList());

        OffsetDateTime now = OffsetDateTime.now();
        template.setDeptId(request.getDeptId());
        template.setItems(itemDTOs);
        template.setUpdatedBy(doctorId);
        template.setUpdatedAt(now);
        templateMapper.updateById(template);

        log.info("更新处方模板 templateId={}, updatedBy={}", id, doctorId);
        return toTemplateListVO(template);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        DataScope scope = currentUserService.getCurrentDataScope();
        PrescriptionTemplate template = templateMapper.selectById(id);
        if (template == null || template.getDeletedAt() != null) {
            throw new BusinessException(ERR_RESOURCE_NOT_FOUND, "模板不存在");
        }
        if (!template.getHospitalId().equals(scope.hospitalId())) {
            throw new BusinessException(ERR_FORBIDDEN, "无权删除该模板");
        }
        // 软删除
        template.setDeletedAt(OffsetDateTime.now());
        template.setStatus(BUserStatusEnum.DISABLED.getCode());
        templateMapper.updateById(template);
        log.info("删除处方模板 templateId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public PrescriptionSubmitVO apply(Long templateId, Long consultId) {
        DataScope scope = currentUserService.getCurrentDataScope();

        // 1. 校验模板：当前医院、启用、未删除、明细非空
        PrescriptionTemplate template = templateMapper.selectById(templateId);
        if (template == null || template.getDeletedAt() != null) {
            throw new BusinessException(ERR_RESOURCE_NOT_FOUND, "模板不存在");
        }
        if (!BUserStatusEnum.isEnabled(template.getStatus())) {
            throw new BusinessException(ERR_RESOURCE_NOT_FOUND, "模板已停用");
        }
        if (!template.getHospitalId().equals(scope.hospitalId())) {
            throw new BusinessException(ERR_FORBIDDEN, "无权使用该模板");
        }
        if (template.getItems() == null || template.getItems().isEmpty()) {
            throw new BusinessException(ERR_BUSINESS_CONFLICT, "模板药品明细为空，无法开方");
        }

        // 2. 模板明细 → 处方明细
        List<PrescriptionSubmitRequest.ItemDTO> items = template.getItems().stream()
                .map(t -> {
                    PrescriptionSubmitRequest.ItemDTO dto = new PrescriptionSubmitRequest.ItemDTO();
                    dto.setDrugId(t.getDrugId());
                    dto.setDosage(t.getDosage());
                    dto.setFrequency(t.getFrequency());
                    dto.setUsageMethod(t.getUsageMethod());
                    dto.setDays(t.getDays());
                    dto.setQuantity(t.getQuantity());
                    return dto;
                })
                .collect(Collectors.toList());

        // 3. 复用共享开方方法（含风险拦截）
        log.info("应用处方模板 templateId={}, consultId={}", templateId, consultId);
        return prescriptionService.createFromItems(consultId, items);
    }

    /**
     * 校验数量是否充足：需求(天数×频次×用量) 必须 ≤ 发放(数量×规格单盒数)。
     *
     * <p>规格、用量或频次任一项无法解析为数值时跳过校验（兼容存量自由文本数据）。
     *
     * @param item 模板药品项（含天数/数量/用量/频次）
     * @param drug 药品（取 specification 解析单盒数量）
     * @throws BusinessException 数量不足时抛 ERR_BUSINESS_CONFLICT
     */
    private void assertQuantitySufficient(SaveTemplateRequest.ItemDTO item, Drug drug) {
        if (item.getDays() == null || item.getDays() <= 0
                || item.getQuantity() == null || item.getQuantity() <= 0) {
            return;
        }
        Long specCount = parseSpecCount(drug.getSpecification());
        Double dosageValue = parseDosageValue(item.getDosage());
        Integer frequencyValue = parseFrequencyValue(item.getFrequency());
        if (specCount == null || specCount <= 0 || dosageValue == null || frequencyValue == null) {
            return;
        }
        double needed = item.getDays() * frequencyValue * dosageValue;
        double dispensed = item.getQuantity() * specCount;
        if (needed > dispensed) {
            throw new BusinessException(ERR_BUSINESS_CONFLICT,
                    String.format("药品[%s]数量不足：天数×频次×用量=%.2f，需 ≤ 数量×规格=%.2f（%d×%d）",
                            drug.getName(), needed, dispensed, item.getQuantity(), specCount));
        }
    }

    /** 从规格解析单盒/单瓶数量，如 "0.25g*24粒" → 24；无 ×N 结构返回 null */
    private Long parseSpecCount(String specification) {
        if (!StringUtils.hasText(specification)) {
            return null;
        }
        Matcher m = SPEC_COUNT_PATTERN.matcher(specification);
        return m.find() ? Long.valueOf(m.group(1)) : null;
    }

    /** 从用量字符串解析数值，如 "2粒" → 2.0、"0.5克" → 0.5；无法解析返回 null */
    private Double parseDosageValue(String dosage) {
        if (!StringUtils.hasText(dosage)) {
            return null;
        }
        Matcher m = NUMBER_PATTERN.matcher(dosage);
        return m.find() ? Double.valueOf(m.group()) : null;
    }

    /** 从频次解析每日次数，如 "每日3次" → 3；无法解析（如 BID）返回 null */
    private Integer parseFrequencyValue(String frequency) {
        if (!StringUtils.hasText(frequency)) {
            return null;
        }
        Matcher m = NUMBER_PATTERN.matcher(frequency);
        return m.find() ? Integer.valueOf(m.group()) : null;
    }

    private TemplateListVO toTemplateListVO(PrescriptionTemplate t) {
        // 取更新人姓名，更新人为空时回退到创建人
        Long targetDoctorId = t.getUpdatedBy() != null ? t.getUpdatedBy() : t.getDoctorId();
        Doctor doctor = targetDoctorId != null ? doctorMapper.selectById(targetDoctorId) : null;
        Department dept = t.getDeptId() != null ? departmentMapper.selectById(t.getDeptId()) : null;
        return TemplateListVO.builder()
                .id(t.getId())
                .name(t.getName())
                .deptId(t.getDeptId())
                .deptName(dept != null ? dept.getName() : null)
                .updatedByName(doctor != null ? doctor.getName() : null)
                .itemCount(t.getItems() != null ? t.getItems().size() : 0)
                .items(t.getItems())
                .createdAt(t.getCreatedAt())
                .updatedAt(t.getUpdatedAt())
                .build();
    }
}