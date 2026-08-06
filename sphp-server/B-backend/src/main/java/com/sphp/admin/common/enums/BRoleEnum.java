package com.sphp.admin.common.enums;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * B 端用户角色枚举。
 *
 * <p>角色值与 {@code b_user.role} 字段一一对应，作为 Sa-Token 权限标识与数据权限（{@code DataScope}）判定的唯一来源。
 * 业务代码严禁直接使用字面量比较，必须引用本枚举。
 *
 * <p>典型用法：
 * <pre>{@code
 * if (BRoleEnum.ADMIN.equalsCode(user.getRole())) { ... }
 * }</pre>
 */
@Getter
@RequiredArgsConstructor
public enum BRoleEnum {

    /** 医院超级管理员：跨科室全量操作权限 */
    ADMIN("ADMIN"),
    /** 科室主任：本管辖科室范围内的全量权限 */
    DEPT_HEAD("DEPT_HEAD"),
    /** 普通医生：仅本人医生维度的操作权限 */
    DOCTOR("DOCTOR");

    /** 数据库存储值 */
    private final String code;

    /**
     * 安全比较：避免对 {@code null} 调用 {@link String#equals(Object)} 抛 NPE。
     *
     * @param code 数据库取值（可能为 null）
     * @return 是否匹配当前枚举值
     */
    public boolean equalsCode(String code) {
        return this.code.equals(code);
    }
}
