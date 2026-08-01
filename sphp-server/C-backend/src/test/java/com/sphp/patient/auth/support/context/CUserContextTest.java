package com.sphp.patient.auth.support.context;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * C端当前用户上下文单元测试。
 */
class CUserContextTest {

    /**
     * 每个测试结束后清理线程上下文。
     */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
    }

    /**
     * 验证上下文保存并清理当前 C 端用户。
     */
    @Test
    void setAndClearCurrentUser() {
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.parse("2026-08-02T10:00:00+08:00"), "session-hash"));

        assertEquals(10001L, CUserContext.getRequired().userId());
        CUserContext.clear();
        assertNull(CUserContext.get());
    }
}
