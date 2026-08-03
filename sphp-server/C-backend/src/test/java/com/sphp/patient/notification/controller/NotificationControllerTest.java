package com.sphp.patient.notification.controller;

import com.sphp.patient.notification.handler.NotificationExceptionHandler;
import com.sphp.patient.notification.service.NotificationService;
import com.sphp.patient.notification.vo.NotificationPageVO;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 站内通知控制器查询接口测试。
 */
class NotificationControllerTest {

    /**
     * 验证通知列表返回统一分页响应和已读状态。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void listNotificationsReturnsExpectedPage() throws Exception {
        NotificationService notificationService = mock(NotificationService.class);
        when(notificationService.listNotifications(20001L, false, 1, 20)).thenReturn(NotificationPageVO.builder()
                .pageNo(1).pageSize(20).total(1)
                .records(List.of(NotificationPageVO.Item.builder().id(21001L).type("APPOINTMENT")
                        .patientId(20001L).patientName("张三").title("挂号订单待支付")
                        .content("请在规定时间内完成支付。 ").read(false).createdAt(OffsetDateTime.now()).build()))
                .build());
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
                .setControllerAdvice(new NotificationExceptionHandler()).build();

        mockMvc.perform(get("/c/v1/notifications").param("patientId", "20001")
                        .param("read", "false").param("pageNo", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("00000"))
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].type").value("APPOINTMENT"))
                .andExpect(jsonPath("$.data.records[0].read").value(false));
    }

    /**
     * 验证负数就诊人 ID 被参数校验拒绝。
     *
     * @throws Exception MockMvc 调用失败时抛出
     */
    @Test
    void listNotificationsRejectsInvalidPatientId() throws Exception {
        NotificationService notificationService = mock(NotificationService.class);
        MockMvc mockMvc = MockMvcBuilders.standaloneSetup(new NotificationController(notificationService))
                .setControllerAdvice(new NotificationExceptionHandler()).build();

        mockMvc.perform(get("/c/v1/notifications").param("patientId", "0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("A0400"));
    }
}
