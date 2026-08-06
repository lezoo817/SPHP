package com.sphp.patient.registration.service.impl;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.registration.dto.RegisteringAppointmentCreateRequest;
import com.sphp.patient.registration.dto.RegisteringAppointmentCancelRequest;
import com.sphp.patient.registration.dto.RegisteringPaymentSimulateRequest;
import com.sphp.patient.registration.dto.RegisteringWaitlistCreateRequest;
import com.sphp.patient.registration.entity.RegisteringWaitlist;
import com.sphp.patient.registration.mapper.RegisteringAppointmentRecord;
import com.sphp.patient.registration.mapper.RegisteringDataMapper;
import com.sphp.patient.registration.mapper.RegisteringAppointmentMapper;
import com.sphp.patient.registration.mapper.RegisteringPaymentOrderMapper;
import com.sphp.patient.registration.mapper.RegisteringPaymentRecord;
import com.sphp.patient.registration.mapper.RegisteringWaitlistMapper;
import com.sphp.patient.registration.event.RegisteringAppointmentLockedEvent;
import com.sphp.patient.registration.support.RegisteringSlotLockService;
import com.sphp.patient.registration.support.RegisteringWaitlistPromotionService;
import com.sphp.patient.registration.config.RegistrationProperties;
import com.sphp.patient.notification.mq.producer.NotificationEventProducer;
import org.springframework.context.ApplicationEventPublisher;
import com.sphp.patient.registration.vo.RegisteringAppointmentCreateVO;
import com.sphp.patient.registration.vo.RegisteringDoctorBookingStatusVO;
import org.junit.jupiter.api.Test;
import org.mindrot.jbcrypt.BCrypt;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

/**
 * C端挂号订单与支付服务单元测试。
 */
class RegisteringServiceImplTest {

    /**
     * 验证预约状态查询按当前账号和医生 ID 读取五天冷却期历史。
     */
    @Test
    void registeringGetDoctorBookingStatusUsesCurrentUserAndDoctorId() {
        RegisteringDataMapper dataMapper = mock(RegisteringDataMapper.class);
        RegisteringServiceImpl service = new RegisteringServiceImpl(dataMapper,
                mock(RegisteringAppointmentMapper.class), mock(RegisteringPaymentOrderMapper.class),
                mock(RegisteringSlotLockService.class), mock(RegisteringWaitlistPromotionService.class),
                mock(RegisteringWaitlistMapper.class), registrationProperties(), mock(ApplicationEventPublisher.class),
                mock(NotificationEventProducer.class));
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        when(dataMapper.existsRegisteringDoctorAppointmentWithinCooldown(eq(10001L), eq(401L), any(OffsetDateTime.class))).thenReturn(true);

        try {
            RegisteringDoctorBookingStatusVO result = service.registeringGetDoctorBookingStatus(401L);

            assertEquals(401L, result.getDoctorId());
            assertEquals(true, result.isBooked());
            verify(dataMapper).existsRegisteringDoctorAppointmentWithinCooldown(eq(10001L), eq(401L), any(OffsetDateTime.class));
        } finally {
            CUserContext.clear();
        }
    }

