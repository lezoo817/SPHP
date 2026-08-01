package com.sphp.admin.config;

import cn.dev33.satoken.stp.StpInterface;
import com.sphp.admin.auth.entity.BUser;
import com.sphp.admin.auth.mapper.BUserMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Sa-Token 角色接口实现：从 b_user 表动态加载角色。
 *
 * <p>供 {@code StpUtil.getRoleList()} 与后续 {@code @SaCheckRole} 权限注解使用。
 */
@Component
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final BUserMapper bUserMapper;

    /** B端当前仅按角色管控，权限码维度暂不启用 */
    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return List.of();
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        BUser user = bUserMapper.selectById(Long.valueOf(loginId.toString()));
        return user == null ? List.of() : List.of(user.getRole());
    }
}
