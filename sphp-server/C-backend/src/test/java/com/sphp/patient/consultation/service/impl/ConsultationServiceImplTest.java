package com.sphp.patient.consultation.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.consultation.dto.PreConsultationSaveRequest;
import com.sphp.patient.consultation.entity.ConsultationRecord;
import com.sphp.patient.consultation.mapper.ConsultationAppointmentRecord;
import com.sphp.patient.consultation.mapper.ConsultationDataMapper;
import com.sphp.patient.consultation.mapper.ConsultationListRecord;
import com.sphp.patient.consultation.mapper.ConsultationDetailRecord;
import com.sphp.patient.consultation.mapper.ConsultationMessageRecord;
import com.sphp.patient.consultation.mapper.ConsultationMessageMapper;
import com.sphp.patient.consultation.mapper.ConsultationPrescriptionRecord;
import com.sphp.patient.consultation.mapper.ConsultationPrescriptionResourceRecord;
import com.sphp.patient.consultation.mapper.ConsultationPrescriptionDetailRecord;
import com.sphp.patient.consultation.mapper.ConsultationPrescriptionItemRecord;
import com.sphp.patient.consultation.vo.PreConsultationSaveVO;
import com.sphp.patient.consultation.vo.ConsultationPageVO;
import com.sphp.patient.consultation.vo.ConsultationDetailVO;
import com.sphp.patient.consultation.dto.ConsultationMessageSendRequest;
import com.sphp.patient.consultation.entity.ConsultationMessage;
import com.sphp.patient.consultation.event.ConsultationMessageSentEvent;
import com.sphp.patient.consultation.vo.ConsultationPrescriptionPageVO;
import com.sphp.patient.consultation.vo.ConsultationPrescriptionDetailVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C端问诊服务单元测试。
 */
class ConsultationServiceImplTest {

