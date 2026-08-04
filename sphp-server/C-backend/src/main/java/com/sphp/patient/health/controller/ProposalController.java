package com.sphp.patient.health.controller;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.health.dto.ProposalFollowUpConfirmRequest;
import com.sphp.patient.health.dto.ProposalMedicationUpdateRequest;
import com.sphp.patient.health.dto.ProposalReportCreateRequest;
import com.sphp.patient.health.service.ProposalService;
import com.sphp.patient.health.vo.ProposalFollowUpVO;
import com.sphp.patient.health.vo.ProposalMedicationPlanVO;
import com.sphp.patient.health.vo.ProposalReportCreateVO;
import com.sphp.patient.health.vo.ProposalReportDetailVO;
import com.sphp.patient.health.vo.ProposalReportInterpretationVO;
import com.sphp.patient.health.vo.ProposalReportPageVO;
import com.sphp.patient.health.vo.ProposalMedicalRecordPageVO;
import com.sphp.patient.health.vo.ProposalMedicalRecordDetailVO;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import com.sphp.shared.common.constant.HeaderConstant;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C 端健康报告、用药计划与随访计划接口。
 */
@RestController
@Validated
@RequestMapping("/c/v1")
@RequiredArgsConstructor
public class ProposalController {
    // 健康报告、用药和随访服务
    private final ProposalService proposalService;
    // 幂等层
    private final CIdempotencyService idempotencyService;

    /**
     * 录入检查报告。
     *
     * <p>该历史兼容接口不再作为 C 端报告主流程；新报告由 B 端医生完成问诊后保存病历产生。</p>
     *
     * @param idempotencyKey 客户端幂等键
     * @param request 报告录入请求
     * @return 已录入报告的 ID 和状态
     */
    @Deprecated(since = "2026-08", forRemoval = false)
    @Operation(summary = "录入检查报告（已废弃）", deprecated = true)
    @PostMapping("/reports")
    public Result<ProposalReportCreateVO> proposalCreateReport(
            @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank String idempotencyKey,
            @Valid @RequestBody ProposalReportCreateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        // 写入操作统一经 Redis 幂等层，成功重放首个结果且不缓存失败响应。
        IdempotencyPayload<ProposalReportCreateVO> payload = idempotencyService.execute(
                userId,
                "/c/v1/reports",
                idempotencyKey,
                request,
                ProposalReportCreateVO.class,
                () -> new IdempotencyPayload<>("报告已录入", proposalService.proposalCreateReport(request)));
        return Result.success(payload.message(), payload.data());
    }

    /**
     * 分页查询当前账号可访问就诊人的医生病历。
     *
     * @param patientId 可选就诊人 ID
     * @param pageNo 可选页码
     * @param pageSize 可选每页数量
     * @return 病历分页数据
     */
    @GetMapping("/medical-records")
    @Operation(summary = "查询医生病历")
    public Result<ProposalMedicalRecordPageVO> proposalListMedicalRecords(
            @RequestParam(required = false) @Positive Long patientId,
            @RequestParam(required = false) @Positive Integer pageNo,
            @RequestParam(required = false) @Positive Integer pageSize) {
        return Result.success("查询成功", proposalService.proposalListMedicalRecords(patientId, pageNo, pageSize));
    }

    /**
     * 查询单份当前账号可访问的医生病历。
     *
     * @param consultId 问诊记录 ID，即病历 ID
     * @return 病历详情
     */
    @GetMapping("/medical-records/{consultId}")
    @Operation(summary = "查询医生病历详情")
    public Result<ProposalMedicalRecordDetailVO> proposalGetMedicalRecord(
            @PathVariable @Positive Long consultId) {
        return Result.success("查询成功", proposalService.proposalGetMedicalRecord(consultId));
    }

    /**
     * 分页查询检查报告。
     *
     * @param patientId 可选就诊人 ID
     * @param pageNo 可选页码
     * @param pageSize 可选每页数量
     * @return 检查报告分页数据
     */
    @GetMapping("/reports")
    public Result<ProposalReportPageVO> proposalListReports(
            @RequestParam(required = false) @Positive Long patientId,
            @RequestParam(required = false) @Positive Integer pageNo,
            @RequestParam(required = false) @Positive Integer pageSize) {
        return Result.success("查询成功", proposalService.proposalListReports(patientId, pageNo, pageSize));
    }

