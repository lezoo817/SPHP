package com.sphp.patient.triage.controller;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.common.constant.TriageConstant;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import com.sphp.patient.triage.dto.TriageAssessmentCreateRequest;
import com.sphp.patient.triage.service.TriageService;
import com.sphp.patient.triage.vo.TriageAssessmentVO;
import com.sphp.shared.common.constant.HeaderConstant;
import com.sphp.shared.result.Result;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.sphp.patient.common.constant.TriageConstant.ASSESSMENT_PATH;
import static com.sphp.shared.common.constant.HeaderConstant.IDEMPOTENCY_KEY;

/**
 * C端症状导诊接口。
 */
@RestController
@Validated
@RequestMapping("/c/v1")
@RequiredArgsConstructor
public class TriageController {
    // 症状导诊服务
    private final TriageService triageService;
    // 幂等性服务
    private final CIdempotencyService idempotencyService;

    /**
     * 提交症状并获取非诊断性的导诊建议。
     *
     * @param idempotencyKey 客户端幂等键
     * @param request 症状导诊请求
     * @return 导诊评估与推荐科室
     */
    @PostMapping("/triage/assessments")
    public Result<TriageAssessmentVO> triageCreateAssessment(
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey,
            @Valid @RequestBody TriageAssessmentCreateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        // 导诊评估会写入医疗相关快照，使用幂等键防止重复提交产生多条评估记录。
        IdempotencyPayload<TriageAssessmentVO> payload = idempotencyService.execute(
                userId,
                ASSESSMENT_PATH, // 症状导诊路径
                idempotencyKey,
                request,
                TriageAssessmentVO.class,
                () -> new IdempotencyPayload<>("导诊评估完成", triageService.triageCreateAssessment(request)));
        return Result.success(payload.message(), payload.data());
    }
}
