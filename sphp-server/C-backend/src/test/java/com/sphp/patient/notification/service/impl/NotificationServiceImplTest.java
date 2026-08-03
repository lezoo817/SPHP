package com.sphp.patient.notification.service.impl;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.notification.mapper.NotificationMapper;
import com.sphp.patient.notification.mapper.NotificationRecord;
import com.sphp.patient.notification.vo.NotificationPageVO;
import com.sphp.patient.notification.vo.NotificationReadVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 站内通知服务查询单元测试。
 */
class NotificationServiceImplTest {

    /**
     * 每个测试结束后清理当前用户上下文。
     */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
    }

    /**
     * 验证通知列表按当前用户和可选患者范围查询。
     */
    @Test
    void listNotificationsReturnsPageForAccessiblePatient() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        NotificationServiceImpl service = new NotificationServiceImpl(mapper);
        CUserContext.set(new CUserPrincipal(10001L, "zhangsan", OffsetDateTime.now().plusHours(1), "session"));
        when(mapper.existsActivePatient(20001L)).thenReturn(true);
        when(mapper.hasActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(mapper.selectNotifications(10001L, 20001L, false, 20, 0)).thenReturn(List.of(record()));
        when(mapper.countNotifications(10001L, 20001L, false)).thenReturn(1L);

        NotificationPageVO result = service.listNotifications(20001L, false, null, null);

        assertEquals(1, result.getTotal());
        assertEquals("APPOINTMENT", result.getRecords().getFirst().getType());
        assertEquals(false, result.getRecords().getFirst().isRead());
    }

    /**
     * 验证指定患者未绑定当前账号时拒绝查询。
     */
    @Test
    void listNotificationsRejectsUnauthorizedPatient() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        NotificationServiceImpl service = new NotificationServiceImpl(mapper);
        CUserContext.set(new CUserPrincipal(10001L, "zhangsan", OffsetDateTime.now().plusHours(1), "session"));
        when(mapper.existsActivePatient(20002L)).thenReturn(true);
        when(mapper.hasActivePatientRelation(10001L, 20002L)).thenReturn(false);

        CAuthException exception = assertThrows(CAuthException.class,
                () -> service.listNotifications(20002L, null, 1, 20));

        assertEquals("A0301", exception.getCode());
        assertEquals(403, exception.getHttpStatus().value());
    }

    /**
     * 验证页大小超出上限时拒绝查询。
     */
    @Test
    void listNotificationsRejectsOversizedPage() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        NotificationServiceImpl service = new NotificationServiceImpl(mapper);
        CUserContext.set(new CUserPrincipal(10001L, "zhangsan", OffsetDateTime.now().plusHours(1), "session"));

        CAuthException exception = assertThrows(CAuthException.class,
                () -> service.listNotifications(null, null, 1, 101));

        assertEquals("A0420", exception.getCode());
    }

    /**
     * 验证未读通知首次标记已读后返回当前写入时间。
     */
    @Test
    void markNotificationReadSetsFirstReadTime() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        NotificationServiceImpl service = new NotificationServiceImpl(mapper);
        CUserContext.set(new CUserPrincipal(10001L, "zhangsan", OffsetDateTime.now().plusHours(1), "session"));
        NotificationRecord notification = record();
        notification.setReadAt(null);
        when(mapper.selectNotification(21001L)).thenReturn(notification);
        when(mapper.markNotificationRead(org.mockito.ArgumentMatchers.eq(21001L), org.mockito.ArgumentMatchers.eq(10001L), any()))
                .thenReturn(1);

        NotificationReadVO result = service.markNotificationRead(21001L);

        assertEquals(21001L, result.getId());
        assertEquals(true, result.isRead());
    }

    /**
     * 验证已读通知重复请求保留原始已读时间。
     */
    @Test
    void markNotificationReadKeepsExistingReadTime() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        NotificationServiceImpl service = new NotificationServiceImpl(mapper);
        CUserContext.set(new CUserPrincipal(10001L, "zhangsan", OffsetDateTime.now().plusHours(1), "session"));
        OffsetDateTime originalReadAt = OffsetDateTime.now().minusMinutes(1);
        NotificationRecord notification = record();
        notification.setReadAt(originalReadAt);
        when(mapper.selectNotification(21001L)).thenReturn(notification);

        NotificationReadVO result = service.markNotificationRead(21001L);

        assertEquals(originalReadAt, result.getReadAt());
    }

    /**
     * 验证其他账号通知不能标记为已读。
     */
    @Test
    void markNotificationReadRejectsOtherUsersNotification() {
        NotificationMapper mapper = mock(NotificationMapper.class);
        NotificationServiceImpl service = new NotificationServiceImpl(mapper);
        CUserContext.set(new CUserPrincipal(10001L, "zhangsan", OffsetDateTime.now().plusHours(1), "session"));
        NotificationRecord notification = record();
        notification.setUserId(10002L);
        when(mapper.selectNotification(21001L)).thenReturn(notification);

        CAuthException exception = assertThrows(CAuthException.class, () -> service.markNotificationRead(21001L));

        assertEquals("A0301", exception.getCode());
    }

    /**
     * 创建通知查询投影。
     *
     * @return 通知查询投影
     */
    private NotificationRecord record() {
        NotificationRecord record = new NotificationRecord();
        record.setId(21001L);
        record.setUserId(10001L);
        record.setPatientId(20001L);
        record.setPatientName("张三");
        record.setType("APPOINTMENT");
        record.setTitle("挂号订单待支付");
        record.setContent("请在规定时间内完成支付。");
        record.setCreatedAt(OffsetDateTime.now());
        return record;
    }
}
