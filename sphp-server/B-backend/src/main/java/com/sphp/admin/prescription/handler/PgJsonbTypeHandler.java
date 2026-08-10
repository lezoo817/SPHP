package com.sphp.admin.prescription.handler;

import com.baomidou.mybatisplus.extension.handlers.JacksonTypeHandler;
import org.apache.ibatis.type.JdbcType;
import org.postgresql.util.PGobject;

import java.lang.reflect.Field;
import java.sql.PreparedStatement;
import java.sql.SQLException;

/**
 * PostgreSQL {@code jsonb} 类型处理器。
 *
 * <p>MyBatis-Plus 默认 {@link JacksonTypeHandler} 通过 {@code ps.setString()} 绑定 JSON，
 * pgjdbc 驱动会以 {@code varchar} 类型发送参数；而 PostgreSQL 没有 {@code varchar → jsonb}
 * 的隐式转换，导致对 jsonb 列执行 INSERT/UPDATE 时报
 * 「字段的类型为 jsonb，但表达式的类型为 character varying」。
 * 本处理器把序列化后的 JSON 包装为 {@link PGobject}(type=jsonb) 再绑定，等价于 SQL 中的
 * {@code ::jsonb}，驱动据此按 jsonb 类型发送参数。
 *
 * <p>读取方向复用 {@link JacksonTypeHandler} 的反序列化逻辑，无需重复实现。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
public class PgJsonbTypeHandler extends JacksonTypeHandler {

    /**
     * 由 MyBatis TypeHandlerRegistry 按字段 Java 类型实例化。
     *
     * @param type 字段 Java 类型
     */
    public PgJsonbTypeHandler(Class<?> type) {
        super(type);
    }

    /**
     * 由 MyBatis-Plus 按字段与泛型信息实例化（用于解析 {@code List<T>} 等泛型字段）。
     *
     * @param type  字段 Java 类型
     * @param field 字段元数据（含泛型类型）
     */
    public PgJsonbTypeHandler(Class<?> type, Field field) {
        super(type, field);
    }

    /**
     * 将 JSON 字符串以 {@code jsonb} 类型绑定到语句参数，避免 PostgreSQL 拒绝 varchar→jsonb。
     */
    @Override
    public void setNonNullParameter(PreparedStatement ps, int i, Object parameter, JdbcType jdbcType)
            throws SQLException {
        PGobject json = new PGobject();
        json.setType("jsonb");
        json.setValue(toJson(parameter));
        ps.setObject(i, json);
    }
}
