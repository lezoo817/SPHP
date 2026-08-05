package com.sphp.admin.common;

import com.sphp.admin.auth.entity.BUser;

/**
 * 当前登录用户的完整上下文（系分 §8.2）。
 *
 * <p>由 {@link UserContextService#loadUserContext(Long)} 加载：b_user 无 dept_id 列，
 * 科室 ID 经 doctor_id 联查 doctor 表补全。业务层通过 {@link UserContextHolder#getContext()}
 * 获取，供数据权限（{@link DataScope}，§7.2）过滤使用。
 *
 * @param user   b_user 实体（含 role / hospitalId / doctorId）
 * @param deptId 数据权限科室 ID（ADMIN 为 null；DEPT_HEAD 为管辖科室；DOCTOR 为本人科室）
 */
public record UserContext(BUser user, Long deptId) {

    public String role() {
        return user.getRole();
    }

    public Long hospitalId() {
        return user.getHospitalId();
    }

    public Long doctorId() {
        return user.getDoctorId();
    }

    /** 转换为数据权限范围（§7.2 三角色）。 */
    public DataScope toDataScope() {
        return new DataScope(user.getRole(), user.getHospitalId(), deptId, user.getDoctorId());
    }
}
