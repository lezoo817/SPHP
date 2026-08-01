package com.sphp.patient.family.controller;

import com.sphp.patient.family.handler.FamilyExceptionHandler;
import com.sphp.patient.family.service.FamilyService;
import com.sphp.patient.family.vo.FamilyMemberListVO;
import org.junit.jupiter.api.BeforeEach;
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
 * 家庭成员控制器接口测试。
 */
class FamilyControllerTest {

    private FamilyService familyService;
    private MockMvc mockMvc;

    /**
     * 初始化独立家庭成员控制器测试环境。
     */
    @BeforeEach
    void setUp() {
        familyService = mock(FamilyService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FamilyController(familyService))
                .setControllerAdvice(new FamilyExceptionHandler())
                .build();
    }

    /**
     * 验证家庭成员列表接口返回统一响应和数组数据。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void listFamilyMembersReturnsExpectedEnvelope() throws Exception {
        when(familyService.listFamilyMembers()).thenReturn(List.of(FamilyMemberListVO.builder()
                .patientId(20001L)
                .name("张三")
                .relation("SELF")
                .relationName("本人")
                .isDefault(true)
                .build()));

        mockMvc.perform(get("/c/v1/family-members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.message").value("查询成功"))
                .andExpect(jsonPath("$.data[0].patientId").value(20001L))
                .andExpect(jsonPath("$.data[0].relationName").value("本人"));
    }
}
