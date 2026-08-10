package com.sphp.admin.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * B 端账号状态枚举。
 *
 * <p>同时适用于 {@code b_user.status} 与 {@code doctor.status}，两表状态机一致，
 * 业务代码严禁直接使用字面量比较，必须引用本枚举。
 *
 * <p>典型用法：
 * <pre>{@code
 * if (BUserStatusEnum.isEnabled(user.getStatus())) { ... }
 * }</pre>
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@Getter
@RequiredArgsConstructor
public enum BUserStatusEnum {

    /** 启用：允许登录 / 出现在业务可选列表 */
    ENABLED("ENABLED"),
    /** 停用：禁止登录 / 不出现在业务可选列表（保留历史关联） */
    DISABLED("DISABLED"),
    /** 暂停：医生状态机扩展项（b_user 无此状态，账号联动时降级为 DISABLED） */
    SUSPENDED("SUSPENDED");

    /** 数据库存储值 */
    private final String code;

    /**
     * 判断给定状态值是否为启用态。
     *
     * <p>对 {@code null} 与未知值统一视为非启用，避免 NPE 与意外放行。
     *
     * @param status 数据库取值（可能为 null）
     * @return 当且仅当 {@code status == "ENABLED"} 时返回 {@code true}
     */
    public static boolean isEnabled(String status) {
        return ENABLED.code.equals(status);
    }

    /**
     * 判断给定状态值是否匹配当前枚举值。null-safe。
     *
     * @param code 数据库取值（可能为 null）
     * @return 是否匹配
     */
    public boolean equalsCode(String code) {
        return this.code.equals(code);
    }
}
