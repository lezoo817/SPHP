package com.sphp.patient.order.vo;

import lombok.Builder;
import lombok.Getter;
import java.util.List;

/** 药房处方药库存响应。 */
@Getter @Builder
public class PharmacyInventoryVO {
    /** 药房 ID */ private final Long pharmacyId;
    /** 药房名称 */ private final String name;
    /** 所属医院 ID */ private final Long hospitalId;
    /** 是否默认药房 */ private final Boolean isDefault;
    /** 配送方式，固定 COURIER */ private final String deliveryMethod;
    /** 处方药品库存 */ private final List<Item> items;
    /** 药品库存项。 */
    @Getter @Builder public static class Item { private final Long drugId; private final Integer availableCount; private final Integer unitPriceCent; }
}
