package com.sphp.admin.doctor.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sphp.admin.auth.entity.Doctor;
import com.sphp.admin.auth.mapper.DoctorMapper;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.DataScope;
import com.sphp.admin.doctor.dto.OnlineConsultationMessageSendRequest;
import com.sphp.admin.doctor.dto.MessageVO;
import com.sphp.admin.doctor.entity.ConsultRecord;
import com.sphp.admin.doctor.entity.ConsultationMessage;
import com.sphp.admin.doctor.mapper.BAppointmentMapper;
import com.sphp.admin.doctor.mapper.BConsultationMessageMapper;
import com.sphp.admin.doctor.mapper.BPatientAllergyMapper;
import com.sphp.admin.doctor.mapper.BPatientMapper;
import com.sphp.admin.doctor.mapper.BPatientMedicalHistoryMapper;
import com.sphp.admin.doctor.mapper.ConsultRecordMapper;
import com.sphp.admin.prescription.mapper.PrescriptionItemMapper;
import com.sphp.admin.prescription.mapper.PrescriptionMapper;
import com.sphp.admin.prescription.service.PrescriptionService;
import com.sphp.shared.event.ConsultationMessageCreatedEvent;
import com.sphp.shared.exception.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * B端在线问诊服务单元测试。
 */
class DoctorConsultServiceImplTest {

    /**
     * 验证医生消息保存成功后发布不含正文的实时事件。
     */
    @Test
    void sendOnlineConsultationMessagePublishesEvent() {
        Fixture fixture = fixture();
        doAnswer(invocation -> {
            ConsultationMessage message = invocation.getArgument(0);
            message.setId(12001L);
            return 1;
        }).when(fixture.messageMapper).insert(any(ConsultationMessage.class));

        MessageVO result = fixture.service.sendOnlineConsultationMessage(11001L, messageRequest());

        assertEquals(12001L, result.getMessageId());
        verify(fixture.messageMapper).insert(any(ConsultationMessage.class));
        verify(fixture.eventPublisher).publishEvent(any(ConsultationMessageCreatedEvent.class));
    }

    /**
     * 验证已结束问诊不会再次写入医生消息。
     */
    @Test
    void sendOnlineConsultationMessageRejectsWhenCompleted() {
        Fixture fixture = fixture();
        ConsultRecord completed = new ConsultRecord();
        completed.setId(11001L);
        completed.setDoctorId(30001L);
        completed.setPatientId(20001L);
        completed.setStatus("COMPLETED");
        when(fixture.consultRecordMapper.lockOnlineConsult(11001L)).thenReturn(completed);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service.sendOnlineConsultationMessage(11001L, messageRequest()));

        assertEquals("3011", exception.getCode());
        verify(fixture.messageMapper, never()).insert(any(ConsultationMessage.class));
        verify(fixture.eventPublisher, never()).publishEvent(any());
    }

    /**
     * 创建绑定同一医生和医院的数据权限测试夹具。
     *
     * @return 在线问诊服务测试夹具
     */
    private Fixture fixture() {
        ConsultRecordMapper consultRecordMapper = mock(ConsultRecordMapper.class);
        BConsultationMessageMapper messageMapper = mock(BConsultationMessageMapper.class);
        DoctorMapper doctorMapper = mock(DoctorMapper.class);
        CurrentUserService currentUserService = mock(CurrentUserService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        ConsultRecord record = new ConsultRecord();
        record.setId(11001L);
        record.setDoctorId(30001L);
        record.setPatientId(20001L);
        record.setStatus("IN_PROGRESS");
        Doctor doctor = new Doctor();
        doctor.setId(30001L);
        doctor.setHospitalId(40001L);
        when(consultRecordMapper.selectById(11001L)).thenReturn(record);
        when(consultRecordMapper.lockOnlineConsult(11001L)).thenReturn(record);
        when(messageMapper.selectOne(any())).thenReturn(null);
        when(doctorMapper.selectById(30001L)).thenReturn(doctor);
        when(currentUserService.getCurrentDataScope())
                .thenReturn(new DataScope("DOCTOR", 40001L, 50001L, 30001L));
        DoctorConsultServiceImpl service = new DoctorConsultServiceImpl(
                consultRecordMapper,
                mock(BPatientMapper.class),
                mock(BPatientAllergyMapper.class),
                mock(BPatientMedicalHistoryMapper.class),
                messageMapper,
                mock(BAppointmentMapper.class),
                mock(PrescriptionMapper.class),
                mock(PrescriptionItemMapper.class),
                doctorMapper,
                currentUserService,
                new ObjectMapper(),
                mock(PrescriptionService.class),
                eventPublisher);
        return new Fixture(service, consultRecordMapper, messageMapper, eventPublisher);
    }

    /**
     * 创建合法医生消息请求。
     *
     * @return 回复请求
     */
    private OnlineConsultationMessageSendRequest messageRequest() {
        OnlineConsultationMessageSendRequest request = new OnlineConsultationMessageSendRequest();
        request.setContent("请按处方用药，症状加重时及时线下就诊");
        request.setClientMessageId("doctor-message-1");
        return request;
    }

    /** 在线问诊测试依赖集合。 */
    private record Fixture(DoctorConsultServiceImpl service,
                           ConsultRecordMapper consultRecordMapper,
                           BConsultationMessageMapper messageMapper,
                           ApplicationEventPublisher eventPublisher) {
    }
}
