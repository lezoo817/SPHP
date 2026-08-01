package com.sphp.patient.family.controller;

import com.sphp.patient.family.handler.FamilyExceptionHandler;
import com.sphp.patient.family.service.FamilyService;
import com.sphp.patient.family.vo.FamilyMemberListVO;
import com.sphp.patient.family.dto.FamilyMemberCreateRequest;
import com.sphp.patient.family.dto.FamilyMemberUpdateRequest;
import com.sphp.patient.family.vo.FamilyMemberCreateVO;
import com.sphp.patient.family.vo.FamilyMemberUpdateVO;
import com.sphp.patient.family.vo.FamilyMemberUnbindVO;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import java.time.OffsetDateTime;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * 家庭成员控制器接口测试。
 */
class FamilyControllerTest {

    private FamilyService familyService;
    private CIdempotencyService idempotencyService;
    private MockMvc mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 初始化独立家庭成员控制器测试环境。
     */
    @BeforeEach
    void setUp() {
        familyService = mock(FamilyService.class);
        idempotencyService = mock(CIdempotencyService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new FamilyController(familyService, idempotencyService))
                .setControllerAdvice(new FamilyExceptionHandler())
                .build();
    }

    /**
     * 每个测试结束后清理当前用户上下文。
     */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
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

    /**
     * 验证新增家庭成员接口返回幂等成功结果。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void createFamilyMemberReturnsCreatedMember() throws Exception {
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        FamilyMemberCreateRequest request = new FamilyMemberCreateRequest();
        request.setName("张小明");
        request.setRelation("CHILD");
        when(idempotencyService.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(new IdempotencyPayload<>("家庭成员已添加", FamilyMemberCreateVO.builder()
                        .patientId(20002L).name("张小明").relation("CHILD").isDefault(false).build()));

        mockMvc.perform(post("/c/v1/family-members")
                        .header("X-Idempotency-Key", "key-001")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("家庭成员已添加"))
                .andExpect(jsonPath("$.data.patientId").value(20002L));
    }

    /**
     * 验证更新家庭成员接口返回更新后的脱敏资料。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void updateFamilyMemberReturnsUpdatedMember() throws Exception {
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        FamilyMemberUpdateRequest request = new FamilyMemberUpdateRequest();
        request.setName("张小明");
        request.setRelation("CHILD");
        when(idempotencyService.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(new IdempotencyPayload<>("家庭成员资料已更新", FamilyMemberUpdateVO.builder()
                        .patientId(20002L).name("张小明").relation("CHILD")
                        .phone("138****8002").updatedAt(OffsetDateTime.now()).build()));

        mockMvc.perform(put("/c/v1/family-members/20002")
                        .header("X-Idempotency-Key", "key-002")
                        .contentType(APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("家庭成员资料已更新"))
                .andExpect(jsonPath("$.data.phone").value("138****8002"));
    }

    /**
     * 验证停用解绑接口返回当前关系的解绑结果。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void unbindFamilyMemberReturnsUnboundResult() throws Exception {
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        when(idempotencyService.execute(any(), any(), any(), any(), any(), any()))
                .thenReturn(new IdempotencyPayload<>("家庭成员已解绑", FamilyMemberUnbindVO.builder()
                        .patientId(20002L).unbound(true).unboundAt(OffsetDateTime.now()).build()));

        mockMvc.perform(delete("/c/v1/family-members/20002")
                        .header("X-Idempotency-Key", "key-003"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("家庭成员已解绑"))
                .andExpect(jsonPath("$.data.patientId").value(20002L))
                .andExpect(jsonPath("$.data.unbound").value(true));
    }
}
