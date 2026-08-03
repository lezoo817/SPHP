package com.sphp.patient.consultation.controller;

import com.sphp.patient.consultation.service.PrescriptionService;
import com.sphp.patient.consultation.vo.ConsultationPrescriptionDetailVO;
import com.sphp.patient.consultation.vo.ConsultationPrescriptionPageVO;
import com.sphp.patient.consultation.vo.PrescriptionInterpretationVO;
import com.sphp.shared.result.Result;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C端处方列表、详情与处方解读接口。
 */
@RestController("cPrescriptionController")
@Validated
@RequestMapping("/c/v1")
@RequiredArgsConstructor
public class PrescriptionController {

    private final PrescriptionService prescriptionService;

    /**
     * 分页查询当前账号可访问患者的已批准处方。
     *
     * @param patientId 可选就诊人 ID
     * @param pageNo 可选页码
     * @param pageSize 可选每页数量
     * @return 已批准处方分页数据
     */
    @GetMapping("/prescriptions")
    public Result<ConsultationPrescriptionPageVO> prescriptionList(
            @RequestParam(required = false) @Positive(message = "patientId 必须为正数") Long patientId,
            @RequestParam(required = false) @Positive(message = "pageNo 必须为正数") Integer pageNo,
            @RequestParam(required = false) @Positive(message = "pageSize 必须为正数") Integer pageSize) {
        return Result.success("查询成功", prescriptionService.prescriptionList(patientId, pageNo, pageSize));
    }

    /**
     * 查询当前账号可访问的已批准处方详情。
     *
     * @param prescriptionId 处方 ID
     * @return 处方详情与药品明细
     */
    @GetMapping("/prescriptions/{prescriptionId}")
    public Result<ConsultationPrescriptionDetailVO> prescriptionGetDetail(
            @PathVariable @Positive(message = "prescriptionId 必须为正数") Long prescriptionId) {
        return Result.success("查询成功", prescriptionService.prescriptionGetDetail(prescriptionId));
    }

    /**
     * 查询当前账号可访问处方的已生成解读。
     *
     * @param prescriptionId 处方 ID
     * @return READY 状态处方解读
     */
    @GetMapping("/prescriptions/{prescriptionId}/interpretation")
    public Result<PrescriptionInterpretationVO> prescriptionGetInterpretation(
            @PathVariable @Positive(message = "prescriptionId 必须为正数") Long prescriptionId) {
        return Result.success("查询成功", prescriptionService.prescriptionGetInterpretation(prescriptionId));
    }
}
