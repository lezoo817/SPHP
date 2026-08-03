package com.sphp.patient.order.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * C端收货地址响应。
 */
@Getter
@Builder
public class DeliveryAddressVO {

    /** 地址 ID。 */
    private final Long id;

    /** 收件人姓名。 */
    private final String receiverName;

    /** 收件人手机号，仅向地址所属账号返回。 */
    private final String receiverPhone;

    /** 省市编码。 */
    private final String province;

    /** 省市中文名称。 */
    private final String provinceName;

    /** 城市名称。 */
    private final String city;

    /** 区县名称。 */
    private final String district;

    /** 详细地址。 */
    private final String detailAddress;

    /** 是否为默认地址。 */
    private final Boolean isDefault;

    /** 创建时间。 */
    private final OffsetDateTime createdAt;

    /** 更新时间。 */
    private final OffsetDateTime updatedAt;

}
