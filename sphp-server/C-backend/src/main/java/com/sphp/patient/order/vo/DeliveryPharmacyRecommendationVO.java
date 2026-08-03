package com.sphp.patient.order.vo;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

/**
 * 基于收货地址的院内药房推荐响应。
 */
@Getter
@Builder
public class DeliveryPharmacyRecommendationVO {

    /** 药房 ID。 */ private final Long pharmacyId;
    /** 药房名称。 */ private final String name;
    /** 所属医院 ID。 */ private final Long hospitalId;
    /** 是否为医院默认药房。 */ private final Boolean isDefault;
    /** 模拟配送距离，单位米。 */ private final Long distanceMeters;
    /** 模拟预计配送时效，单位分钟。 */ private final Integer estimatedDeliveryMinutes;
    /** 当前处方在该药房的真实总价，单位分。 */ private final Integer totalAmountCent;
    /** 综合推荐分数，范围 0 至 100。 */ private final Integer score;
    /** 面向用户的固定推荐原因。 */ private final List<String> recommendReasons;
    /** 处方药品的该药房库存和价格。 */ private final List<Item> items;

    /**
     * 推荐药房中的处方药品项。
     */
    @Getter
    @Builder
    public static class Item {
        /** 药品 ID。 */ private final Long drugId;
        /** 处方购买数量。 */ private final Integer quantity;
        /** 当前可售库存。 */ private final Integer availableCount;
        /** 当前库存单价，单位分。 */ private final Integer unitPriceCent;
    }
}