    /**
     * 验证可访问就诊人的可用时段可锁定，并创建未支付订单和待支付支付单。
     */
    @Test
    void registeringCreateAppointmentLocksSnapshotAndCreatesPayment() {
        RegisteringDataMapper dataMapper = mock(RegisteringDataMapper.class);
        RegisteringAppointmentMapper appointmentMapper = mock(RegisteringAppointmentMapper.class);
        RegisteringPaymentOrderMapper paymentMapper = mock(RegisteringPaymentOrderMapper.class);
        RegisteringWaitlistMapper waitlistMapper = mock(RegisteringWaitlistMapper.class);
        RegisteringSlotLockService slotLockService = mock(RegisteringSlotLockService.class);
        RegisteringWaitlistPromotionService waitlistPromotionService = mock(RegisteringWaitlistPromotionService.class);
        RegistrationProperties properties = new RegistrationProperties();
        properties.setPaymentTimeout(900);
        RegisteringServiceImpl service = new RegisteringServiceImpl(dataMapper, appointmentMapper, paymentMapper,
                slotLockService, waitlistPromotionService, waitlistMapper, properties, mock(ApplicationEventPublisher.class),
                mock(NotificationEventProducer.class));
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        when(dataMapper.existsRegisteringActivePatient(20001L)).thenReturn(true);
        when(dataMapper.hasActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(dataMapper.selectRegisteringSlotLockInfo(101L, 501L)).thenReturn(
                new com.sphp.patient.registration.mapper.RegisteringSlotLockRecord(
                        501L, 301L, 401L, 5000, LocalDate.now().plusDays(1),
                        LocalTime.of(9, 0), LocalTime.of(9, 30), "PUBLISHED", "ENABLED"));
        when(dataMapper.registeringLockActiveUser(10001L)).thenReturn(10001L);
        when(dataMapper.existsRegisteringDoctorAppointmentWithinCooldown(eq(10001L), eq(401L), any(OffsetDateTime.class))).thenReturn(false);
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
            verify(waitlistPromotionService).registeringFulfillNotifiedWaitlist(eq(10001L), eq(20001L), eq(501L), any());
        } finally {
            CUserContext.clear();
        }
    }

    /**
     * 验证已经开始但尚未结束的时段仍可锁号，且支付截止时间不超过时段结束。
     */
    @Test
    void registeringCreateAppointmentAllowsOngoingSlotAndCapsPaymentAtSlotEnd() {
        RegisteringDataMapper dataMapper = mock(RegisteringDataMapper.class);
        RegisteringAppointmentMapper appointmentMapper = mock(RegisteringAppointmentMapper.class);
        RegisteringPaymentOrderMapper paymentMapper = mock(RegisteringPaymentOrderMapper.class);
        RegisteringSlotLockService slotLockService = mock(RegisteringSlotLockService.class);
        ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
        OffsetDateTime slotEndAt = OffsetDateTime.now(com.sphp.patient.common.constant.RegistrationConstant.BUSINESS_ZONE_ID)
                .plusMinutes(5).withSecond(0).withNano(0);
        when(dataMapper.existsRegisteringActivePatient(20001L)).thenReturn(true);
        when(dataMapper.hasActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(dataMapper.selectRegisteringSlotLockInfo(101L, 501L)).thenReturn(
                new com.sphp.patient.registration.mapper.RegisteringSlotLockRecord(
                        501L, 301L, 401L, 5000, slotEndAt.toLocalDate(),
                        slotEndAt.toLocalTime().minusMinutes(10), slotEndAt.toLocalTime(), "PUBLISHED", "ENABLED"));
        when(dataMapper.registeringLockActiveUser(10001L)).thenReturn(10001L);
        when(dataMapper.existsRegisteringDoctorAppointmentWithinCooldown(eq(10001L), eq(401L), any(OffsetDateTime.class))).thenReturn(false);
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
        RegisteringServiceImpl service = new RegisteringServiceImpl(dataMapper, appointmentMapper, paymentMapper,
                slotLockService, mock(RegisteringWaitlistPromotionService.class), mock(RegisteringWaitlistMapper.class),
                registrationProperties(), eventPublisher, mock(NotificationEventProducer.class));
        RegisteringAppointmentCreateRequest request = new RegisteringAppointmentCreateRequest();
        request.setPatientId(20001L);
        request.setHospitalId(101L);
        request.setSlotId(501L);
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));

