package com.sphp.patient.common.constant;

/**
 * C端收货地址与模拟配送公共常量。
 */
public final class DeliveryConstant {

    /** 单个账号允许保存的最大有效收货地址数量。 */
    public static final int MAX_ACTIVE_ADDRESS_COUNT = 20;

    /** 同省模拟配送最短时效，单位分钟。 */
    public static final int SAME_PROVINCE_MINUTES = 900;

    /** 同省模拟配送最长时效，单位分钟。 */
    public static final int SAME_PROVINCE_MAX_MINUTES = 960;


    /** 防止常量类被实例化。 */
    private DeliveryConstant() {
    }
}
