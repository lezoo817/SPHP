package com.sphp.patient.registration.service;

import com.sphp.patient.registration.vo.DepartmentListVO;
import com.sphp.patient.registration.vo.DoctorPageVO;
import com.sphp.patient.registration.vo.HospitalListVO;

import java.time.LocalDate;
import java.util.List;

/**
 * C端挂号资源查询服务。
 */
public interface RegistrationService {

    /**
     * 查询全部可供 C端选择的医院。
     *
     * @return 启用医院列表
     */
    List<HospitalListVO> listHospitals();

    /**
     * 查询指定可用医院下的启用科室。
     *
     * @param hospitalId 医院 ID
     * @param keyword 可选科室名称关键字
     * @return 可选科室列表
     * @throws com.sphp.patient.auth.exception.CAuthException 医院不存在或已停用时抛出
     */
    List<DepartmentListVO> listDepartments(Long hospitalId, String keyword);

    /**
     * 分页查询指定医院和科室下的可用医生，并统计指定日期的可预约号源。
     *
     * @param hospitalId 医院 ID
     * @param departmentId 科室 ID
     * @param date 出诊日期，未传时使用当前业务日期
     * @param pageNo 页码，未传时使用默认页码
     * @param pageSize 页大小，未传时使用默认页大小
     * @return 医生分页数据
     * @throws com.sphp.patient.auth.exception.CAuthException 资源不可用或医院链路不匹配时抛出
     */
    DoctorPageVO listDoctors(Long hospitalId, Long departmentId, LocalDate date, Integer pageNo, Integer pageSize);
}
