package com.sphp.patient.common.constant;

import org.springframework.data.redis.core.script.DefaultRedisScript;

/**
 * C端挂号订单与支付公共常量。
 */
public final class RegisteringConstant {

    /** 号源实时余量 Redis 键前缀 */
    public static final String SLOT_REMAIN_KEY_PREFIX = "cend:slot:remain:";

    /** 创建挂号订单幂等路径 */
    public static final String APPOINTMENT_CREATE_PATH = "/c/v1/appointments";

    /** 余量充足时原子扣减的 Lua 脚本 */
    public static final DefaultRedisScript<Long> LOCK_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('GET', KEYS[1]); "
                    + "if (not current) then return -1; end; "
                    + "if (tonumber(current) <= 0) then return 0; end; "
                    + "redis.call('DECR', KEYS[1]); return 1;", Long.class);

    /** 仅在补偿时归还一个已预扣余量的 Lua 脚本 */
    public static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "return redis.call('INCR', KEYS[1]);", Long.class);

    /** 业务交换机名称。 */
    public static final String BUSINESS_EXCHANGE = "cend.business.exchange";

    /** 死信交换机名称。 */
    public static final String DLX_EXCHANGE = "cend.dlx.exchange";

    /** 挂号延迟队列名称。 */
    public static final String DELAY_QUEUE = "cend.appointment.delay.queue";

    /** 挂号超时队列名称。 */
    public static final String TIMEOUT_QUEUE = "cend.appointment.timeout.queue";

    /** 锁号路由键。 */
    public static final String LOCKED_KEY = "appointment.locked";

    /** 超时路由键。 */
    public static final String TIMEOUT_KEY = "appointment.timeout";

    /**
     * 防止常量类被实例化。
     */
    private RegisteringConstant() {
    }
}
