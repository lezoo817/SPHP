package com.sphp.patient.registration.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.util.List;

/**
 * C端挂号资源跨表查询接口。
 */
@Mapper
public interface RegistrationResourceMapper {

    /**
     * 查询全部启用医院。
     *
     * @return 可选医院列表
     */
    List<HospitalRecord> selectAvailableHospitals();

    /**
     * 查询指定启用医院。
     *
     * @param hospitalId 医院 ID
     * @return 医院记录，不存在或不可用时返回 null
     */
    HospitalRecord selectAvailableHospital(@Param("hospitalId") Long hospitalId);

    /**
     * 查询医院下启用科室。
     *
     * @param hospitalId 医院 ID
     * @param keyword 可选科室名称关键词
     * @return 科室列表
     */
    List<DepartmentRecord> selectAvailableDepartments(@Param("hospitalId") Long hospitalId,
                                                       @Param("keyword") String keyword);

    /**
     * 查询指定启用科室及医院归属。
     *
     * @param departmentId 科室 ID
     * @return 科室归属记录，不存在或不可用时返回 null
     */
    DepartmentLinkRecord selectAvailableDepartmentLink(@Param("departmentId") Long departmentId);

    /**
     * 分页查询医院科室下启用医生及指定日期余量。
     *
     * @param hospitalId 医院 ID
     * @param departmentId 科室 ID
     * @param date 出诊日期
     * @param limit 页大小
     * @param offset 起始偏移量
     * @return 医生列表
     */
    List<DoctorRecord> selectAvailableDoctors(@Param("hospitalId") Long hospitalId,
                                               @Param("departmentId") Long departmentId,
                                               @Param("date") LocalDate date,
                                               @Param("limit") int limit,
                                               @Param("offset") long offset);

    /**
     * 统计医院科室下启用医生总数。
     *
     * @param hospitalId 医院 ID
     * @param departmentId 科室 ID
     * @return 医生总数
     */
    long countAvailableDoctors(@Param("hospitalId") Long hospitalId, @Param("departmentId") Long departmentId);

    /**
     * 查询指定启用医生及医院归属。
     *
     * @param doctorId 医生 ID
     * @return 医生归属记录，不存在或不可用时返回 null
     */
    DoctorLinkRecord selectAvailableDoctorLink(@Param("doctorId") Long doctorId);

    /**
     * 判断医生在指定日期是否存在已发布排班。
     *
     * @param doctorId 医生 ID
     * @param date 排班日期
     * @return 存在时返回 true
     */
    boolean hasPublishedSchedule(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);

    /**
     * 查询指定医生在指定日期已发布排班下的号源时段。
     *
     * @param doctorId 医生 ID
     * @param date 排班日期
     * @return 已发布时段列表
     */
    List<SlotRecord> selectPublishedSlots(@Param("doctorId") Long doctorId, @Param("date") LocalDate date);
}
