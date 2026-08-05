package com.sphp.patient.consultation.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/**
 * 创建或保存预问诊请求参数。
 */
@Getter
@Setter
public class PreConsultationSaveRequest {

    /** 接诊医生 ID */
    @NotNull(message = "doctorId 不能为空")
    @Positive(message = "doctorId 必须为正数")
    private Long doctorId;

    /** Agent 汇总的患者主诉 */
    @NotBlank(message = "主诉不能为空")
    @Size(max = 1000, message = "主诉不能超过1000个字符")
    private String chiefComplaint;

    /** 可选现病史补充 */
    @Size(max = 10000, message = "现病史不能超过10000个字符")
    private String historyOfPresentIllness;

    /** 可选附件列表 */
    @Valid
    private List<ConsultationAttachmentRequest> attachments;

}
