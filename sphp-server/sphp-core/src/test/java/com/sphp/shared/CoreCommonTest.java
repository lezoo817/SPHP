package com.sphp.shared;

import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import com.sphp.shared.entity.BaseDO;
import com.sphp.shared.exception.BusinessException;
import com.sphp.shared.handler.CommonMetaObjectHandler;
import com.sphp.shared.result.Result;
import org.apache.ibatis.reflection.SystemMetaObject;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * Core 公共基础能力单元测试。
 */
class CoreCommonTest {

    /**
     * 初始化测试实体的 MyBatis-Plus 元数据，使自动填充路径与 Mapper 运行时一致。
     */
    @BeforeAll
    static void initializeTableInfo() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), AuditEntity.class);
    }

    /**
     * 验证插入时补齐为空的审计时间字段。
     */
    @Test
    void insertFillPopulatesMissingAuditTimes() {
        AuditEntity entity = new AuditEntity();

        new CommonMetaObjectHandler().insertFill(SystemMetaObject.forObject(entity));

        assertNotNull(entity.getCreatedAt());
        assertNotNull(entity.getUpdatedAt());
    }

    /**
     * 验证自动填充不覆盖业务显式指定的审计时间。
     */
    @Test
    void fillDoesNotOverwriteExplicitAuditTimes() {
        OffsetDateTime createdAt = OffsetDateTime.parse("2026-01-01T08:00:00+08:00");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-01-02T08:00:00+08:00");
        AuditEntity entity = new AuditEntity();
        entity.setCreatedAt(createdAt);
        entity.setUpdatedAt(updatedAt);

        CommonMetaObjectHandler handler = new CommonMetaObjectHandler();
        handler.insertFill(SystemMetaObject.forObject(entity));
        handler.updateFill(SystemMetaObject.forObject(entity));

        assertSame(createdAt, entity.getCreatedAt());
        assertSame(updatedAt, entity.getUpdatedAt());
    }

    /**
     * 验证更新时仅补齐为空的更新时间。
     */
    @Test
    void updateFillPopulatesMissingUpdatedAt() {
        AuditEntity entity = new AuditEntity();

        new CommonMetaObjectHandler().updateFill(SystemMetaObject.forObject(entity));

        assertNotNull(entity.getUpdatedAt());
    }

    /**
     * 验证业务码枚举完整收录系分规定的公共错误码。
     */
    @Test
    void errorCodeEnumContainsAllSpecifiedCodes() {
        Set<String> expectedCodeMessages = Set.of(
                "00000|一切正常", "A0111|账号已存在", "A0120|密码校验失败", "A0203|账号已停用",
                "A0210|用户登录失败", "A0211|用户输入密码错误次数超限", "A0230|用户登录已过期",
                "A0240|用户验证码错误", "A0241|用户验证码尝试次数超限", "A0301|访问未授权",
                "A0341|用户签名异常", "A0400|用户请求参数错误", "A0402|无效的用户输入",
                "A0420|请求参数值超出允许范围", "A0430|用户输入内容非法", "A0441|用户支付超时",
                "A0443|订单已关闭或状态不可操作", "A0501|请求次数超出限制", "A0506|用户重复请求",
                "B0001|系统执行出错", "B0201|系统高并发库存竞争", "B0202|系统业务状态冲突",
                "B0300|系统资源或库存不足", "C0001|调用第三方服务出错", "C0200|第三方系统执行超时",
                "C0500|通知服务出错"
        );
        Set<String> actualCodeMessages = java.util.Arrays.stream(ErrorCodeEnum.values())
                .map(errorCode -> errorCode.getCode() + "|" + errorCode.getMessage())
                .collect(Collectors.toSet());

        assertEquals(26, ErrorCodeEnum.values().length);
        assertEquals(expectedCodeMessages, actualCodeMessages);
    }

    /**
     * 验证枚举异常、枚举响应与既有字符串 API 的兼容性。
     */
    @Test
    void exceptionAndResultSupportEnumAndStringErrorCodes() {
        BusinessException defaultException = new BusinessException(ErrorCodeEnum.UNAUTHORIZED);
        BusinessException customException = new BusinessException(ErrorCodeEnum.INVALID_PARAMETER, "参数不符合当前操作要求");
        BusinessException legacyException = new BusinessException("A0301", "登录状态已失效");
        Result<Void> enumResult = Result.error(ErrorCodeEnum.OUT_OF_STOCK);
        Result<Void> customResult = Result.error(ErrorCodeEnum.INVALID_PARAMETER, "请求体缺失或格式错误");
        Result<Void> legacyResult = Result.error("A0301", "登录状态已失效");

        assertEquals("A0301", defaultException.getCode());
        assertEquals("访问未授权", defaultException.getMessage());
        assertEquals("A0400", customException.getCode());
        assertEquals("参数不符合当前操作要求", customException.getMessage());
        assertEquals("A0301", legacyException.getCode());
        assertEquals("B0300", enumResult.getCode());
        assertEquals("系统资源或库存不足", enumResult.getMessage());
        assertEquals("A0400", customResult.getCode());
        assertEquals("请求体缺失或格式错误", customResult.getMessage());
        assertEquals("A0301", legacyResult.getCode());
    }

    /**
     * 用于验证审计字段映射的测试实体。
     */
    @TableName("test_audit_entity")
    private static class AuditEntity extends BaseDO {
    }
}
