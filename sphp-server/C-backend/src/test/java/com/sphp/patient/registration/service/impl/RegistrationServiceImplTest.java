package com.sphp.patient.registration.service.impl;

import com.sphp.patient.registration.mapper.RegistrationResourceMapper;
import com.sphp.patient.registration.mapper.HospitalRecord;
import com.sphp.patient.registration.mapper.DepartmentRecord;
import com.sphp.patient.registration.mapper.DepartmentLinkRecord;
import com.sphp.patient.registration.mapper.DoctorRecord;
import com.sphp.patient.registration.vo.HospitalListVO;
import com.sphp.patient.registration.vo.DepartmentListVO;
import com.sphp.patient.registration.vo.DoctorPageVO;
import com.sphp.patient.auth.exception.CAuthException;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 挂号资源查询服务单元测试。
 */
class RegistrationServiceImplTest {

    /**
     * 验证仅映射可用医院的前端展示字段。
     */
    @Test
    void listHospitalsReturnsAvailableHospitalFields() {
        RegistrationResourceMapper resourceMapper = mock(RegistrationResourceMapper.class);
        RegistrationServiceImpl registrationService = new RegistrationServiceImpl(resourceMapper);
        when(resourceMapper.selectAvailableHospitals()).thenReturn(List.of(
                new HospitalRecord(101L, "智愈先锋第一医院", "三级甲等", "北京市东城区示例路1号", "010-12345678")
        ));

        List<HospitalListVO> result = registrationService.listHospitals();

        assertEquals(1, result.size());
        assertEquals(101L, result.getFirst().getHospitalId());
        assertEquals("智愈先锋第一医院", result.getFirst().getName());
        assertEquals("三级甲等", result.getFirst().getLevel());
        assertEquals("010-12345678", result.getFirst().getContact());
    }

    /**
     * 验证查询科室前校验医院可用，并按关键词返回启用科室。
     */
    @Test
    void listDepartmentsValidatesHospitalAndReturnsDepartmentFields() {
        RegistrationResourceMapper resourceMapper = mock(RegistrationResourceMapper.class);
        RegistrationServiceImpl registrationService = new RegistrationServiceImpl(resourceMapper);
        when(resourceMapper.selectAvailableHospital(101L)).thenReturn(
                new HospitalRecord(101L, "智愈先锋第一医院", "三级甲等", "北京市东城区示例路1号", "010-12345678"));
        when(resourceMapper.selectAvailableDepartments(101L, "呼吸")).thenReturn(List.of(
                new DepartmentRecord(301L, "呼吸内科", "呼吸系统疾病诊疗")
        ));

        List<DepartmentListVO> result = registrationService.listDepartments(101L, "呼吸");

        assertEquals(1, result.size());
        assertEquals(301L, result.getFirst().getId());
        assertEquals("呼吸内科", result.getFirst().getName());
        assertEquals("呼吸系统疾病诊疗", result.getFirst().getDescription());
    }

    /**
     * 验证医生查询校验医院与科室归属后，按指定日期和分页条件返回可预约余量。
     */
    @Test
    void listDoctorsValidatesResourceChainAndReturnsPagedDoctors() {
        RegistrationResourceMapper resourceMapper = mock(RegistrationResourceMapper.class);
        RegistrationServiceImpl registrationService = new RegistrationServiceImpl(resourceMapper);
        LocalDate date = LocalDate.of(2026, 8, 3);
        when(resourceMapper.selectAvailableHospital(101L)).thenReturn(
                new HospitalRecord(101L, "智慧先锋第一医院", "三级甲等", "北京市东城区示例路1号", "010-12345678"));
        when(resourceMapper.selectAvailableDepartmentLink(301L)).thenReturn(new DepartmentLinkRecord(301L, 101L));
        when(resourceMapper.selectAvailableDoctors(101L, 301L, date, 2, 2)).thenReturn(List.of(
                new DoctorRecord(501L, "张医生", "主任医师", "呼吸内科", 5000, 8L)
        ));
        when(resourceMapper.countAvailableDoctors(101L, 301L)).thenReturn(3L);

        DoctorPageVO result = registrationService.listDoctors(101L, 301L, date, 2, 2);

        assertEquals(2, result.getPageNo());
        assertEquals(2, result.getPageSize());
        assertEquals(3L, result.getTotal());
        assertEquals(501L, result.getRecords().getFirst().getId());
        assertEquals(8L, result.getRecords().getFirst().getAvailableCount());
    }

    /**
     * 验证科室不属于所选医院时拒绝查询，防止跨医院读取医生资源。
     */
    @Test
    void listDoctorsRejectsDepartmentFromAnotherHospital() {
        RegistrationResourceMapper resourceMapper = mock(RegistrationResourceMapper.class);
        RegistrationServiceImpl registrationService = new RegistrationServiceImpl(resourceMapper);
        when(resourceMapper.selectAvailableHospital(101L)).thenReturn(
                new HospitalRecord(101L, "智慧先锋第一医院", "三级甲等", "北京市东城区示例路1号", "010-12345678"));
        when(resourceMapper.selectAvailableDepartmentLink(301L)).thenReturn(new DepartmentLinkRecord(301L, 102L));

        CAuthException exception = assertThrows(CAuthException.class,
                () -> registrationService.listDoctors(101L, 301L, LocalDate.of(2026, 8, 3), 1, 20));

        assertEquals("A0301", exception.getCode());
    }
}