    /**
     * 查看医生病历报告详情。
     *
     * @param reportId 报告 ID
     * @return 医生病历报告详情
     */
    @GetMapping("/reports/{reportId}")
    public Result<ProposalReportDetailVO> proposalGetReport(@PathVariable @Positive Long reportId) {
        return Result.success("查询成功", proposalService.proposalGetReport(reportId));
    }

    /**
     * 查看已生成的医生病历报告解读。
     *
     * @param reportId 报告 ID
     * @return 报告解读内容
     */
    @GetMapping("/reports/{reportId}/interpretation")
    public Result<ProposalReportInterpretationVO> proposalGetReportInterpretation(
            @PathVariable @Positive Long reportId) {
        return Result.success("查询成功", proposalService.proposalGetReportInterpretation(reportId));
    }

    /**
     * 查询用药计划。
     *
     * @param patientId 可选就诊人 ID
     * @param status 可选计划状态
     * @return 用药计划列表
     */
    @GetMapping("/medication-plans")
    public Result<List<ProposalMedicationPlanVO>> proposalListMedicationPlans(
            @RequestParam(required = false) @Positive Long patientId,
            @RequestParam(required = false) String status) {
        return Result.success("查询成功", proposalService.proposalListMedicationPlans(patientId, status));
    }

    /**
     * 更新用药计划状态。
     *
     * @param planId 用药计划 ID
     * @param idempotencyKey 客户端幂等键
     * @param request 状态变更请求
     * @return 更新后的计划摘要
     */
    @PatchMapping("/medication-plans/{planId}")
    public Result<ProposalMedicationPlanVO> proposalUpdateMedicationPlan(
            @PathVariable @Positive Long planId,
            @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank String idempotencyKey,
            @Valid @RequestBody ProposalMedicationUpdateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        // 资源 ID 纳入路径域，避免不同用药计划使用同一幂等键发生误重放。
        IdempotencyPayload<ProposalMedicationPlanVO> payload = idempotencyService.execute(
                userId,
                "/c/v1/medication-plans/" + planId,
                idempotencyKey,
                request,
                ProposalMedicationPlanVO.class,
                () -> new IdempotencyPayload<>("用药计划已更新",
                        proposalService.proposalUpdateMedicationPlan(planId, request)));
        return Result.success(payload.message(), payload.data());
    }

    /**
     * 查询随访计划。
     *
     * @param patientId 可选就诊人 ID
     * @param status 可选随访状态
     * @return 随访计划列表
     */
    @GetMapping("/follow-ups")
    public Result<List<ProposalFollowUpVO>> proposalListFollowUps(
            @RequestParam(required = false) @Positive Long patientId,
            @RequestParam(required = false) String status) {
        return Result.success("查询成功", proposalService.proposalListFollowUps(patientId, status));
    }

    /**
     * 确认随访计划。
     *
     * @param followUpId 随访计划 ID
     * @param idempotencyKey 客户端幂等键
     * @param request 确认请求
     * @return 已确认的随访计划
     */
    @PostMapping("/follow-ups/{followUpId}/confirm")
    public Result<ProposalFollowUpVO> proposalConfirmFollowUp(
            @PathVariable @Positive Long followUpId,
            @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank String idempotencyKey,
            @Valid @RequestBody ProposalFollowUpConfirmRequest request) {
        Long userId = CUserContext.getRequired().userId();
        // 确认操作按随访资源隔离幂等结果，保证重复点击不会重复变更状态。
        IdempotencyPayload<ProposalFollowUpVO> payload = idempotencyService.execute(
                userId,
                "/c/v1/follow-ups/" + followUpId + "/confirm",
                idempotencyKey,
                request,
                ProposalFollowUpVO.class,
                () -> new IdempotencyPayload<>("随访计划已确认",
                        proposalService.proposalConfirmFollowUp(followUpId, request)));
        return Result.success(payload.message(), payload.data());
    }
}
