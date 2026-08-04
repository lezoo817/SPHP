package com.sphp.patient.common.enums;

import java.util.Arrays;

/**
 * 演示配送覆盖的省市枚举。
 */
public enum DeliveryProvinceEnum {

    /** 河南省。 */
    HENAN("河南省"),
    /** 上海市。 */
    SHANGHAI("上海市"),
    /** 北京市。 */
    BEIJING("北京市"),
    /** 江苏省。 */
    JIANGSU("江苏省"),
    /** 浙江省。 */
    ZHEJIANG("浙江省"),
    /** 广东省。 */
    GUANGDONG("广东省");

    /** 面向用户展示及医院地址解析的省市名称。 */
    private final String displayName;

    DeliveryProvinceEnum(String displayName) {
        this.displayName = displayName;
    }

    /**
     * 获取省市展示名称。
     *
     * @return 省市中文名称
     */
    public String getDisplayName() {
        return displayName;
    }

    /**
     * 从医院地址文本解析支持的省市。
     *
     * @param address 医院地址文本
     * @return 匹配到的省市；未匹配时返回 {@code null}
     */
    public static DeliveryProvinceEnum resolveFromAddress(String address) {
        if (address == null || address.isBlank()) {
            return null;
        }
        return Arrays.stream(values()).filter(item -> address.contains(item.displayName)
                        || address.contains(item.displayName.substring(0, 2)))
                .findFirst().orElse(null);
    }
}
