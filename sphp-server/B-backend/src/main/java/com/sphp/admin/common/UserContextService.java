package com.sphp.admin.common;

import com.sphp.admin.auth.entity.BUser;
import com.sphp.admin.auth.entity.Doctor;
import com.sphp.admin.auth.mapper.BUserMapper;
import com.sphp.admin.auth.mapper.DoctorMapper;
import com.sphp.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

/**
 * 用户上下文加载服务（系分 §8.2）。
 *
 * <p>供 {@link UserContextInterceptor} 按 X-User-Id 查询 b_user 表，补全
 * 角色 / 医院 / 科室 / 医生信息后缓存至 {@link UserContextHolder}。
 * 用户不存在或已停用统一抛 {@code A0301}。
 */
@Service
@RequiredArgsConstructor
public class UserContextService {

    private final BUserMapper bUserMapper;
    private final DoctorMapper doctorMapper;

    /**
     * 按用户 ID 加载完整上下文。
     *
     * @param userId b_user 主键（来自 X-User-Id 请求头）
     * @return 含 deptId 的完整用户上下文
     * @throws BusinessException A0301：用户不存在或已停用
     */
    public UserContext loadUserContext(Long userId) {
        BUser user = bUserMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException("A0301", "用户不存在或已停用");
        }
        // b_user 无 dept_id 列：doctor_id 非空时经 doctor 表补全科室（§5.2.3）
        Long deptId = null;
        if (user.getDoctorId() != null) {
            Doctor doctor = doctorMapper.selectById(user.getDoctorId());
            deptId = (doctor != null && doctor.getDeletedAt() == null) ? doctor.getDeptId() : null;
        }
        return new UserContext(user, deptId);
    }
}
