package com.sphp.patient.registration.service.impl;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.registration.dto.RegisteringAppointmentCreateRequest;
import com.sphp.patient.registration.mapper.RegisteringDataMapper;
import com.sphp.patient.registration.mapper.RegisteringAppointmentMapper;
import com.sphp.patient.registration.mapper.RegisteringPaymentOrderMapper;
import com.sphp.patient.registration.support.RegisteringSlotLockService;
import com.sphp.patient.registration.config.RegistrationProperties;
import com.sphp.patient.registration.vo.RegisteringAppointmentCreateVO;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * C端挂号订单与支付服务单元测试。
 */
class RegisteringServiceImplTest {

    /**
     * 验证可访问就诊人的可用时段可锁定，并创建未支付订单和待支付支付单。
     */
    @Test
    void registeringCreateAppointmentLocksSnapshotAndCreatesPayment() {
        RegisteringDataMapper dataMapper = mock(RegisteringDataMapper.class);
        RegisteringAppointmentMapper appointmentMapper = mock(RegisteringAppointmentMapper.class);
        RegisteringPaymentOrderMapper paymentMapper = mock(RegisteringPaymentOrderMapper.class);
        RegisteringSlotLockService slotLockService = mock(RegisteringSlotLockService.class);
        RegistrationProperties properties = new RegistrationProperties();
        properties.setPaymentTimeout(900);
        RegisteringServiceImpl service = new RegisteringServiceImpl(dataMapper, appointmentMapper, paymentMapper,
                slotLockService, properties);
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        when(dataMapper.existsRegisteringActivePatient(20001L)).thenReturn(true);
        when(dataMapper.hasActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(dataMapper.selectRegisteringSlotLockInfo(101L, 501L)).thenReturn(
                new com.sphp.patient.registration.mapper.RegisteringSlotLockRecord(
                        501L, 301L, 401L, 5000, LocalDate.now().plusDays(1),
                        LocalTime.of(9, 0), LocalTime.of(9, 30), "PUBLISHED", "ENABLED"));
        when(dataMapper.countRegisteringAvailableSnapshots(501L)).thenReturn(1L);
        when(slotLockService.registeringLock(eq(501L), eq(1L), any())).thenReturn(true);
        when(dataMapper.registeringLockOneSnapshot(eq(501L), eq(20001L), any())).thenReturn(9001L);
        when(appointmentMapper.insert(any(com.sphp.patient.registration.entity.RegisteringAppointment.class))).thenAnswer(invocation -> {
            invocation.<com.sphp.patient.registration.entity.RegisteringAppointment>getArgument(0).setId(7001L);
            return 1;
        });
        when(paymentMapper.insert(any(com.sphp.patient.registration.entity.RegisteringPaymentOrder.class))).thenAnswer(invocation -> {
            invocation.<com.sphp.patient.registration.entity.RegisteringPaymentOrder>getArgument(0).setId(8001L);
            return 1;
        });

        RegisteringAppointmentCreateRequest request = new RegisteringAppointmentCreateRequest();
        request.setPatientId(20001L);
        request.setHospitalId(101L);
        request.setSlotId(501L);
        try {
            RegisteringAppointmentCreateVO result = service.registeringCreateAppointment(request);

            assertEquals(7001L, result.getAppointmentId());
            assertEquals(8001L, result.getPaymentId());
            assertEquals("UNPAID", result.getStatus());
            assertEquals(5000, result.getAmountCent());
        } finally {
            CUserContext.clear();
        }
    }
}
