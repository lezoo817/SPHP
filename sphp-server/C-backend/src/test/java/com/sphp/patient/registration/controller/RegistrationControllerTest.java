package com.sphp.patient.registration.controller;

import com.sphp.patient.registration.handler.RegistrationExceptionHandler;
import com.sphp.patient.registration.service.RegistrationService;
import com.sphp.patient.registration.vo.HospitalListVO;
import com.sphp.patient.registration.vo.DepartmentListVO;
import com.sphp.patient.registration.vo.DoctorListItemVO;
import com.sphp.patient.registration.vo.DoctorPageVO;
import com.sphp.patient.registration.vo.AppointmentSlotVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 挂号资源查询控制器接口测试。
 */
class RegistrationControllerTest {

    /**
     * 验证可用医院查询返回统一响应和医院资料。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void listHospitalsReturnsExpectedEnvelope() throws Exception {
        RegistrationService registrationService = mock(RegistrationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RegistrationController(registrationService))
                .setControllerAdvice(new RegistrationExceptionHandler())
                .build();
        when(registrationService.listHospitals()).thenReturn(List.of(HospitalListVO.builder()
                .hospitalId(101L)
                .name("智愈先锋第一医院")
                .level("三级甲等")
                .address("北京市东城区示例路1号")
                .contact("010-12345678")
                .build()));

        mockMvc.perform(get("/c/v1/hospitals"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.message").value("查询成功"))
                .andExpect(jsonPath("$.data[0].hospitalId").value(101L))
                .andExpect(jsonPath("$.data[0].name").value("智愈先锋第一医院"));
    }

    /**
     * 验证科室查询接口返回当前医院下的启用科室。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void listDepartmentsReturnsExpectedEnvelope() throws Exception {
        RegistrationService registrationService = mock(RegistrationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RegistrationController(registrationService))
                .setControllerAdvice(new RegistrationExceptionHandler())
                .build();
        when(registrationService.listDepartments(101L, "呼吸")).thenReturn(List.of(DepartmentListVO.builder()
                .id(301L).name("呼吸内科").location("1号楼2层201室").build()));

        mockMvc.perform(get("/c/v1/departments").param("hospitalId", "101").param("keyword", "呼吸"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data[0].id").value(301L))
                .andExpect(jsonPath("$.data[0].name").value("呼吸内科"));
    }

    /**
     * 验证医生查询接口返回分页信封和指定日期的可预约余量。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void listDoctorsReturnsExpectedEnvelope() throws Exception {
        RegistrationService registrationService = mock(RegistrationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RegistrationController(registrationService))
                .setControllerAdvice(new RegistrationExceptionHandler())
                .build();
        when(registrationService.listDoctors(101L, 301L, java.time.LocalDate.of(2026, 8, 3), 1, 20))
                .thenReturn(DoctorPageVO.builder()
                        .pageNo(1)
                        .pageSize(20)
                        .total(1L)
                        .records(List.of(DoctorListItemVO.builder()
                                .id(501L).name("张医生").title("主任医师")
                                .specialty("呼吸内科").registrationFeeCent(5000).availableCount(8L).build()))
                        .build());

        mockMvc.perform(get("/c/v1/doctors").param("hospitalId", "101").param("departmentId", "301")
                        .param("date", "2026-08-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].id").value(501L))
                .andExpect(jsonPath("$.data.records[0].availableCount").value(8));
    }

    /**
     * 验证医生可预约时段接口返回实时余量和带时区的时间字段。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void listDoctorSlotsReturnsExpectedEnvelope() throws Exception {
        RegistrationService registrationService = mock(RegistrationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RegistrationController(registrationService))
                .setControllerAdvice(new RegistrationExceptionHandler())
                .build();
        when(registrationService.listDoctorSlots(101L, 501L, java.time.LocalDate.of(2026, 8, 3)))
                .thenReturn(List.of(AppointmentSlotVO.builder()
                        .slotId(1001L)
                        .startTime(java.time.OffsetDateTime.parse("2026-08-03T08:00:00+08:00"))
                        .endTime(java.time.OffsetDateTime.parse("2026-08-03T08:30:00+08:00"))
                        .feeCent(5000)
                        .availableCount(3L)
                        .scheduleStatus("PUBLISHED")
                        .build()));

        mockMvc.perform(get("/c/v1/doctors/501/slots").param("hospitalId", "101").param("date", "2026-08-03"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data[0].slotId").value(1001L))
                .andExpect(jsonPath("$.data[0].availableCount").value(3))
                .andExpect(jsonPath("$.data[0].scheduleStatus").value("PUBLISHED"));
    }

    /**
     * 验证无法解析的时段日期按参数错误返回，避免错误进入系统异常响应。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void listDoctorSlotsRejectsMalformedDate() throws Exception {
        RegistrationService registrationService = mock(RegistrationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new RegistrationController(registrationService))
                .setControllerAdvice(new RegistrationExceptionHandler())
                .build();

        mockMvc.perform(get("/c/v1/doctors/501/slots").param("hospitalId", "101").param("date", "invalid"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"));
    }
}
