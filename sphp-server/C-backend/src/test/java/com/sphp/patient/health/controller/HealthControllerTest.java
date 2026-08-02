package com.sphp.patient.health.controller;

import com.sphp.patient.health.handler.HealthExceptionHandler;
import com.sphp.patient.health.service.HealthService;
import com.sphp.patient.health.vo.HealthProfileVO;
import com.sphp.patient.health.vo.HealthRecordVO;
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
 * 健康档案控制器接口测试。
 */
class HealthControllerTest {

    /**
     * 验证健康档案查询接口返回统一响应和脱敏患者资料。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void getHealthRecordReturnsExpectedEnvelope() throws Exception {
        HealthService healthService = mock(HealthService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new HealthController(healthService))
                .setControllerAdvice(new HealthExceptionHandler())
                .build();
        when(healthService.getHealthRecord(20001L)).thenReturn(HealthRecordVO.builder()
                .profile(HealthProfileVO.builder().id(20001L).name("张三").gender("MALE").build())
                .allergies(List.of())
                .medicalHistories(List.of())
                .summary("已记录0项过敏史和0项既往史")
                .build());

        mockMvc.perform(get("/c/v1/health-record").param("patientId", "20001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.message").value("查询成功"))
                .andExpect(jsonPath("$.data.profile.id").value(20001L))
                .andExpect(jsonPath("$.data.profile.name").value("张三"))
                .andExpect(jsonPath("$.data.profile.phone").doesNotExist());
    }
}
