package com.sphp.admin.doctor.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sphp.admin.auth.entity.Doctor;
import com.sphp.admin.auth.mapper.DoctorMapper;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.DataScope;
import com.sphp.admin.doctor.dto.OnlineConsultationReplyRequest;
import com.sphp.admin.doctor.dto.OnlineConsultationReplyVO;
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
import com.sphp.shared.event.OnlineConsultationRepliedEvent;
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
     * 验证状态条件更新成功后仅写入一条医生回复并发布安全事件。
     */
    @Test
    void replyOnlineConsultCompletesOnceAndPublishesEvent() {
        Fixture fixture = fixture();
        when(fixture.consultRecordMapper.completeOnlineConsult(any(), any(), any())).thenReturn(1);
        doAnswer(invocation -> {
            ConsultationMessage message = invocation.getArgument(0);
            message.setId(12001L);
            return 1;
        }).when(fixture.messageMapper).insert(any(ConsultationMessage.class));

        OnlineConsultationReplyVO result = fixture.service.replyOnlineConsult(11001L, replyRequest());

        assertEquals("COMPLETED", result.getStatus());
        assertEquals(12001L, result.getMessageId());
        verify(fixture.messageMapper).insert(any(ConsultationMessage.class));
        verify(fixture.eventPublisher).publishEvent(any(OnlineConsultationRepliedEvent.class));
    }

    /**
     * 验证并发请求未抢到状态更新时不写入第二条医生回复。
     */
    @Test
    void replyOnlineConsultRejectsWhenConditionalUpdateLosesRace() {
        Fixture fixture = fixture();
        when(fixture.consultRecordMapper.completeOnlineConsult(any(), any(), any())).thenReturn(0);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fixture.service.replyOnlineConsult(11001L, replyRequest()));

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
        when(messageMapper.selectCount(any())).thenReturn(0L);
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
     * 创建合法医生回复请求。
     *
     * @return 回复请求
     */
    private OnlineConsultationReplyRequest replyRequest() {
        OnlineConsultationReplyRequest request = new OnlineConsultationReplyRequest();
        request.setContent("请按处方用药，症状加重时及时线下就诊");
        return request;
    }

    /** 在线问诊测试依赖集合。 */
    private record Fixture(DoctorConsultServiceImpl service,
                           ConsultRecordMapper consultRecordMapper,
                           BConsultationMessageMapper messageMapper,
                           ApplicationEventPublisher eventPublisher) {
    }
}