        try {
            service.registeringCreateAppointment(request);

            org.mockito.ArgumentCaptor<com.sphp.patient.registration.entity.RegisteringAppointment> appointmentCaptor =
                    org.mockito.ArgumentCaptor.forClass(com.sphp.patient.registration.entity.RegisteringAppointment.class);
            org.mockito.ArgumentCaptor<RegisteringAppointmentLockedEvent> eventCaptor =
                    org.mockito.ArgumentCaptor.forClass(RegisteringAppointmentLockedEvent.class);
            verify(appointmentMapper).insert(appointmentCaptor.capture());
            verify(eventPublisher).publishEvent(eventCaptor.capture());
            assertEquals(slotEndAt.toInstant(), appointmentCaptor.getValue().getExpireAt().toInstant());
            assertEquals(slotEndAt.toInstant(), eventCaptor.getValue().expireAt().toInstant());
        } finally {
            CUserContext.clear();
        }
    }

    /**
     * 验证已超过时段结束时间的支付会取消未支付订单并释放已锁定号源。
     */
    @Test
    void registeringSimulatePaymentExpiresOrderWhenSlotEndDeadlineHasPassed() {
        RegisteringDataMapper dataMapper = mock(RegisteringDataMapper.class);
        RegisteringWaitlistPromotionService promotionService = mock(RegisteringWaitlistPromotionService.class);
        RegisteringPaymentRecord payment = new RegisteringPaymentRecord(8001L, 7001L, 20001L, 401L,
                10001L, 9001L, 501L, 5000, "PENDING", "UNPAID", OffsetDateTime.now().minusSeconds(1),
                null, BCrypt.hashpw("Password123", BCrypt.gensalt()));
        when(dataMapper.selectRegisteringPayment(8001L)).thenReturn(payment);
        when(dataMapper.existsRegisteringActivePatient(20001L)).thenReturn(true);
        when(dataMapper.hasActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(dataMapper.registeringCancelUnpaidAppointment(eq(7001L), any())).thenReturn(1);
        when(dataMapper.registeringReleaseLockedSnapshot(eq(9001L), any())).thenReturn(1);
        RegisteringServiceImpl service = service(dataMapper, mock(RegisteringWaitlistMapper.class),
                mock(NotificationEventProducer.class), promotionService);
        RegisteringPaymentSimulateRequest request = new RegisteringPaymentSimulateRequest();
        request.setLoginPassword("Password123");
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));

        try {
            CAuthException exception = assertThrows(CAuthException.class,
                    () -> service.registeringSimulatePayment(8001L, request));

            assertEquals("A0441", exception.getCode());
            verify(dataMapper).registeringCancelUnpaidAppointment(eq(7001L), any());
            verify(dataMapper).registeringClosePendingPayment(eq(7001L), any());
            verify(dataMapper).registeringReleaseLockedSnapshot(eq(9001L), any());
            verify(promotionService).registeringPromoteAfterSlotReleased(501L);
        } finally {
            CUserContext.clear();
        }
    }

    /**
     * 验证同一登录账号在五天冷却期内预约过同一医生时，不能再次创建挂号订单。
     */
    @Test
    void registeringCreateAppointmentRejectsUserWhoAlreadyPaidSameDoctorBeforeLockingSlot() {
        RegisteringDataMapper dataMapper = mock(RegisteringDataMapper.class);
        RegisteringSlotLockService slotLockService = mock(RegisteringSlotLockService.class);
        when(dataMapper.existsRegisteringActivePatient(20002L)).thenReturn(true);
        when(dataMapper.hasActivePatientRelation(10001L, 20002L)).thenReturn(true);
        when(dataMapper.selectRegisteringSlotLockInfo(101L, 501L)).thenReturn(
                new com.sphp.patient.registration.mapper.RegisteringSlotLockRecord(
                        501L, 301L, 401L, 5000, LocalDate.now().plusDays(1),
                        LocalTime.of(9, 0), LocalTime.of(9, 30), "PUBLISHED", "ENABLED"));
        when(dataMapper.registeringLockActiveUser(10001L)).thenReturn(10001L);
        when(dataMapper.existsRegisteringDoctorAppointmentWithinCooldown(eq(10001L), eq(401L), any(OffsetDateTime.class))).thenReturn(true);
        RegisteringServiceImpl service = new RegisteringServiceImpl(dataMapper,
                mock(RegisteringAppointmentMapper.class), mock(RegisteringPaymentOrderMapper.class),
                slotLockService, mock(RegisteringWaitlistPromotionService.class), mock(RegisteringWaitlistMapper.class),
                registrationProperties(), mock(ApplicationEventPublisher.class), mock(NotificationEventProducer.class));
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        RegisteringAppointmentCreateRequest request = new RegisteringAppointmentCreateRequest();
        request.setPatientId(20002L);
        request.setHospitalId(101L);
        request.setSlotId(501L);

        try {
            CAuthException exception = assertThrows(CAuthException.class,
                    () -> service.registeringCreateAppointment(request));

            assertEquals("A0506", exception.getCode());
            verify(dataMapper).registeringLockActiveUser(10001L);
            verify(dataMapper).existsRegisteringDoctorAppointmentWithinCooldown(eq(10001L), eq(401L), any(OffsetDateTime.class));
            verify(slotLockService, never()).registeringLock(any(), any(Long.class), any());
        } finally {
            CUserContext.clear();
        }
    }

    /**
     * 验证同一账号的另一笔待支付订单不能绕过限约规则完成支付。
     */
    @Test
    void registeringSimulatePaymentRejectsSecondPaidAppointmentForSameDoctor() {
        RegisteringDataMapper dataMapper = mock(RegisteringDataMapper.class);
        RegisteringPaymentRecord payment = new RegisteringPaymentRecord(8002L, 7002L, 20002L, 401L,
                10001L, 9002L, 501L, 5000, "PENDING", "UNPAID", OffsetDateTime.now().plusMinutes(10),
                null, BCrypt.hashpw("Password123", BCrypt.gensalt()));
        when(dataMapper.selectRegisteringPayment(8002L)).thenReturn(payment);
        when(dataMapper.existsRegisteringActivePatient(20002L)).thenReturn(true);
        when(dataMapper.hasActivePatientRelation(10001L, 20002L)).thenReturn(true);
        when(dataMapper.registeringLockActiveUser(10001L)).thenReturn(10001L);
        when(dataMapper.existsRegisteringDoctorAppointmentWithinCooldown(eq(10001L), eq(401L), any(OffsetDateTime.class))).thenReturn(true);
        RegisteringServiceImpl service = service(dataMapper, mock(RegisteringWaitlistMapper.class),
                mock(NotificationEventProducer.class), mock(RegisteringWaitlistPromotionService.class));
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        RegisteringPaymentSimulateRequest request = new RegisteringPaymentSimulateRequest();
        request.setLoginPassword("Password123");

        try {
            CAuthException exception = assertThrows(CAuthException.class,
                    () -> service.registeringSimulatePayment(8002L, request));

            assertEquals("A0506", exception.getCode());
            verify(dataMapper).registeringLockActiveUser(10001L);
            verify(dataMapper).existsRegisteringDoctorAppointmentWithinCooldown(eq(10001L), eq(401L), any(OffsetDateTime.class));
            verify(dataMapper, never()).registeringMarkPaymentSuccess(any(), any());
            verify(dataMapper, never()).registeringMarkAppointmentPaid(any(), any());
            verify(dataMapper, never()).registeringMarkSnapshotSold(any(), any());
        } finally {
            CUserContext.clear();
        }
    }

    /**
     * 验证候补登记会持久化当前 C 端用户，供后续通知精确定位登记账号。
     */
    @Test
    void registeringCreateWaitlistPersistsRegistrantUser() {
        RegisteringDataMapper dataMapper = mock(RegisteringDataMapper.class);
        RegisteringWaitlistMapper waitlistMapper = mock(RegisteringWaitlistMapper.class);
        NotificationEventProducer notificationProducer = mock(NotificationEventProducer.class);
        when(dataMapper.existsRegisteringActivePatient(20001L)).thenReturn(true);
        when(dataMapper.hasActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(dataMapper.lockRegisteringWaitlistSlot(501L)).thenReturn(
                new com.sphp.patient.registration.mapper.RegisteringSlotLockRecord(
                        501L, 301L, 401L, 5000, LocalDate.now().plusDays(1),
                        LocalTime.of(9, 0), LocalTime.of(9, 30), "PUBLISHED", "ENABLED"));
        when(dataMapper.countRegisteringAvailableSnapshots(501L)).thenReturn(0L);
        when(dataMapper.existsRegisteringActiveWaitlist(20001L, 501L)).thenReturn(false);
        when(dataMapper.selectRegisteringNextQueueNo(501L)).thenReturn(1);
        when(waitlistMapper.insert(any(RegisteringWaitlist.class))).thenAnswer(invocation -> {
            invocation.<RegisteringWaitlist>getArgument(0).setId(6001L);
            return 1;
        });
        RegisteringServiceImpl service = service(dataMapper, waitlistMapper, notificationProducer,
                mock(RegisteringWaitlistPromotionService.class));
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        RegisteringWaitlistCreateRequest request = new RegisteringWaitlistCreateRequest();
        request.setPatientId(20001L);
        request.setSlotId(501L);

        try {
            service.registeringCreateWaitlist(request);
            org.mockito.ArgumentCaptor<RegisteringWaitlist> captor =
                    org.mockito.ArgumentCaptor.forClass(RegisteringWaitlist.class);
            verify(waitlistMapper).insert(captor.capture());
            assertEquals(10001L, captor.getValue().getUserId());
            assertEquals("WAITING", captor.getValue().getStatus());
        } finally {
            CUserContext.clear();
        }
    }

    /**
     * 验证主动取消真实释放号源后才触发候补晋级。
     */
    @Test
    void registeringCancelAppointmentPromotesWaitlistAfterRelease() {
        RegisteringDataMapper dataMapper = mock(RegisteringDataMapper.class);
        RegisteringWaitlistPromotionService promotionService = mock(RegisteringWaitlistPromotionService.class);
        when(dataMapper.selectRegisteringAppointment(7001L)).thenReturn(appointmentRecord());
        when(dataMapper.existsRegisteringActivePatient(20001L)).thenReturn(true);
        when(dataMapper.hasActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(dataMapper.registeringCancelUnpaidAppointment(eq(7001L), any())).thenReturn(1);
        when(dataMapper.registeringReleaseLockedSnapshot(eq(9001L), any())).thenReturn(1);
        RegisteringServiceImpl service = service(dataMapper, mock(RegisteringWaitlistMapper.class),
                mock(NotificationEventProducer.class), promotionService);
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));

        try {
            service.registeringCancelAppointment(7001L, null);
            verify(promotionService).registeringPromoteAfterSlotReleased(501L);
        } finally {
            CUserContext.clear();
        }
    }

    /**
     * 验证付款账号可在预约开始前使用登录密码取消已支付挂号。
     */
    @Test
    void registeringCancelPaidAppointmentReleasesSoldSnapshotAfterPasswordVerification() {
        RegisteringDataMapper dataMapper = mock(RegisteringDataMapper.class);
        RegisteringWaitlistPromotionService promotionService = mock(RegisteringWaitlistPromotionService.class);
        when(dataMapper.selectRegisteringAppointment(7001L)).thenReturn(paidAppointmentRecord());
        when(dataMapper.existsRegisteringActivePatient(20001L)).thenReturn(true);
        when(dataMapper.hasActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(dataMapper.selectRegisteringPayment(8001L)).thenReturn(paidPaymentRecord(10001L));
        when(dataMapper.registeringCancelPaidAppointment(eq(7001L), any())).thenReturn(1);
        when(dataMapper.registeringReleaseSoldSnapshot(eq(9001L), any())).thenReturn(1);
        RegisteringServiceImpl service = service(dataMapper, mock(RegisteringWaitlistMapper.class),
                mock(NotificationEventProducer.class), promotionService);
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        RegisteringAppointmentCancelRequest request = new RegisteringAppointmentCancelRequest();
        request.setLoginPassword("P@ssw0rd123");

        try {
            assertEquals("CANCELLED", service.registeringCancelAppointment(7001L, request).getStatus());
            verify(dataMapper).registeringCancelPaidAppointment(eq(7001L), any());
            verify(dataMapper).registeringReleaseSoldSnapshot(eq(9001L), any());
            verify(dataMapper, never()).registeringClosePendingPayment(eq(7001L), any());
            verify(promotionService).registeringPromoteAfterSlotReleased(501L);
        } finally {
            CUserContext.clear();
        }
    }

    /**
     * 验证已支付挂号缺少或输入错误密码时不会取消订单或释放号源。
     */
    @Test
    void registeringCancelPaidAppointmentRejectsMissingOrInvalidPassword() {
        RegisteringDataMapper dataMapper = mock(RegisteringDataMapper.class);
        when(dataMapper.selectRegisteringAppointment(7001L)).thenReturn(paidAppointmentRecord());
        when(dataMapper.existsRegisteringActivePatient(20001L)).thenReturn(true);
        when(dataMapper.hasActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(dataMapper.selectRegisteringPayment(8001L)).thenReturn(paidPaymentRecord(10001L));
        RegisteringServiceImpl service = service(dataMapper, mock(RegisteringWaitlistMapper.class),
                mock(NotificationEventProducer.class), mock(RegisteringWaitlistPromotionService.class));
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        RegisteringAppointmentCancelRequest invalidRequest = new RegisteringAppointmentCancelRequest();
        invalidRequest.setLoginPassword("wrong-password");

        try {
            assertThrows(CAuthException.class, () -> service.registeringCancelAppointment(7001L, null));
            assertThrows(CAuthException.class, () -> service.registeringCancelAppointment(7001L, invalidRequest));
            verify(dataMapper, never()).registeringCancelPaidAppointment(eq(7001L), any());
            verify(dataMapper, never()).registeringReleaseSoldSnapshot(eq(9001L), any());
        } finally {
            CUserContext.clear();
        }
    }

    /**
     * 验证已支付订单在数据库条件更新发现预约已开始时不能释放已售号源。
     */
    @Test
    void registeringCancelPaidAppointmentRejectsWhenAppointmentHasStarted() {
        RegisteringDataMapper dataMapper = mock(RegisteringDataMapper.class);
        when(dataMapper.selectRegisteringAppointment(7001L)).thenReturn(paidAppointmentRecord());
        when(dataMapper.existsRegisteringActivePatient(20001L)).thenReturn(true);
        when(dataMapper.hasActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(dataMapper.selectRegisteringPayment(8001L)).thenReturn(paidPaymentRecord(10001L));
        when(dataMapper.registeringCancelPaidAppointment(eq(7001L), any())).thenReturn(0);
        RegisteringServiceImpl service = service(dataMapper, mock(RegisteringWaitlistMapper.class),
                mock(NotificationEventProducer.class), mock(RegisteringWaitlistPromotionService.class));
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        RegisteringAppointmentCancelRequest request = new RegisteringAppointmentCancelRequest();
        request.setLoginPassword("P@ssw0rd123");

        try {
            assertThrows(CAuthException.class, () -> service.registeringCancelAppointment(7001L, request));
            verify(dataMapper, never()).registeringReleaseSoldSnapshot(eq(9001L), any());
        } finally {
            CUserContext.clear();
        }
    }

    /**
     * 验证绑定同一就诊人的其他账号不能使用自身密码取消付款账号的已支付挂号。
     */
    @Test
    void registeringCancelPaidAppointmentRejectsNonPayerUser() {
        RegisteringDataMapper dataMapper = mock(RegisteringDataMapper.class);
        when(dataMapper.selectRegisteringAppointment(7001L)).thenReturn(paidAppointmentRecord());
        when(dataMapper.existsRegisteringActivePatient(20001L)).thenReturn(true);
        when(dataMapper.hasActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(dataMapper.selectRegisteringPayment(8001L)).thenReturn(paidPaymentRecord(10002L));
        RegisteringServiceImpl service = service(dataMapper, mock(RegisteringWaitlistMapper.class),
                mock(NotificationEventProducer.class), mock(RegisteringWaitlistPromotionService.class));
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
        RegisteringAppointmentCancelRequest request = new RegisteringAppointmentCancelRequest();
        request.setLoginPassword("P@ssw0rd123");

        try {
            assertThrows(CAuthException.class, () -> service.registeringCancelAppointment(7001L, request));
            verify(dataMapper, never()).registeringCancelPaidAppointment(eq(7001L), any());
            verify(dataMapper, never()).registeringReleaseSoldSnapshot(eq(9001L), any());
        } finally {
            CUserContext.clear();
        }
    }

    /**
     * 创建当前账号可取消的已支付挂号订单投影。
     *
     * @return 已支付挂号订单
     */
    private RegisteringAppointmentRecord paidAppointmentRecord() {
        return new RegisteringAppointmentRecord(7001L, 20001L, 9001L, 501L, 401L, "张医生", "呼吸内科",
                "门诊楼三层", LocalDate.now().plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 30),
                "PAID", 5000, OffsetDateTime.now().plusMinutes(15), 8001L, "SUCCESS");
    }

    /**
     * 创建指定付款账号的成功支付单投影。
     *
     * @param payerUserId 付款 C 端账号 ID
     * @return 成功支付单
     */
    private RegisteringPaymentRecord paidPaymentRecord(Long payerUserId) {
        return new RegisteringPaymentRecord(8001L, 7001L, 20001L, 401L, payerUserId, 9001L, 501L,
                5000, "SUCCESS", "PAID", OffsetDateTime.now().plusMinutes(15), OffsetDateTime.now(),
                BCrypt.hashpw("P@ssw0rd123", BCrypt.gensalt()));
    }

    /**
     * 创建挂号服务测试实例。
     *
     * @param dataMapper 挂号数据访问接口
     * @param waitlistMapper 候补数据访问接口
     * @param notificationProducer 通知生产器
     * @param promotionService 候补晋级服务
     * @return 挂号服务实现
     */
    private RegisteringServiceImpl service(RegisteringDataMapper dataMapper, RegisteringWaitlistMapper waitlistMapper,
                                           NotificationEventProducer notificationProducer,
                                           RegisteringWaitlistPromotionService promotionService) {
        RegistrationProperties properties = registrationProperties();
        return new RegisteringServiceImpl(dataMapper, mock(RegisteringAppointmentMapper.class),
                mock(RegisteringPaymentOrderMapper.class), mock(RegisteringSlotLockService.class), promotionService,
                waitlistMapper, properties, mock(ApplicationEventPublisher.class), notificationProducer);
    }

    /**
     * 创建测试使用的挂号配置。
     *
     * @return 支付超时时间为十五分钟的配置
     */
    private RegistrationProperties registrationProperties() {
        RegistrationProperties properties = new RegistrationProperties();
        properties.setPaymentTimeout(900);
        return properties;
    }

    /**
     * 创建当前账号可访问的未支付挂号订单投影。
     *
     * @return 挂号订单投影
     */
    private RegisteringAppointmentRecord appointmentRecord() {
        return new RegisteringAppointmentRecord(7001L, 20001L, 9001L, 501L, 401L, "张医生", "呼吸内科",
                "门诊楼三层", LocalDate.now().plusDays(1), LocalTime.of(9, 0), LocalTime.of(9, 30),
                "UNPAID", 5000, OffsetDateTime.now().plusMinutes(15), 8001L, "PENDING");
    }
}
