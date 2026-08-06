package com.sphp.admin.config;

import cn.dev33.satoken.stp.StpInterface;
import com.sphp.admin.auth.entity.BUser;
import com.sphp.admin.auth.mapper.BUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 角色 / 权限码接口实现。
 *
 * <p>登录身份经 Sa-Token 无状态 JWT 解析后，本类按 {@code loginId} 从 b_user 表动态加载
 * 角色，供 {@code StpUtil.getRoleList()} 与 {@code @SaCheckRole} 权限注解使用。
 * 角色字段值由 {@link com.sphp.admin.common.enums.BRoleEnum} 约束（ADMIN /
 * DEPT_HEAD / DOCTOR），业务代码严禁直接比较字面量。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final BUserMapper bUserMapper;

    /**
     * 返回当前登录用户的权限码列表。
     *
     * <p>B 端目前仅按角色维度管控（{@code @SaCheckRole}），权限码维度暂未启用，
     * 一律返回空集合，保证 {@code @SaCheckPermission} 默认放行失败时的可预期行为。
     *
     * @param loginId   登录用户 ID（b_user.id）
     * @param loginType 登录类型（多端登录场景下区分；B 端单端登录可忽略）
     * @return 权限码集合（始终为空）
     */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    /**
     * 返回当前登录用户的角色列表。
     *
     * <p>从 b_user 表单值加载：账号被软删时返回空集合，使 Sa-Token 自动判定无权限。
     * 返回值供 {@code StpUtil.hasRole} / {@code @SaCheckRole} 校验，字符串值需与
     * {@link com.sphp.admin.common.enums.BRoleEnum#getCode()} 严格一致。
     *
     * @param loginId   登录用户 ID（b_user.id）
     * @param loginType 登录类型（B 端单端登录可忽略）
     * @return 角色列表；用户不存在或已软删时返回空集合
     */
    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        BUser user = bUserMapper.selectById(Long.valueOf(loginId.toString()));
        return user == null ? List.of() : List.of(user.getRole());
    }
}
