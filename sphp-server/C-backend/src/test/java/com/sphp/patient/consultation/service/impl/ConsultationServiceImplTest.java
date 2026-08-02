package com.sphp.patient.consultation.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.consultation.dto.PreConsultationSaveRequest;
import com.sphp.patient.consultation.entity.ConsultationRecord;
import com.sphp.patient.consultation.mapper.ConsultationAppointmentRecord;
import com.sphp.patient.consultation.mapper.ConsultationDataMapper;
import com.sphp.patient.consultation.vo.PreConsultationSaveVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

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
        ConsultationServiceImpl service = new ConsultationServiceImpl(dataMapper, new ObjectMapper());
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
        ConsultationServiceImpl service = new ConsultationServiceImpl(dataMapper, new ObjectMapper());
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
