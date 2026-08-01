package com.sphp.admin.hospital.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.admin.hospital.entity.Department;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 科室表 Mapper。
 *
 * <p>除 CRUD 外，提供科室停用前置校验所需的排班 / 问诊计数查询（@Select 注解，无需 XML）。
 */
public interface DepartmentMapper extends BaseMapper<Department> {

    /**
     * 统计科室下已发布（PUBLISHED）排班数量。
     *
     * @param deptId 科室 ID
     * @return 已发布排班数
     */
    @Select("SELECT COUNT(*) FROM schedule WHERE dept_id = #{deptId} AND status = 'PUBLISHED' AND deleted_at IS NULL")
    long countPublishedScheduleByDept(@Param("deptId") Long deptId);

    /**
     * 统计科室下进行中（IN_PROGRESS）问诊数量（经 doctor.dept_id 关联）。
     *
     * @param deptId 科室 ID
     * @return 进行中问诊数
     */
    @Select("SELECT COUNT(*) FROM consult_record cr JOIN doctor d ON cr.doctor_id = d.id " +
            "WHERE d.dept_id = #{deptId} AND cr.status = 'IN_PROGRESS' AND cr.deleted_at IS NULL")
    long countInProgressConsultByDept(@Param("deptId") Long deptId);
}
