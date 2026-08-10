package com.sphp.admin.hospital.service;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.hospital.dto.DepartmentCreateRequest;
import com.sphp.admin.hospital.dto.DepartmentStatusRequest;
import com.sphp.admin.hospital.dto.DepartmentUpdateRequest;
import com.sphp.admin.hospital.vo.DepartmentDetailVO;
import com.sphp.admin.hospital.vo.DepartmentListVO;

/**
 * 科室管理服务。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
public interface DepartmentService {

    /**
     * 分页查询科室列表（按当前管理员所属医院过滤）。
     *
     * @param name           科室名称模糊检索（可空）
     * @param headDoctorName 科室主任姓名模糊检索（可空）
     * @param status         状态过滤 ENABLED/DISABLED（可空）
     * @param page           页码（从 1 开始）
     * @param size           每页大小
     */
    PageResult<DepartmentListVO> page(String name, String headDoctorName, String status, int page, int size);

    /**
     * 查询科室详情（含医生数量、负责人姓名）。
     */
    DepartmentDetailVO detail(Long id);

    /**
     * 新增科室（医院归属自动填充为当前管理员所属医院）。
     *
     * @param request 新增请求
     */
    void create(DepartmentCreateRequest request);

    /**
     * 编辑科室。
     *
     * @param id      科室 ID
     * @param request 编辑请求
     */
    void update(Long id, DepartmentUpdateRequest request);

    /**
     * 启用/停用科室（停用时执行前置校验：无启用医生 / 已发布排班 / 进行中问诊，
     * 校验通过后同步停用该科室下所有医生）。
     *
     * @param id      科室 ID
     * @param request 启停请求
     */
    void updateStatus(Long id, DepartmentStatusRequest request);
}
