package com.sphp.admin.prescription.controller;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.prescription.dto.AuditRequest;
import com.sphp.admin.prescription.dto.PrescriptionDetailVO;
import com.sphp.admin.prescription.dto.PrescriptionListVO;
import com.sphp.admin.prescription.dto.PrescriptionSubmitRequest;
import com.sphp.admin.prescription.dto.PrescriptionSubmitVO;
import com.sphp.admin.prescription.service.PrescriptionService;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处方管理控制器（系分 §5.6）。
 *
 * <p>外部完整 URL 前缀为 {@code /api/b/...}。
 * 包含处方提交、列表、详情。
 */
@RestController
@RequestMapping("/b")
@Tag(name = "5-处方管理", description = "处方提交/列表/详情")
@RequiredArgsConstructor
public class PrescriptionController {

    private static final int MAX_PAGE_SIZE = 100;

    private static int clampSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private final PrescriptionService prescriptionService;

    // ==================== 5.6.1 提交处方 ====================

    @PostMapping("/prescriptions")
    @Operation(summary = "提交处方", description = "含风险拦截，根据命中级别决定 APPROVED 或 SUBMITTED")
    public Result<PrescriptionSubmitVO> submit(@Valid @RequestBody PrescriptionSubmitRequest request) {
        return Result.success("提交成功", prescriptionService.submit(request));
    }

    // ==================== 5.6.2 处方列表 ====================

    @GetMapping("/prescriptions")
    @Operation(summary = "查询处方列表", description = "分页查询处方（按当前用户数据权限过滤）")
    public Result<PageResult<PrescriptionListVO>> page(
            @Parameter(description = "问诊记录 ID") @RequestParam(required = false) Long consultId,
            @Parameter(description = "患者 ID") @RequestParam(required = false) Long patientId,
            @Parameter(description = "状态过滤（多值逗号分隔）") @RequestParam(required = false) String status,
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") int size) {
        return Result.success("查询成功",
                prescriptionService.page(consultId, patientId, status, page, clampSize(size)));
    }

    // ==================== 5.6.3 处方详情 ====================

    @GetMapping("/prescriptions/{id}")
    @Operation(summary = "查询处方详情", description = "返回处方完整信息（含药品明细）")
    public Result<PrescriptionDetailVO> getDetail(@PathVariable Long id) {
        return Result.success("查询成功", prescriptionService.getDetail(id));
    }

    // ==================== 5.6.4 待审核处方列表 ====================

    @GetMapping("/prescriptions/pending-audit")
    @Operation(summary = "待审核处方列表", description = "分页查询待审核处方（仅 ADMIN / DEPT_HEAD 可访问）")
    public Result<PageResult<PrescriptionListVO>> pendingAudit(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") int size) {
        return Result.success("查询成功",
                prescriptionService.pendingAuditList(page, clampSize(size)));
    }

    // ==================== 5.6.5 审核处方 ====================

    @PutMapping("/prescriptions/{id}/audit")
    @Operation(summary = "审核处方", description = "通过或驳回处方（仅 ADMIN / DEPT_HEAD 可操作）")
    public Result<String> audit(@PathVariable Long id, @Valid @RequestBody AuditRequest request) {
        prescriptionService.audit(id, request);
        return Result.success("审核成功");
    }
}