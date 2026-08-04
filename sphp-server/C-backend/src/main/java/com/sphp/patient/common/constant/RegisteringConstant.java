package com.sphp.patient.common.constant;

/**
 * C端挂号订单与支付公共常量。
 */
public final class RegisteringConstant {

    /** 号源实时余量 Redis 键前缀 */
    public static final String SLOT_REMAIN_KEY_PREFIX = "cend:slot:remain:";

    /** 创建挂号订单幂等路径 */
    public static final String APPOINTMENT_CREATE_PATH = "/c/v1/appointments";

    /**
     * 防止常量类被实例化。
     */
    private RegisteringConstant() {
    }
}
