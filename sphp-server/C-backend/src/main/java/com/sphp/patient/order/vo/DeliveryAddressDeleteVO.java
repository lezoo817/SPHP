package com.sphp.patient.order.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 收货地址软删除结果。
 */
@Getter
@Builder
public class DeliveryAddressDeleteVO {

    /** 已删除地址 ID。 */ private final Long id;
    /** 删除时间。 */ private final OffsetDateTime deletedAt;
}
