package com.sphp.patient.support.idempotency;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.CAuthDigestUtil;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;

/**
 * C端状态变更接口的 Redis 幂等处理服务。
 */
@Service
@RequiredArgsConstructor
public class CIdempotencyService {

    /** 幂等 Redis 键前缀 */
    private static final String IDEMPOTENCY_KEY_PREFIX = "cend:idempotency:";
    /** 处理中缓存值前缀 */
    private static final String PROCESSING_PREFIX = "PENDING|";

    private final StringRedisTemplate redisTemplate;
    private final ObjectMapper objectMapper;
    private final CIdempotencyProperties properties;

    /**
     * 执行幂等业务操作或重放首次成功结果。
     *
     * @param userId 当前 C端用户 ID
     * @param requestPath 接口相对路径
     * @param idempotencyKey 客户端幂等键
     * @param requestBody 请求业务参数
     * @param dataType 响应业务数据类型
     * @param action 首次请求执行业务回调
     * @return 首次或缓存的成功结果
     * @param <T> 业务数据类型
     * @throws CAuthException 幂等键冲突、处理中或缓存解析失败时抛出
     */
    public <T> IdempotencyPayload<T> execute(Long userId, String requestPath, String idempotencyKey,
                                             Object requestBody, Class<T> dataType,
                                             Supplier<IdempotencyPayload<T>> action) {
        String requestHash = requestHash(requestBody);
        String redisKey = buildRedisKey(userId, requestPath, idempotencyKey);
        String processingValue = PROCESSING_PREFIX + requestHash;

        // 先写入短期占位，保证相同用户、路径和幂等键只有一个请求进入业务层
        Boolean locked = redisTemplate.opsForValue().setIfAbsent(
                redisKey, processingValue, Duration.ofSeconds(properties.getProcessingTtlSeconds()));
        if (Boolean.FALSE.equals(locked)) {
            return readCachedPayload(redisKey, requestHash, dataType);
        }
        if (locked == null) {
            throw duplicateRequest("幂等请求正在处理中");
        }

        try {
            IdempotencyPayload<T> payload = action.get();
            String cachedValue = serializeCachedRecord(requestHash, payload);
            // 只缓存完整成功结果，业务异常时删除占位供客户端重新提交
            redisTemplate.opsForValue().set(
                    redisKey, cachedValue, Duration.ofSeconds(properties.getSuccessTtlSeconds()));
            return payload;
        } catch (RuntimeException e) {
            redisTemplate.delete(redisKey);
            throw e;
        }
    }

    /**
     * 计算请求参数摘要，用于识别同一幂等键的不同请求。
     *
     * @param requestBody 请求业务参数
     * @return SHA-256 十六进制摘要
     * @throws CAuthException JSON 序列化失败时抛出
     */
    public String requestHash(Object requestBody) {
        try {
            return CAuthDigestUtil.sha256Hex(objectMapper.writeValueAsString(requestBody));
        } catch (JsonProcessingException e) {
            throw duplicateRequest("请求摘要计算失败");
        }
    }

    /**
     * 读取并反序列化已缓存的首次成功结果。
     *
     * @param redisKey 幂等 Redis 键
     * @param requestHash 当前请求摘要
     * @param dataType 业务数据类型
     * @return 缓存的成功结果
     * @param <T> 业务数据类型
     */
    private <T> IdempotencyPayload<T> readCachedPayload(String redisKey, String requestHash, Class<T> dataType) {
        String cachedValue = redisTemplate.opsForValue().get(redisKey);
        if (cachedValue == null || cachedValue.startsWith(PROCESSING_PREFIX)) {
            throw duplicateRequest("幂等请求正在处理中");
        }
        try {
            CachedRecord record = objectMapper.readValue(cachedValue, CachedRecord.class);
            if (!requestHash.equals(record.requestHash())) {
                throw duplicateRequest("幂等键与首次请求不一致");
            }
            return new IdempotencyPayload<>(record.message(), objectMapper.readValue(record.dataJson(), dataType));
        } catch (JsonProcessingException e) {
            throw duplicateRequest("幂等结果读取失败");
        }
    }

    /**
     * 序列化可重放的首次成功结果。
     *
     * @param requestHash 请求摘要
     * @param payload 成功结果
     * @return Redis 缓存字符串
     */
    private String serializeCachedRecord(String requestHash, IdempotencyPayload<?> payload) {
        try {
            return objectMapper.writeValueAsString(new CachedRecord(
                    requestHash, payload.message(), objectMapper.writeValueAsString(payload.data())));
        } catch (JsonProcessingException e) {
            throw duplicateRequest("幂等结果缓存失败");
        }
    }

    /**
     * 组装 Redis 幂等键。
     *
     * @param userId 当前用户 ID
     * @param requestPath 接口相对路径
     * @param idempotencyKey 客户端幂等键
     * @return Redis 键
     */
    private String buildRedisKey(Long userId, String requestPath, String idempotencyKey) {
        return IDEMPOTENCY_KEY_PREFIX + userId + ":" + requestPath + ":" + idempotencyKey;
    }

    /**
     * 创建重复请求异常。
     *
     * @param message 用户可读提示
     * @return HTTP 409 业务异常
     */
    private CAuthException duplicateRequest(String message) {
        return new CAuthException(ErrorCodeEnum.DUPLICATE_REQUEST, HttpStatus.CONFLICT, message);
    }

    /**
     * Redis 中缓存的首次成功结果。
     *
     * @param requestHash 首次请求摘要
     * @param message 首次成功提示
     * @param dataJson 首次成功数据 JSON
     */
    public record CachedRecord(String requestHash, String message, String dataJson) {
    }
}