    /**
     * 每个测试结束后清理线程用户上下文。
     */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
    }

    /**
     * 验证已支付预约能够创建草稿，且草稿没有提交时间。
     */
    @Test
    void savePreConsultationCreatesDraftForPaidAppointment() {
        ConsultationDataMapper dataMapper = mock(ConsultationDataMapper.class);
        ConsultationServiceImpl service = newService(dataMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        PreConsultationSaveRequest request = request(false);
        when(dataMapper.selectConsultationSelfPatientId(10001L)).thenReturn(20001L);
        when(dataMapper.existsConsultationActivePatient(20001L)).thenReturn(true);
        when(dataMapper.hasConsultationActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(dataMapper.lockConsultationAppointment(7001L))
                .thenReturn(new ConsultationAppointmentRecord(7001L, 20001L, 30001L, "PAID"));
        when(dataMapper.selectConsultationByAppointmentForUpdate(7001L)).thenReturn(null);
        doAnswer(invocation -> {
            ConsultationRecord record = invocation.getArgument(0);
            record.setId(11001L);
            return 1;
        }).when(dataMapper).insertConsultationRecord(any());

        PreConsultationSaveVO result = service.savePreConsultation(request);

        assertEquals(11001L, result.getConsultationId());
        assertEquals("DRAFT", result.getStatus());
        assertNull(result.getSubmittedAt());
        verify(dataMapper).insertConsultationRecord(any(ConsultationRecord.class));
    }

    /**
     * 验证未支付预约不能保存预问诊。
     */
    @Test
    void savePreConsultationRejectsUnpaidAppointment() {
        ConsultationDataMapper dataMapper = mock(ConsultationDataMapper.class);
        ConsultationServiceImpl service = newService(dataMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        when(dataMapper.selectConsultationSelfPatientId(10001L)).thenReturn(20001L);
        when(dataMapper.existsConsultationActivePatient(20001L)).thenReturn(true);
        when(dataMapper.hasConsultationActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(dataMapper.lockConsultationAppointment(7001L))
                .thenReturn(new ConsultationAppointmentRecord(7001L, 20001L, 30001L, "UNPAID"));

        CAuthException exception = assertThrows(CAuthException.class,
                () -> service.savePreConsultation(request(true)));

        assertEquals("A0443", exception.getCode());
    }

    /**
     * 验证问诊列表按已授权就诊人查询并应用默认分页。
     */
    @Test
    void listConsultationsUsesAccessiblePatientAndDefaultPagination() {
        ConsultationDataMapper dataMapper = mock(ConsultationDataMapper.class);
        ConsultationServiceImpl service = newService(dataMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        when(dataMapper.selectConsultationSelfPatientId(10001L)).thenReturn(20001L);
        when(dataMapper.existsConsultationActivePatient(20001L)).thenReturn(true);
        when(dataMapper.hasConsultationActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(dataMapper.selectConsultationList(20001L, "PENDING", 20, 0))
                .thenReturn(List.of(new ConsultationListRecord(11001L, 7001L, "王医生", "PENDING", OffsetDateTime.now())));
        when(dataMapper.countConsultationList(20001L, "PENDING")).thenReturn(1L);

        ConsultationPageVO result = service.listConsultations(null, "PENDING", null, null);

        assertEquals(1L, result.getTotal());
        assertEquals(20, result.getPageSize());
        assertEquals(11001L, result.getRecords().getFirst().getId());
    }

    /**
     * 验证问诊详情按资源患者反查归属，并按时间返回消息。
     */
    @Test
    void getConsultationDetailChecksPatientOwnershipAndReturnsMessages() {
        ConsultationDataMapper dataMapper = mock(ConsultationDataMapper.class);
        ConsultationServiceImpl service = newService(dataMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        when(dataMapper.selectConsultationDetail(11001L)).thenReturn(new ConsultationDetailRecord(
                11001L, 20001L, "IN_PROGRESS", 30001L, "王医生", "主治医师", "咳嗽",
                "两日前开始", "[]", OffsetDateTime.now(), OffsetDateTime.now()));
        when(dataMapper.existsConsultationActivePatient(20001L)).thenReturn(true);
        when(dataMapper.hasConsultationActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(dataMapper.selectConsultationMessages(11001L)).thenReturn(List.of(
                new ConsultationMessageRecord(12001L, "DOCTOR", "体温最高多少？", OffsetDateTime.now())));
        when(dataMapper.selectConsultationApprovedPrescriptionIds(11001L)).thenReturn(List.of(13001L));

        ConsultationDetailVO result = service.getConsultationDetail(11001L);

        assertEquals("IN_PROGRESS", result.getStatus());
        assertEquals("王医生", result.getDoctor().getName());
        assertEquals("DOCTOR", result.getMessages().getFirst().getSenderType());
        assertEquals(13001L, result.getPrescriptionIds().getFirst());
    }

    /**
     * 验证进行中的问诊可发送患者消息，并发布不含内容的业务事件。
     */
    @Test
    void sendConsultationMessagePersistsMessageAndPublishesSafeEvent() {
        ConsultationDataMapper dataMapper = mock(ConsultationDataMapper.class);
        ConsultationMessageMapper messageMapper = mock(ConsultationMessageMapper.class);
        org.springframework.context.ApplicationEventPublisher eventPublisher = mock(org.springframework.context.ApplicationEventPublisher.class);
        ConsultationServiceImpl service = new ConsultationServiceImpl(dataMapper, messageMapper, new ObjectMapper(), eventPublisher);
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        when(dataMapper.lockConsultationDetail(11001L)).thenReturn(new ConsultationDetailRecord(
                11001L, 20001L, "IN_PROGRESS", 30001L, "王医生", "主治医师", "咳嗽",
                null, "[]", OffsetDateTime.now(), OffsetDateTime.now()));
        when(dataMapper.existsConsultationActivePatient(20001L)).thenReturn(true);
        when(dataMapper.hasConsultationActivePatientRelation(10001L, 20001L)).thenReturn(true);
        doAnswer(invocation -> {
            ConsultationMessage message = invocation.getArgument(0);
            message.setId(12001L);
            return 1;
        }).when(messageMapper).insert(any(ConsultationMessage.class));
        ConsultationMessageSendRequest request = new ConsultationMessageSendRequest();
        request.setContent("最高体温38.5度");

        com.sphp.patient.consultation.vo.ConsultationMessageSendVO result = service.sendConsultationMessage(11001L, request);

        assertEquals(12001L, result.getMessageId());
        assertEquals("PATIENT", result.getSenderType());
        verify(eventPublisher).publishEvent(org.mockito.ArgumentMatchers.<Object>argThat(event -> {
            if (!(event instanceof ConsultationMessageSentEvent sentEvent)) {
                return false;
            }
            return sentEvent.consultationId().equals(11001L)
                    && sentEvent.patientId().equals(20001L)
                    && sentEvent.userId().equals(10001L);
        }));
    }

    /**
     * 验证处方列表只查询已批准处方并使用患者归属范围。
     */
    @Test
    void listPrescriptionsUsesAccessiblePatientAndApprovedMapper() {
        ConsultationDataMapper dataMapper = mock(ConsultationDataMapper.class);
        ConsultationServiceImpl service = newService(dataMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        when(dataMapper.selectConsultationSelfPatientId(10001L)).thenReturn(20001L);
        when(dataMapper.existsConsultationActivePatient(20001L)).thenReturn(true);
        when(dataMapper.hasConsultationActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(dataMapper.selectApprovedPrescriptionList(20001L, 20, 0)).thenReturn(List.of(
                new ConsultationPrescriptionRecord(13001L, 11001L, "王医生", OffsetDateTime.now())));
        when(dataMapper.countApprovedPrescriptionList(20001L)).thenReturn(1L);

        ConsultationPrescriptionPageVO result = service.listPrescriptions(null, null, null);

        assertEquals(1L, result.getTotal());
        assertEquals("APPROVED", result.getRecords().getFirst().getStatus());
        assertEquals(13001L, result.getRecords().getFirst().getId());
    }

    /**
     * 验证处方详情先校验患者归属，再返回已批准处方药品明细。
     */
    @Test
    void getPrescriptionDetailReturnsApprovedItemsAfterOwnershipCheck() {
        ConsultationDataMapper dataMapper = mock(ConsultationDataMapper.class);
        ConsultationServiceImpl service = newService(dataMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        when(dataMapper.selectConsultationPrescriptionResource(13001L))
                .thenReturn(new ConsultationPrescriptionResourceRecord(13001L, 20001L, "APPROVED"));
        when(dataMapper.existsConsultationActivePatient(20001L)).thenReturn(true);
        when(dataMapper.hasConsultationActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(dataMapper.selectApprovedPrescriptionDetail(13001L))
                .thenReturn(new ConsultationPrescriptionDetailRecord(13001L, 30001L, "王医生", "主治医师"));
        when(dataMapper.selectConsultationPrescriptionItems(13001L)).thenReturn(List.of(
                new ConsultationPrescriptionItemRecord(14001L, "阿莫西林胶囊", "0.25g*24粒", "0.5g",
                        "每日3次", "口服", (short) 5)));

        ConsultationPrescriptionDetailVO result = service.getPrescriptionDetail(13001L);

        assertEquals("APPROVED", result.getStatus());
        assertEquals("王医生", result.getDoctorName());
        assertEquals("阿莫西林胶囊", result.getItems().getFirst().getDrugName());
    }

    /**
     * 创建不关心消息写入行为的问诊服务。
     *
     * @param dataMapper 问诊数据访问模拟对象
     * @return 问诊服务实现
     */
    private ConsultationServiceImpl newService(ConsultationDataMapper dataMapper) {
        return new ConsultationServiceImpl(dataMapper, mock(ConsultationMessageMapper.class), new ObjectMapper(),
                mock(org.springframework.context.ApplicationEventPublisher.class));
    }

    /**
     * 创建预问诊请求测试数据。
     *
     * @param submit 是否提交
     * @return 预问诊请求
     */
    private PreConsultationSaveRequest request(boolean submit) {
        PreConsultationSaveRequest request = new PreConsultationSaveRequest();
        request.setAppointmentId(7001L);
        request.setChiefComplaint("咳嗽发热三天");
        request.setSubmit(submit);
        return request;
    }
}
