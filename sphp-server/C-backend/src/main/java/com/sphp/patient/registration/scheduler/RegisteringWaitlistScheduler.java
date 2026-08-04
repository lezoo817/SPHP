package com.sphp.patient.registration.scheduler;

import com.sphp.patient.registration.support.RegisteringWaitlistPromotionService;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * C端挂号候补通知超时扫描任务。
 */
@Component
@RequiredArgsConstructor
public class RegisteringWaitlistScheduler {
    // 候补晋级与过期处理服务
    private final RegisteringWaitlistPromotionService waitlistPromotionService;

    /**
     * 定期结束过期候补，并在可预约余量存在时通知下一位候补。
     */
    @Scheduled(fixedDelayString = "${sphp.registration.waitlist-scan-interval-millis}")
    public void registeringScanExpiredWaitlists() {
        // 调度线程没有请求身份，处理逻辑仅依据候补记录中持久化的用户和患者归属。
        waitlistPromotionService.registeringExpireDueWaitlists();
    }
}
