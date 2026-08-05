package com.sphp.admin.common;

import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sphp.admin.auth.entity.BUser;
import com.sphp.admin.auth.entity.Doctor;
import com.sphp.admin.auth.mapper.BUserMapper;
import com.sphp.admin.auth.mapper.DoctorMapper;
import com.sphp.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 当前登录用户上下文服务。
 *
 * <p>双通道鉴权：Agent 调用经 {@link UserContextInterceptor} 按 X-User-Id 建立上下文后，
 * 优先读取 {@link UserContextHolder}；B 端 Web 沿用 Sa-Token，Service 层通过 {@link StpUtil}
 * 取当前登录 b_user。管理员接口统一要求 ADMIN 角色，并以 ADMIN 所属 {@code hospital_id} 作为数据隔离范围。
 */
@Service
@RequiredArgsConstructor
public class CurrentUserService {

    /** ADMIN 角色常量 */
    public static final String ROLE_ADMIN = "ADMIN";

    private final BUserMapper bUserMapper;
    private final DoctorMapper doctorMapper;

    /**
     * 获取当前登录用户（未登录 / Token 无效 / 用户不存在或已软删均抛 A0301）。
     *
     * <p>双通道：Agent 调用（X-User-Id 已由拦截器写入 {@link UserContextHolder}）优先取线程上下文；
     * 否则回落 Sa-Token Bearer 会话。
     */
    public BUser getCurrentUser() {
        UserContext context = UserContextHolder.getContext();
        if (context != null) {
            return context.user();
        }
        Long userId;
        try {
            userId = StpUtil.getLoginIdAsLong();
        } catch (NotLoginException e) {
            throw new BusinessException("A0301", "Token无效或已过期");
        }
        BUser user = bUserMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException("A0301", "用户不存在或已停用");
        }
        return user;
    }

    /**
     * 获取当前登录管理员所属医院 ID。
     *
     * <p>要求当前用户角色为 ADMIN，否则抛 A0443（管理员接口权限校验）。
     */
    public Long getCurrentHospitalId() {
        BUser user = getCurrentUser();
        if (!ROLE_ADMIN.equals(user.getRole())) {
            throw new BusinessException("A0443", "无操作权限，仅管理员可访问");
        }
        return user.getHospitalId();
    }

    /**
     * 按账号校验是否已存在有效用户（用于账号唯一性检查）。
     *
     * @param account    登录账号
     * @param excludeUserId 需排除的用户 ID（编辑场景传自身，新增场景传 null）
     * @return 是否存在
     */
    public boolean accountExists(String account, Long excludeUserId) {
        return bUserMapper.selectCount(Wrappers.<BUser>lambdaQuery()
                .eq(BUser::getAccount, account)
                .ne(excludeUserId != null, BUser::getId, excludeUserId)
                .isNull(BUser::getDeletedAt)) > 0;
    }

    /**
     * 获取当前登录用户的数据权限范围（系分 §7.2，三角色通用）。
     *
     * <p>排班管理等非纯管理员接口使用本方法：ADMIN 仅按 hospital_id 过滤；
     * DEPT_HEAD 经 doctor.dept_id 取管辖科室；DOCTOR 仅本人（doctor_id）。
     *
     * @return 数据权限范围；doctor_id 为 null（如 ADMIN）时 deptId 为 null
     */
    public DataScope getCurrentDataScope() {
        // Agent 通道：deptId 已由拦截器补全，直接转换（§8.2）
        UserContext context = UserContextHolder.getContext();
        if (context != null) {
            return context.toDataScope();
        }
        BUser user = getCurrentUser();
        Long deptId = null;
        if (user.getDoctorId() != null) {
            Doctor doctor = doctorMapper.selectById(user.getDoctorId());
            deptId = (doctor != null && doctor.getDeletedAt() == null) ? doctor.getDeptId() : null;
        }
        return new DataScope(user.getRole(), user.getHospitalId(), deptId, user.getDoctorId());
    }
}
