package com.sphp.patient.health.service.impl;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.common.enums.ProposalMedicationActionEnum;
import com.sphp.patient.health.dto.ProposalFollowUpConfirmRequest;
import com.sphp.patient.health.dto.ProposalMedicationUpdateRequest;
import com.sphp.patient.health.dto.ProposalReportCreateRequest;
import com.sphp.patient.health.entity.ProposalPatientReport;
import com.sphp.patient.health.entity.ProposalReportIndicator;
import com.sphp.patient.health.mapper.ConsultationReportRecord;
import com.sphp.patient.health.mapper.ConsultationReportInterpretationRecord;
import com.sphp.patient.health.mapper.ConsultationReportListRecord;
import com.sphp.patient.health.mapper.FollowUpRecord;
import com.sphp.patient.health.mapper.HealthPatientMapper;
import com.sphp.patient.health.mapper.MedicationRecord;
import com.sphp.patient.health.mapper.ProposalDataMapper;
import com.sphp.patient.health.mapper.ProposalReportIndicatorMapper;
import com.sphp.patient.health.mapper.ProposalReportMapper;
import com.sphp.patient.health.vo.ProposalFollowUpVO;
import com.sphp.patient.health.vo.ProposalMedicationPlanVO;
import com.sphp.patient.health.vo.ProposalReportCreateVO;
import com.sphp.patient.health.vo.ProposalReportInterpretationVO;
import com.sphp.patient.health.vo.ProposalReportPageVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 健康报告、用药与随访服务的业务规则测试。
 */
class ProposalServiceImplTest {

