package com.sphp.patient.registration.controller;

import com.sphp.patient.registration.handler.RegistrationExceptionHandler;
import com.sphp.patient.registration.service.RegistrationService;
import com.sphp.patient.registration.vo.HospitalListVO;
import com.sphp.patient.registration.vo.DepartmentListVO;
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
                .id(301L).name("呼吸内科").description("呼吸系统疾病诊疗").build()));

        mockMvc.perform(get("/c/v1/departments").param("hospitalId", "101").param("keyword", "呼吸"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data[0].id").value(301L))
                .andExpect(jsonPath("$.data[0].name").value("呼吸内科"));
    }
}
