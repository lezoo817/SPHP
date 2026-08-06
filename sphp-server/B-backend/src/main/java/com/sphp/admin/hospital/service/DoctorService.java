package com.sphp.admin.hospital.service;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.hospital.dto.DoctorAccountRequest;
import com.sphp.admin.hospital.dto.DoctorCreateRequest;
import com.sphp.admin.hospital.dto.DoctorPasswordRequest;
import com.sphp.admin.hospital.dto.DoctorStatusRequest;
import com.sphp.admin.hospital.dto.DoctorUpdateRequest;
import com.sphp.admin.hospital.vo.DoctorListVO;

/**
 * 医生管理服务。
 */
public interface DoctorService {

    /**
     * 分页查询医生列表（按当前管理员所属医院过滤，含科室名与登录账号）。
     *
     * @param deptId 科室过滤（可空）
     * @param name   姓名模糊检索（可空）
     * @param status 状态过滤 ENABLED/DISABLED/SUSPENDED（可空）
     * @param page   页码（从 1 开始）
     * @param size   每页大小
     */
    PageResult<DoctorListVO> page(Long deptId, String name, String status, int page, int size);

    /**
     * 新增医生（同一事务同步开通 {@code DOCTOR} 登录账号，并回填 {@code doctor.b_user_id}）。
     *
     * @param request 新增请求
     */
    void create(DoctorCreateRequest request);

    /**
     * 编辑医生（不修改所属科室）。
     *
     * @param id      医生 ID
     * @param request 编辑请求（仅更新非空字段）
     */
    void update(Long id, DoctorUpdateRequest request);

    /**
     * 启用/停用/暂停医生。
     *
     * <p>非 {@code ENABLED} 切换时校验无已发布排班 / 无进行中问诊；通过后更新医生状态，
     * 并联动关联 {@code b_user} 状态（{@code SUSPENDED} 映射为 {@code DISABLED}）。
     *
     * @param id      医生 ID
     * @param request 状态变更请求
     */
    void updateStatus(Long id, DoctorStatusRequest request);

    /**
     * 修改医生登录账号。
     *
     * @param id      医生 ID
     * @param request 新账号请求
     * @throws com.sphp.shared.exception.BusinessException 无关联登录账号（A0121）/ 账号已存在（A0112）
     */
    void updateAccount(Long id, DoctorAccountRequest request);

    /**
     * 重置医生登录密码（jBCrypt 哈希入库，不校验旧密码）。
     *
     * @param id      医生 ID
     * @param request 新密码请求
     * @throws com.sphp.shared.exception.BusinessException 无关联登录账号（A0121）
     */
    void resetPassword(Long id, DoctorPasswordRequest request);
}
