package com.sphp.patient.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 创建购药订单请求参数。
 */
@Getter
@Setter
public class DrugOrderCreateRequest {
    /** 可选就诊人 ID，未传时使用本人 */
    @Positive(message = "patientId 必须为正数") private Long patientId;
    /** 已批准处方 ID */
    @NotNull(message = "prescriptionId 不能为空") @Positive(message = "prescriptionId 必须为正数") private Long prescriptionId;
    /** 选定院内药房 ID */
    @NotNull(message = "pharmacyId 不能为空") @Positive(message = "pharmacyId 必须为正数") private Long pharmacyId;
    /** 快递收货地址 */
    @NotBlank(message = "deliveryAddress 不能为空") @Size(max = 500, message = "deliveryAddress 不能超过500个字符") private String deliveryAddress;
}
