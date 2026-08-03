package com.sphp.admin.patient.controller;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.patient.service.PatientService;
import com.sphp.admin.patient.vo.PatientDetailVO;
import com.sphp.admin.patient.vo.PatientListVO;
import com.sphp.admin.patient.vo.PatientMedicationVO;
import com.sphp.admin.patient.vo.PatientPrescriptionVO;
import com.sphp.admin.patient.vo.PatientVisitVO;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 患者管理接口（管理员，系分 §5.8）。
 *
 * <p>外部完整 URL 前缀为 {@code /api/b/admin/patients}。
 */
@RestController
@RequestMapping("/b/admin/patients")
@Tag(name = "7-患者管理", description = "患者列表/详情/就诊记录/历史处方/当前用药与随访（管理员）")
@RequiredArgsConstructor
public class PatientController {

    /** 每页大小上限，防止超大数据量查询 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 每页大小钳制到 [1, MAX_PAGE_SIZE] */
    private static int clampSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private final PatientService patientService;

    @GetMapping
    @Operation(summary = "患者列表", description = "分页查询本院就诊过的患者列表（按当前管理员所属医院过滤）")
    public Result<PageResult<PatientListVO>> page(
            @Parameter(description = "姓名模糊检索") @RequestParam(required = false) String name,
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") int size) {
        return Result.success("查询成功", patientService.page(name, page, clampSize(size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "患者详情", description = "查询患者基本信息、过敏史、既往史")
    public Result<PatientDetailVO> detail(@PathVariable Long id) {
        return Result.success("查询成功", patientService.detail(id));
    }

    @GetMapping("/{id}/visits")
    @Operation(summary = "患者就诊记录", description = "分页查询患者挂号/问诊历史列表")
    public Result<PageResult<PatientVisitVO>> visits(
            @PathVariable Long id,
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") int size) {
        return Result.success("查询成功", patientService.visits(id, page, clampSize(size)));
    }

    @GetMapping("/{id}/prescriptions")
    @Operation(summary = "患者历史处方", description = "分页查询患者历史处方列表")
    public Result<PageResult<PatientPrescriptionVO>> prescriptions(
            @PathVariable Long id,
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") int size) {
        return Result.success("查询成功", patientService.prescriptions(id, page, clampSize(size)));
    }

    @GetMapping("/{id}/medications")
    @Operation(summary = "患者当前用药与随访", description = "查询患者当前用药计划与随访计划")
    public Result<PatientMedicationVO> medications(@PathVariable Long id) {
        return Result.success("查询成功", patientService.medications(id));
    }
}