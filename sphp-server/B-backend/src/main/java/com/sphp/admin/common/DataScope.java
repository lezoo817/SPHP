package com.sphp.admin.common;

/**
 * 当前登录用户的数据权限范围。
 *
 * <p>由 {@link CurrentUserService#getCurrentDataScope()} 解析，Service 层据此显式拼接过滤条件：
 * <ul>
 *     <li>ADMIN：仅按 {@code hospital_id} 过滤（deptId/doctorId 为 null）</li>
 *     <li>DEPT_HEAD：额外按 {@code dept_id = 管辖科室} 过滤（经 doctor.dept_id 获取）</li>
 *     <li>DOCTOR：额外按 {@code doctor_id = 本人} 过滤</li>
 * </ul>
 *
 * @param role       角色：ADMIN / DEPT_HEAD / DOCTOR
 * @param hospitalId 所属医院 ID（始终非空）
 * @param deptId     数据权限科室 ID（ADMIN 为 null；DEPT_HEAD 为管辖科室；DOCTOR 通常为本人科室）
 * @param doctorId   数据权限医生 ID（ADMIN 为 null；DOCTOR 为本人）
 *
 * @author lezoo17
 * @since 2026-08-10
 */
public record DataScope(String role, Long hospitalId, Long deptId, Long doctorId) {
}
