package com.sphp.patient.registration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/** 挂号候补登记请求参数。 */
@Getter @Setter
public class RegisteringWaitlistCreateRequest {
    /** 可选就诊人 ID，未传时使用本人 */
    @Positive(message = "patientId 必须为正数") private Long patientId;
    /** 已满号源时段 ID */
    @NotNull(message = "slotId 不能为空") @Positive(message = "slotId 必须为正数") private Long slotId;
}
