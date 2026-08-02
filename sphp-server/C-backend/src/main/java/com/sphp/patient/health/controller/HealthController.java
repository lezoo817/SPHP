package com.sphp.patient.health.controller;

import com.sphp.patient.health.service.HealthService;
import com.sphp.patient.health.vo.HealthRecordVO;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

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
}
