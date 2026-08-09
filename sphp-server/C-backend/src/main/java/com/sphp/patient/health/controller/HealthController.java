package com.sphp.patient.health.controller;

import com.sphp.patient.health.service.HealthService;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.health.dto.AllergyCreateRequest;
import com.sphp.patient.health.dto.AllergyUpdateRequest;
import com.sphp.patient.health.dto.MedicalHistoryCreateRequest;
import com.sphp.patient.health.dto.MedicalHistoryUpdateRequest;
import com.sphp.patient.health.vo.AllergyCreateVO;
import com.sphp.patient.health.vo.AllergyUpdateVO;
import com.sphp.patient.health.vo.MedicalHistoryCreateVO;
import com.sphp.patient.health.vo.MedicalHistoryUpdateVO;
import com.sphp.patient.health.vo.HealthRecordVO;
import com.sphp.patient.health.vo.HealthRecordDeleteVO;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import com.sphp.shared.common.constant.HeaderConstant;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.sphp.shared.common.constant.HeaderConstant.IDEMPOTENCY_KEY;

/**
 * C端健康档案接口。
 */
@RestController
@Validated
@RequestMapping("/c/v1/health-record")
@Tag(name = "C端健康档案", description = "查询健康档案并维护过敏史、既往史")
@RequiredArgsConstructor
public class HealthController {

    private final HealthService healthService;
    // 幂等性服务
    private final CIdempotencyService idempotencyService;

    /**
     * 查询当前账号可访问就诊人的健康档案。
     *
     * @param patientId 可选就诊人 ID，未传时查询本人
     * @return 健康档案资料、过敏史、既往史和摘要
     */
    @GetMapping
    @Operation(summary = "查询健康档案")
    public Result<HealthRecordVO> getHealthRecord(
            @RequestParam(required = false) @Positive(message = "就诊人ID必须为正整数") Long patientId) {
        return Result.success("查询成功", healthService.getHealthRecord(patientId));
    }

    /**
     * 为当前账号可访问就诊人新增过敏史。
     *
     * @param idempotencyKey 客户端幂等键
     * @param request 新增过敏史请求
     * @return 新建过敏史信息
     */
    @PostMapping("/allergies")
    @Operation(summary = "新增过敏史")
    public Result<AllergyCreateVO> createAllergy(
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey,
            @Valid @RequestBody AllergyCreateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<AllergyCreateVO> payload = idempotencyService.execute(
                userId,
                "/c/v1/health-record/allergies",
                idempotencyKey,
                request,
                AllergyCreateVO.class,
                () -> new IdempotencyPayload<>("过敏史已保存", healthService.createAllergy(request))
        );
        return Result.success(payload.message(), payload.data());
    }

    /**
     * 更新当前账号可访问就诊人的过敏史。
     *
     * @param allergyId 过敏史 ID
     * @param idempotencyKey 客户端幂等键
     * @param request 更新过敏史请求
     * @return 更新后的过敏史信息
     */
    @PutMapping("/allergies/{allergyId}")
    @Operation(summary = "更新过敏史")
    public Result<AllergyUpdateVO> updateAllergy(
            @PathVariable @Positive(message = "过敏史ID必须为正整数") Long allergyId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey,
            @Valid @RequestBody AllergyUpdateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<AllergyUpdateVO> payload = idempotencyService.execute(
                userId,
                "/c/v1/health-record/allergies/" + allergyId,
                idempotencyKey,
                request,
                AllergyUpdateVO.class,
                () -> new IdempotencyPayload<>("过敏史已更新", healthService.updateAllergy(allergyId, request))
        );
        return Result.success(payload.message(), payload.data());
    }

    /**
     * 删除当前账号可访问就诊人的过敏史。
     *
     * @param allergyId 过敏史 ID
     * @param idempotencyKey 客户端幂等键
     * @return 软删除结果
     */
    @DeleteMapping("/allergies/{allergyId}")
    @Operation(summary = "删除过敏史")
    public Result<HealthRecordDeleteVO> deleteAllergy(
            @PathVariable @Positive(message = "过敏史ID必须为正整数") Long allergyId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<HealthRecordDeleteVO> payload = idempotencyService.execute(
                userId,
                "/c/v1/health-record/allergies/" + allergyId,
                idempotencyKey,
                allergyId,
                HealthRecordDeleteVO.class,
                () -> new IdempotencyPayload<>("过敏史已删除", healthService.deleteAllergy(allergyId)));
        return Result.success(payload.message(), payload.data());
    }

    /**
     * 为当前账号可访问就诊人新增既往史。
     *
     * @param idempotencyKey 客户端幂等键
     * @param request 新增既往史请求
     * @return 新建既往史信息
     */
    @PostMapping("/histories")
    @Operation(summary = "新增既往史")
    public Result<MedicalHistoryCreateVO> createMedicalHistory(
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey,
            @Valid @RequestBody MedicalHistoryCreateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<MedicalHistoryCreateVO> payload = idempotencyService.execute(
                userId,
                "/c/v1/health-record/histories",
                idempotencyKey,
                request,
                MedicalHistoryCreateVO.class,
                () -> new IdempotencyPayload<>("既往史已保存", healthService.createMedicalHistory(request))
        );
        return Result.success(payload.message(), payload.data());
    }

    /**
     * 更新当前账号可访问就诊人的既往史。
     *
     * @param historyId 既往史 ID
     * @param idempotencyKey 客户端幂等键
     * @param request 更新既往史请求
     * @return 更新后的既往史信息
     */
    @PutMapping("/histories/{historyId}")
    @Operation(summary = "更新既往史")
    public Result<MedicalHistoryUpdateVO> updateMedicalHistory(
            @PathVariable @Positive(message = "既往史ID必须为正整数") Long historyId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey,
            @Valid @RequestBody MedicalHistoryUpdateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<MedicalHistoryUpdateVO> payload = idempotencyService.execute(
                userId,
                "/c/v1/health-record/histories/" + historyId,
                idempotencyKey,
                request,
                MedicalHistoryUpdateVO.class,
                () -> new IdempotencyPayload<>("既往史已更新", healthService.updateMedicalHistory(historyId, request))
        );
        return Result.success(payload.message(), payload.data());
    }
}
