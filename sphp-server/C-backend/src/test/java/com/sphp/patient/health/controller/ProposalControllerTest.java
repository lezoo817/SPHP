package com.sphp.patient.health.controller;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.health.handler.ProposalExceptionHandler;
import com.sphp.patient.health.service.ProposalService;
import com.sphp.patient.health.vo.ProposalFollowUpVO;
import com.sphp.patient.health.vo.ProposalMedicationPlanVO;
import com.sphp.patient.health.vo.ProposalReportCreateVO;
import com.sphp.patient.health.vo.ProposalReportDetailVO;
import com.sphp.patient.health.vo.ProposalReportInterpretationVO;
import com.sphp.patient.health.vo.ProposalReportPageVO;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 健康报告、用药与随访接口的控制器测试。
 */
class ProposalControllerTest {

    private final ObjectMapper objectMapper = new ObjectMapper().findAndRegisterModules()
            .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);

    /**
     * 每个测试结束后清理线程用户上下文，避免影响其他接口测试。
     */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
    }

    /**
     * 验证录入报告路由经幂等服务返回创建结果。
     *
     * @throws Exception MockMvc 执行失败时抛出
     */
    @Test
    void proposalCreateReportReturnsRecordedResult() throws Exception {
        ProposalService service = mock(ProposalService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        setUserContext();
        when(idempotencyService.execute(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(new IdempotencyPayload<>("报告已录入",
                        ProposalReportCreateVO.builder().reportId(7001L).status("RECORDED").build()));

        newMockMvc(service, idempotencyService).perform(post("/c/v1/reports")
                        .header("X-Idempotency-Key", "report-create-001")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportName\":\"血常规\",\"reportDate\":\"2026-08-02\",\"indicators\":[{\"name\":\"白细胞\",\"value\":\"5.2\"}]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.reportId").value(7001L))
                .andExpect(jsonPath("$.data.status").value("RECORDED"));
    }

    /**
     * 验证报告列表路由返回分页数据。
     *
     * @throws Exception MockMvc 执行失败时抛出
     */
    @Test
    void proposalListReportsReturnsPage() throws Exception {
        ProposalService service = mock(ProposalService.class);
        when(service.proposalListReports(20001L, 1, 20)).thenReturn(ProposalReportPageVO.builder()
                .pageNo(1).pageSize(20).total(1)
                .records(List.of(ProposalReportPageVO.Item.builder().id(7001L)
                        .reportName("血常规").reportDate(LocalDate.of(2026, 8, 2)).indicatorCount(2).build()))
                .build());

        newMockMvc(service, mock(CIdempotencyService.class)).perform(get("/c/v1/reports")
                        .param("patientId", "20001").param("pageNo", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].indicatorCount").value(2));
    }

    /**
     * 验证报告详情路由返回指标列表。
     *
     * @throws Exception MockMvc 执行失败时抛出
     */
    @Test
    void proposalGetReportReturnsIndicators() throws Exception {
        ProposalService service = mock(ProposalService.class);
        when(service.proposalGetReport(7001L)).thenReturn(ProposalReportDetailVO.builder().id(7001L)
                .reportName("血常规").reportDate(LocalDate.of(2026, 8, 2))
                .indicators(List.of(ProposalReportDetailVO.Indicator.builder().name("白细胞")
                        .value("5.2").unit("10^9/L").referenceRange("3.5-9.5").build())).build());

        newMockMvc(service, mock(CIdempotencyService.class)).perform(get("/c/v1/reports/7001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.indicators[0].name").value("白细胞"));
    }

    /**
     * 验证报告解读路由返回已生成的解读内容。
     *
     * @throws Exception MockMvc 执行失败时抛出
     */
    @Test
    void proposalGetReportInterpretationReturnsReadyContent() throws Exception {
        ProposalService service = mock(ProposalService.class);
        ProposalReportInterpretationVO interpretation = new ProposalReportInterpretationVO();
        interpretation.setReportId(7001L);
        interpretation.setDisclaimer("仅供参考");
        when(service.proposalGetReportInterpretation(7001L)).thenReturn(interpretation);

        newMockMvc(service, mock(CIdempotencyService.class)).perform(get("/c/v1/reports/7001/interpretation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.reportId").value(7001L))
                .andExpect(jsonPath("$.data.disclaimer").value("仅供参考"));
    }

    /**
     * 验证用药计划列表路由支持状态筛选。
     *
     * @throws Exception MockMvc 执行失败时抛出
     */
    @Test
    void proposalListMedicationPlansReturnsPlans() throws Exception {
        ProposalService service = mock(ProposalService.class);
        when(service.proposalListMedicationPlans(20001L, "ACTIVE")).thenReturn(List.of(
                ProposalMedicationPlanVO.builder().id(8001L).drugName("阿莫西林")
                        .dosage("0.5g").frequency("每日三次").status("ACTIVE").build()));

        newMockMvc(service, mock(CIdempotencyService.class)).perform(get("/c/v1/medication-plans")
                        .param("patientId", "20001").param("status", "ACTIVE"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].drugName").value("阿莫西林"))
                .andExpect(jsonPath("$.data[0].status").value("ACTIVE"));
    }

    /**
     * 验证更新用药计划路由通过幂等服务返回状态转换结果。
     *
     * @throws Exception MockMvc 执行失败时抛出
     */
    @Test
    void proposalUpdateMedicationPlanReturnsUpdatedPlan() throws Exception {
        ProposalService service = mock(ProposalService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        setUserContext();
        when(idempotencyService.execute(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(new IdempotencyPayload<>("用药计划已更新", ProposalMedicationPlanVO.builder()
                        .id(8001L).drugName("阿莫西林").status("PAUSED").build()));

        newMockMvc(service, idempotencyService).perform(patch("/c/v1/medication-plans/8001")
                        .header("X-Idempotency-Key", "medication-update-001")
                        .contentType(MediaType.APPLICATION_JSON).content("{\"action\":\"PAUSE\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PAUSED"));
    }

    /**
     * 验证随访计划列表路由返回当前患者可访问的计划。
     *
     * @throws Exception MockMvc 执行失败时抛出
     */
    @Test
    void proposalListFollowUpsReturnsPlans() throws Exception {
        ProposalService service = mock(ProposalService.class);
        when(service.proposalListFollowUps(20001L, "PENDING_CONFIRM")).thenReturn(List.of(
                ProposalFollowUpVO.builder().id(9001L).type("复查").status("PENDING_CONFIRM")
                        .dueAt(OffsetDateTime.parse("2026-08-03T09:00:00+08:00")).build()));

        newMockMvc(service, mock(CIdempotencyService.class)).perform(get("/c/v1/follow-ups")
                        .param("patientId", "20001").param("status", "PENDING_CONFIRM"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].type").value("复查"));
    }

    /**
     * 验证确认随访路由返回确认后的提醒时间。
     *
     * @throws Exception MockMvc 执行失败时抛出
     */
    @Test
    void proposalConfirmFollowUpReturnsConfirmedPlan() throws Exception {
        ProposalService service = mock(ProposalService.class);
        CIdempotencyService idempotencyService = mock(CIdempotencyService.class);
        setUserContext();
        when(idempotencyService.execute(any(), anyString(), anyString(), any(), any(), any()))
                .thenReturn(new IdempotencyPayload<>("随访计划已确认", ProposalFollowUpVO.builder()
                        .id(9001L).type("复查").status("CONFIRMED")
                        .remindAt(OffsetDateTime.parse("2026-08-03T09:00:00+08:00")).build()));

        newMockMvc(service, idempotencyService).perform(post("/c/v1/follow-ups/9001/confirm")
                        .header("X-Idempotency-Key", "follow-up-confirm-001")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.data.remindAt").exists());
    }

    /**
     * 验证写接口缺少幂等键时返回参数错误。
     *
     * @throws Exception MockMvc 执行失败时抛出
     */
    @Test
    void proposalCreateReportRejectsMissingIdempotencyKey() throws Exception {
        newMockMvc(mock(ProposalService.class), mock(CIdempotencyService.class)).perform(post("/c/v1/reports")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportName\":\"血常规\",\"reportDate\":\"2026-08-02\",\"indicators\":[{\"name\":\"白细胞\",\"value\":\"5.2\"}]}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"));
    }

    /**
     * 验证业务状态冲突转换为 HTTP 409 与约定业务码。
     *
     * @throws Exception MockMvc 执行失败时抛出
     */
    @Test
    void proposalInterpretationConflictReturnsExpectedStatus() throws Exception {
        ProposalService service = mock(ProposalService.class);
        when(service.proposalGetReportInterpretation(7001L)).thenThrow(new CAuthException(
                ErrorCodeEnum.BUSINESS_STATUS_CONFLICT, HttpStatus.CONFLICT, "报告解读尚未生成"));

        newMockMvc(service, mock(CIdempotencyService.class)).perform(get("/c/v1/reports/7001/interpretation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("B0202"));
    }

    /**
     * 设置用于调用写接口的当前 C 端用户。
     */
    private void setUserContext() {
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
    }

    /**
     * 创建控制器独立测试环境。
     *
     * @param service 健康报告服务模拟对象
     * @param idempotencyService 幂等服务模拟对象
     * @return MockMvc 测试对象
     */
    private MockMvc newMockMvc(ProposalService service, CIdempotencyService idempotencyService) {
        return MockMvcBuilders.standaloneSetup(new ProposalController(service, idempotencyService))
                .setControllerAdvice(new ProposalExceptionHandler())
                .setMessageConverters(new MappingJackson2HttpMessageConverter(objectMapper))
                .build();
    }
}
