package com.sphp.patient.order.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * 更新 C端收货地址请求参数。
 */
@Getter
@Setter
public class DeliveryAddressUpdateRequest {

    /** 收件人姓名。 */
    @NotBlank(message = "receiverName 不能为空")
    @Size(max = 64, message = "receiverName 不能超过64个字符")
    private String receiverName;
    /** 收件人大陆手机号。 */
    @NotBlank(message = "receiverPhone 不能为空")
    @Pattern(regexp = "^1[3-9]\\d{9}$", message = "receiverPhone 格式不正确")
    private String receiverPhone;
    /** 收货地址所属省市编码。 */
    @NotBlank(message = "province 不能为空")
    @Pattern(regexp = "HENAN|SHANGHAI|BEIJING|JIANGSU|ZHEJIANG|GUANGDONG", message = "province 不在支持范围内")
    private String province;
    /** 收货城市。 */
    @NotBlank(message = "city 不能为空")
    @Size(max = 64, message = "city 不能超过64个字符")
    private String city;
    /** 收货区县，可不传。 */
    @Size(max = 64, message = "district 不能超过64个字符")
    private String district;
    /** 收货详细地址。 */
    @NotBlank(message = "detailAddress 不能为空")
    @Size(max = 200, message = "detailAddress 不能超过200个字符")
    private String detailAddress;
}
