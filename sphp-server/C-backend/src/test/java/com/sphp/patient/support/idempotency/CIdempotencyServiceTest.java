package com.sphp.patient.support.idempotency;

import com.sphp.patient.auth.exception.CAuthException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C端接口幂等服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class CIdempotencyServiceTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;

    private CIdempotencyService idempotencyService;

    /**
     * 初始化幂等服务及默认过期配置。
     */
    @BeforeEach
    void setUp() {
        CIdempotencyProperties properties = new CIdempotencyProperties();
        properties.setSuccessTtlSeconds(86400);
        properties.setProcessingTtlSeconds(30);
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        idempotencyService = new CIdempotencyService(redisTemplate, new com.fasterxml.jackson.databind.ObjectMapper(), properties);
    }

    /**
     * 验证首次请求执行回调并缓存成功结果。
     */
    @Test
    void firstRequestExecutesAndCachesSuccessResult() {
        when(valueOperations.setIfAbsent(any(), any(), eq(Duration.ofSeconds(30)))).thenReturn(true);
        AtomicInteger invocationCount = new AtomicInteger();

        IdempotencyPayload<SampleResponse> result = idempotencyService.execute(
                10001L, "/c/v1/family-members", "key-001", Map.of("name", "张三"),
                SampleResponse.class,
                () -> {
                    invocationCount.incrementAndGet();
                    return new IdempotencyPayload<>("家庭成员已添加", new SampleResponse(20001L, "张三"));
                }
        );

        assertEquals(1, invocationCount.get());
        assertEquals(20001L, result.data().patientId());
        assertEquals("家庭成员已添加", result.message());
        verify(valueOperations).set(any(), any(), eq(Duration.ofSeconds(86400)));
    }

    /**
     * 验证同一幂等键与请求摘要直接重放首次成功结果。
     */
    @Test
    void sameRequestReplaysCachedSuccessResult() throws Exception {
        String requestHash = idempotencyService.requestHash(Map.of("name", "张三"));
        String cachedValue = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                new CIdempotencyService.CachedRecord(requestHash, "家庭成员已添加",
                        "{\"patientId\":20001,\"name\":\"张三\"}"));
        when(valueOperations.setIfAbsent(any(), any(), eq(Duration.ofSeconds(30)))).thenReturn(false);
        when(valueOperations.get(any())).thenReturn(cachedValue);

        IdempotencyPayload<SampleResponse> result = idempotencyService.execute(
                10001L, "/c/v1/family-members", "key-001", Map.of("name", "张三"),
                SampleResponse.class,
                () -> {
                    throw new AssertionError("缓存命中时不应执行业务回调");
                }
        );

        assertEquals(20001L, result.data().patientId());
        assertEquals("张三", result.data().name());
    }

    /**
     * 验证相同幂等键携带不同请求摘要时拒绝执行。
     */
    @Test
    void differentRequestWithSameKeyIsRejected() throws Exception {
        String cachedValue = new com.fasterxml.jackson.databind.ObjectMapper().writeValueAsString(
                new CIdempotencyService.CachedRecord("another-hash", "家庭成员已添加", "{}"));
        when(valueOperations.setIfAbsent(any(), any(), eq(Duration.ofSeconds(30)))).thenReturn(false);
        when(valueOperations.get(any())).thenReturn(cachedValue);

        CAuthException exception = assertThrows(CAuthException.class, () -> idempotencyService.execute(
                10001L, "/c/v1/family-members", "key-001", Map.of("name", "李四"),
                SampleResponse.class,
                () -> new IdempotencyPayload<>("不应执行", new SampleResponse(20002L, "李四"))
        ));

        assertEquals("A0506", exception.getCode());
    }

    /**
     * 幂等缓存测试响应对象。
     *
     * @param patientId 就诊人 ID
     * @param name 姓名
     */
    private record SampleResponse(Long patientId, String name) {
    }
}
