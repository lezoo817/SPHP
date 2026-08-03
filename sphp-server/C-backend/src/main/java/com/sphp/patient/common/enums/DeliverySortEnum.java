package com.sphp.patient.common.enums;

/**
 * 药房推荐结果排序方式枚举。
 */
public enum DeliverySortEnum {

    /** 综合价格、距离和配送时效排序。 */
    RECOMMENDED,
    /** 按处方总价从低到高排序。 */
    PRICE,
    /** 按模拟距离从近到远排序。 */
    DISTANCE,
    /** 按模拟配送时效从短到长排序。 */
    DELIVERY_TIME
}