    /**
     * 每个测试结束后清理当前用户，避免 ThreadLocal 污染。
     */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
    }

    /**
     * 验证报告创建会在同一患者下持久化报告和全部指标。
     */
    @Test
    void proposalCreateReportPersistsReportAndIndicators() {
        HealthPatientMapper patientMapper = authorizedPatientMapper();
        ProposalReportMapper reportMapper = mock(ProposalReportMapper.class);
        ProposalReportIndicatorMapper indicatorMapper = mock(ProposalReportIndicatorMapper.class);
        doAnswer(invocation -> {
            ProposalPatientReport report = invocation.getArgument(0);
            report.setId(7001L);
            return 1;
        }).when(reportMapper).insert(any(ProposalPatientReport.class));
        when(indicatorMapper.insert(any(ProposalReportIndicator.class))).thenReturn(1);
        ProposalServiceImpl service = service(patientMapper, reportMapper, indicatorMapper, mock(ProposalDataMapper.class));
        setUserContext();

        ProposalReportCreateVO result = service.proposalCreateReport(reportRequest());

        assertEquals(7001L, result.getReportId());
        assertEquals("RECORDED", result.getStatus());
        ArgumentCaptor<ProposalReportIndicator> indicatorCaptor = ArgumentCaptor.forClass(ProposalReportIndicator.class);
        verify(indicatorMapper).insert(indicatorCaptor.capture());
        assertEquals(7001L, indicatorCaptor.getValue().getReportId());
        assertEquals("白细胞", indicatorCaptor.getValue().getName());
    }

    /**
     * 验证报告列表使用当前可访问患者范围，并映射医生病历摘要字段。
     */
    @Test
    void proposalListReportsMapsCompletedDoctorNotes() {
        ProposalDataMapper dataMapper = mock(ProposalDataMapper.class);
        OffsetDateTime completedAt = OffsetDateTime.parse("2026-08-02T09:30:00+08:00");
        OffsetDateTime updatedAt = OffsetDateTime.parse("2026-08-02T09:35:00+08:00");
        when(dataMapper.proposalSelectConsultationReports(20001L, 20, 0L)).thenReturn(List.of(
                new ConsultationReportListRecord(7001L, 20001L, "张医生", "呼吸内科", completedAt, updatedAt)));
        when(dataMapper.proposalCountConsultationReports(20001L)).thenReturn(1L);
        ProposalServiceImpl service = service(authorizedPatientMapper(), mock(ProposalReportMapper.class),
                mock(ProposalReportIndicatorMapper.class), dataMapper);
        setUserContext();

        ProposalReportPageVO result = service.proposalListReports(null, null, null);

        assertEquals(1, result.getPageNo());
        assertEquals(20, result.getPageSize());
        assertEquals(1L, result.getTotal());
        assertEquals("张医生", result.getRecords().getFirst().getDoctorName());
        assertEquals("呼吸内科", result.getRecords().getFirst().getDepartmentName());
        assertEquals(completedAt, result.getRecords().getFirst().getCompletedAt());
        verify(dataMapper).proposalSelectConsultationReports(20001L, 20, 0L);
    }

    /**
     * 验证报告资源归属当前账号以外时拒绝访问。
     */
    @Test
    void proposalGetReportRejectsForeignPatientResource() {
        HealthPatientMapper patientMapper = mock(HealthPatientMapper.class);
        ProposalDataMapper dataMapper = mock(ProposalDataMapper.class);
        when(dataMapper.proposalSelectConsultationReport(7001L)).thenReturn(new ConsultationReportRecord(
                7001L, 20002L, 30001L, "张医生", "呼吸内科", "医生病历正文",
                OffsetDateTime.parse("2026-08-02T09:00:00+08:00"),
                OffsetDateTime.parse("2026-08-02T09:30:00+08:00"),
                OffsetDateTime.parse("2026-08-02T09:35:00+08:00")));
        when(patientMapper.existsActivePatient(20002L)).thenReturn(true);
        when(patientMapper.hasActivePatientRelation(10001L, 20002L)).thenReturn(false);
        ProposalServiceImpl service = service(patientMapper, mock(ProposalReportMapper.class),
                mock(ProposalReportIndicatorMapper.class), dataMapper);
        setUserContext();

        CAuthException exception = assertThrows(CAuthException.class, () -> service.proposalGetReport(7001L));

        assertEquals("A0301", exception.getCode());
    }

    /**
     * 验证仅 READY 状态且存在内容的报告可读取解读结果。
     */
    @Test
    void proposalGetReportInterpretationReadsReadyContent() {
        HealthPatientMapper patientMapper = authorizedPatientMapper();
        ProposalDataMapper dataMapper = mock(ProposalDataMapper.class);
        when(dataMapper.proposalSelectConsultationReport(7001L)).thenReturn(consultationReport(20001L));
        when(dataMapper.proposalSelectReadyConsultationReportInterpretation(7001L)).thenReturn(
                new ConsultationReportInterpretationRecord("建议规律复诊", "仅供参考",
                        OffsetDateTime.parse("2026-08-02T10:00:00+08:00")));
        ProposalServiceImpl service = service(patientMapper, mock(ProposalReportMapper.class),
                mock(ProposalReportIndicatorMapper.class), dataMapper);
        setUserContext();

        ProposalReportInterpretationVO result = service.proposalGetReportInterpretation(7001L);

        assertEquals(7001L, result.getReportId());
        assertEquals("建议规律复诊", result.getContent());
        assertEquals("仅供参考", result.getDisclaimer());
    }

    /**
     * 验证 PENDING 报告解读返回状态冲突。
     */
    @Test
    void proposalGetReportInterpretationRejectsPendingStatus() {
        HealthPatientMapper patientMapper = authorizedPatientMapper();
        ProposalDataMapper dataMapper = mock(ProposalDataMapper.class);
        when(dataMapper.proposalSelectConsultationReport(7001L)).thenReturn(consultationReport(20001L));
        ProposalServiceImpl service = service(patientMapper, mock(ProposalReportMapper.class),
                mock(ProposalReportIndicatorMapper.class), dataMapper);
        setUserContext();

        CAuthException exception = assertThrows(CAuthException.class,
                () -> service.proposalGetReportInterpretation(7001L));

        assertEquals("B0202", exception.getCode());
    }

    /**
     * 验证暂停动作清空下一次用药提醒时间。
     */
    @Test
    void proposalUpdateMedicationPlanPausesActivePlan() {
        ProposalDataMapper dataMapper = medicationMapper("ACTIVE");
        ProposalServiceImpl service = service(authorizedPatientMapper(), mock(ProposalReportMapper.class),
                mock(ProposalReportIndicatorMapper.class), dataMapper);
        setUserContext();

        ProposalMedicationPlanVO result = service.proposalUpdateMedicationPlan(8001L, medicationRequest(
                ProposalMedicationActionEnum.PAUSE));

        assertEquals("PAUSED", result.getStatus());
        assertNull(result.getNextReminderAt());
        verify(dataMapper).proposalUpdateMedication(eq(8001L), eq(20001L), eq("PAUSED"), eq("ACTIVE"), isNull(), isNull(), any());
    }

    /**
     * 验证恢复动作只允许暂停计划，并重新设置提醒时间。
     */
    @Test
    void proposalUpdateMedicationPlanResumesPausedPlan() {
        ProposalDataMapper dataMapper = medicationMapper("PAUSED");
        ProposalServiceImpl service = service(authorizedPatientMapper(), mock(ProposalReportMapper.class),
                mock(ProposalReportIndicatorMapper.class), dataMapper);
        setUserContext();

        ProposalMedicationPlanVO result = service.proposalUpdateMedicationPlan(8001L, medicationRequest(
                ProposalMedicationActionEnum.RESUME));

        assertEquals("ACTIVE", result.getStatus());
        assertTrue(result.getNextReminderAt().isBefore(OffsetDateTime.now().plusSeconds(1)));
        verify(dataMapper).proposalUpdateMedication(eq(8001L), eq(20001L), eq("ACTIVE"), eq("PAUSED"), any(), isNull(), any());
    }

    /**
     * 验证完成动作终止计划并清空后续提醒。
     */
    @Test
    void proposalUpdateMedicationPlanCompletesPlan() {
        ProposalDataMapper dataMapper = medicationMapper("ACTIVE");
        ProposalServiceImpl service = service(authorizedPatientMapper(), mock(ProposalReportMapper.class),
                mock(ProposalReportIndicatorMapper.class), dataMapper);
        setUserContext();

        ProposalMedicationPlanVO result = service.proposalUpdateMedicationPlan(8001L, medicationRequest(
                ProposalMedicationActionEnum.COMPLETE));

        assertEquals("COMPLETED", result.getStatus());
        assertNull(result.getNextReminderAt());
        verify(dataMapper).proposalUpdateMedication(eq(8001L), eq(20001L), eq("COMPLETED"), eq("ACTIVE"), isNull(), any(), any());
    }

    /**
     * 验证条件更新未命中时按并发状态变化返回冲突。
     */
    @Test
    void proposalUpdateMedicationPlanRejectsConcurrentStateChange() {
        ProposalDataMapper dataMapper = medicationMapper("ACTIVE");
        when(dataMapper.proposalUpdateMedication(any(), any(), any(), any(), any(), any(), any())).thenReturn(0);
        ProposalServiceImpl service = service(authorizedPatientMapper(), mock(ProposalReportMapper.class),
                mock(ProposalReportIndicatorMapper.class), dataMapper);
        setUserContext();

        CAuthException exception = assertThrows(CAuthException.class,
                () -> service.proposalUpdateMedicationPlan(8001L, medicationRequest(ProposalMedicationActionEnum.PAUSE)));

        assertEquals("A0443", exception.getCode());
    }

    /**
     * 验证随访确认未提供提醒时间时使用计划到期时间。
     */
    @Test
    void proposalConfirmFollowUpUsesDueAtByDefault() {
        OffsetDateTime dueAt = OffsetDateTime.parse("2026-08-10T09:00:00+08:00");
        ProposalDataMapper dataMapper = mock(ProposalDataMapper.class);
        when(dataMapper.proposalSelectFollowUp(9001L)).thenReturn(new FollowUpRecord(9001L, 20001L,
                "复查", dueAt, "一周后复查", "PENDING_CONFIRM", null));
        when(dataMapper.proposalConfirmFollowUp(eq(9001L), eq(20001L), eq(dueAt), any())).thenReturn(1);
        ProposalServiceImpl service = service(authorizedPatientMapper(), mock(ProposalReportMapper.class),
                mock(ProposalReportIndicatorMapper.class), dataMapper);
        setUserContext();

        ProposalFollowUpVO result = service.proposalConfirmFollowUp(9001L, new ProposalFollowUpConfirmRequest());

        assertEquals("CONFIRMED", result.getStatus());
        assertEquals(dueAt, result.getRemindAt());
        verify(dataMapper).proposalConfirmFollowUp(eq(9001L), eq(20001L), eq(dueAt), any());
    }

    /**
     * 创建拥有当前患者有效关系的 Mapper 模拟对象。
     *
     * @return 已授权患者关系 Mapper
     */
    private HealthPatientMapper authorizedPatientMapper() {
        HealthPatientMapper patientMapper = mock(HealthPatientMapper.class);
        when(patientMapper.selectSelfPatientId(10001L)).thenReturn(20001L);
        when(patientMapper.existsActivePatient(20001L)).thenReturn(true);
        when(patientMapper.hasActivePatientRelation(10001L, 20001L)).thenReturn(true);
        return patientMapper;
    }

    /**
     * 创建指定状态的用药计划数据访问模拟对象。
     *
     * @param status 用药计划当前状态
     * @return 带有条件更新成功结果的 Mapper
     */
    private ProposalDataMapper medicationMapper(String status) {
        ProposalDataMapper dataMapper = mock(ProposalDataMapper.class);
        when(dataMapper.proposalSelectMedication(8001L)).thenReturn(new MedicationRecord(8001L, 20001L,
                "阿莫西林", "0.5g", "每日三次", OffsetDateTime.now().plusHours(1), status));
        when(dataMapper.proposalUpdateMedication(any(), any(), any(), any(), any(), any(), any())).thenReturn(1);
        return dataMapper;
    }

    /**
     * 创建报告录入请求测试数据。
     *
     * @return 合法的报告录入请求
     */
    private ProposalReportCreateRequest reportRequest() {
        ProposalReportCreateRequest request = new ProposalReportCreateRequest();
        request.setReportName("血常规");
        request.setReportDate(LocalDate.of(2026, 8, 2));
        ProposalReportCreateRequest.Indicator indicator = new ProposalReportCreateRequest.Indicator();
        indicator.setName("白细胞");
        indicator.setValue("5.2");
        request.setIndicators(List.of(indicator));
        return request;
    }

    /**
     * 创建用药计划状态变更请求。
     *
     * @param action 状态变更动作
     * @return 更新请求
     */
    private ProposalMedicationUpdateRequest medicationRequest(ProposalMedicationActionEnum action) {
        ProposalMedicationUpdateRequest request = new ProposalMedicationUpdateRequest();
        request.setAction(action);
        return request;
    }

    /**
     * 创建健康报告服务实例。
     *
     * @param patientMapper 患者归属 Mapper
     * @param reportMapper 报告 Mapper
     * @param indicatorMapper 指标 Mapper
     * @param dataMapper 复杂数据查询 Mapper
     * @return 健康报告服务
     */
    private ProposalServiceImpl service(HealthPatientMapper patientMapper, ProposalReportMapper reportMapper,
                                        ProposalReportIndicatorMapper indicatorMapper, ProposalDataMapper dataMapper) {
        return new ProposalServiceImpl(patientMapper, reportMapper, indicatorMapper, dataMapper);
    }

    /**
     * 创建可向当前患者展示的已完成医生病历。
     *
     * @param patientId 就诊人 ID
     * @return 医生病历报告投影
     */
    private ConsultationReportRecord consultationReport(Long patientId) {
        return new ConsultationReportRecord(7001L, patientId, 30001L, "张医生", "呼吸内科", "医生病历正文",
                OffsetDateTime.parse("2026-08-02T09:00:00+08:00"),
                OffsetDateTime.parse("2026-08-02T09:30:00+08:00"),
                OffsetDateTime.parse("2026-08-02T09:35:00+08:00"));
    }

    /**
     * 设置当前 C 端用户上下文。
     */
    private void setUserContext() {
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
    }
}
