package com.sphp.patient.registration.support;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.common.constant.RegisteringConstant;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.List;

/**
 * C端挂号号源 Redis 原子预扣与补偿服务。
 */
@Service
@RequiredArgsConstructor
public class RegisteringSlotLockService {

    /** 余量充足时原子扣减的 Lua 脚本 */
    private static final DefaultRedisScript<Long> LOCK_SCRIPT = new DefaultRedisScript<>(
            "local current = redis.call('GET', KEYS[1]); "
                    + "if (not current) then return -1; end; "
                    + "if (tonumber(current) <= 0) then return 0; end; "
                    + "return redis.call('DECR', KEYS[1]);", Long.class);
    /** 仅在补偿时归还一个已预扣余量的 Lua 脚本 */
    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>(
            "return redis.call('INCR', KEYS[1]);", Long.class);

    private final StringRedisTemplate redisTemplate;

    /**
     * 初始化余量并以 Lua 原子预扣一个号源。
     *
     * @param slotId 时段 ID
     * @param databaseAvailableCount PostgreSQL 可用快照数
     * @param ttl 余量缓存有效期
     * @return 预扣成功时返回 true，余量不足时返回 false
     * @throws CAuthException Redis 不可用或脚本执行失败时抛出
     */
    public boolean registeringLock(Long slotId, long databaseAvailableCount, Duration ttl) {
        String key = RegisteringConstant.SLOT_REMAIN_KEY_PREFIX + slotId;
        try {
            // 缓存缺失时仅由首个请求基于数据库快照回填，避免覆盖并发扣减后的余量。
            redisTemplate.opsForValue().setIfAbsent(key, Long.toString(databaseAvailableCount), ttl);
            Long result = redisTemplate.execute(LOCK_SCRIPT, List.of(key));
            if (result == null) {
                throw systemError("号源预扣脚本未返回结果");
            }
            return result >= 0;
        } catch (CAuthException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw systemError("号源服务暂不可用");
        }
    }

    /**
     * 在订单事务失败、取消或超时时归还一个已预扣号源。
     *
     * @param slotId 时段 ID
     * @throws CAuthException Redis 补偿失败时抛出
     */
    public void registeringUnlock(Long slotId) {
        String key = RegisteringConstant.SLOT_REMAIN_KEY_PREFIX + slotId;
        try {
            Long result = redisTemplate.execute(UNLOCK_SCRIPT, List.of(key));
            if (result == null) {
                throw systemError("号源补偿脚本未返回结果");
            }
        } catch (CAuthException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw systemError("号源补偿失败");
        }
    }

    /**
     * 创建 Redis 依赖异常，避免向客户端泄漏底层连接信息。
     *
     * @param message 面向客户端的提示信息
     * @return 系统异常
     */
    private CAuthException systemError(String message) {
        return new CAuthException(ErrorCodeEnum.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
