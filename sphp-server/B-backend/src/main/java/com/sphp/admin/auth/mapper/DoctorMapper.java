package com.sphp.admin.auth.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.admin.auth.entity.Doctor;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 医生表 Mapper。
 *
 * <p>除 CRUD 外，提供医生停用前置校验所需的排班 / 问诊计数查询：使用 {@link Select}
 * 注解避免额外 XML 维护，单表 COUNT 无需结果映射。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
public interface DoctorMapper extends BaseMapper<Doctor> {

    /**
     * 统计医生已发布（PUBLISHED）排班数量。
     *
     * @param doctorId 医生 ID
     * @return 已发布排班数
     */
    @Select("SELECT COUNT(*) FROM schedule WHERE doctor_id = #{doctorId} AND status = 'PUBLISHED' AND deleted_at IS NULL")
    long countPublishedScheduleByDoctor(@Param("doctorId") Long doctorId);

    /**
     * 统计医生进行中（IN_PROGRESS）问诊数量。
     *
     * @param doctorId 医生 ID
     * @return 进行中问诊数
     */
    @Select("SELECT COUNT(*) FROM consult_record WHERE doctor_id = #{doctorId} AND status = 'IN_PROGRESS' AND deleted_at IS NULL")
    long countInProgressConsultByDoctor(@Param("doctorId") Long doctorId);
}
