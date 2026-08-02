package com.sphp.patient.registration.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建挂号锁定订单请求参数。
 */
@Getter
@Setter
public class RegisteringAppointmentCreateRequest {

    /** 可选就诊人 ID，未传时使用本人 */
    @Positive(message = "patientId 必须为正数")
    private Long patientId;
    /** 当前选择的医院 ID */
    @NotNull(message = "hospitalId 不能为空")
    @Positive(message = "hospitalId 必须为正数")
    private Long hospitalId;
    /** 待锁定的号源时段 ID */
    @NotNull(message = "slotId 不能为空")
    @Positive(message = "slotId 必须为正数")
    private Long slotId;
}
