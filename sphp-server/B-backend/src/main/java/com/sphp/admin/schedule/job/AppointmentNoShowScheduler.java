package com.sphp.admin.schedule.job;

import com.sphp.admin.schedule.mapper.AppointmentNoShowMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * NO_SHOW 自动标记调度器（B端）。
 *
 * <p>定期扫描"slot 已结束 + 超过宽限期（默认 1 小时）+ 无 COMPLETED/IN_PROGRESS/DRAFT
 * 就诊记录"的 PAID 预约，自动标 NO_SHOW，让：
 * <ul>
 *     <li>B 端"取消发布"前置校验（{@code countPaidFutureAppointments}）立即放行</li>
 *     <li>前端"剩余号源"统计（{@code aggregateByScheduleIds}）正确反映可约数</li>
 *     <li>C 端下次 LOCKED → 取消的 snapshot 状态机保持一致</li>
 * </ul>
 * 配合 {@code ScheduleServiceImpl.forceRelease}（LOCKED → AVAILABLE）形成完整闭环。
 * 与 {@code ScheduleMapper.countPaidFutureAppointments} 的有效状态推导互为兜底：
 * 即使本调度器短暂漏跑，unpublish 校验的 SQL 升级也能正确放行。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class AppointmentNoShowScheduler {

    /**
     * slot 结束后的宽限期（分钟），仅当 now - end_time >= 该值时调度器才标记 NO_SHOW。
     * 业务含义：给迟到患者 1 小时缓冲期，避免医生仍在就诊时被误标。
     */
    private static final int DEFAULT_GRACE_MINUTES = 60;

    /** 默认扫描间隔（毫秒），对应 application.yml 中 sphp.schedule.no-show-scan-interval-millis 缺省值。 */
    private static final String DEFAULT_INTERVAL_MILLIS = "30000";

    private final AppointmentNoShowMapper appointmentNoShowMapper;

    /**
     * 定期标记过期 PAID 为 NO_SHOW。
     *
     * <p>fixedDelay 模式：上一次执行结束后等待指定毫秒再执行下一次，避免与长事务重叠。
     * initialDelay 让应用启动后稍等片刻再跑第一轮，避免与其他初始化任务争资源。
     */
    @Scheduled(
            fixedDelayString = "${sphp.schedule.no-show-scan-interval-millis:" + DEFAULT_INTERVAL_MILLIS + "}",
            initialDelayString = "${sphp.schedule.no-show-initial-delay-millis:" + DEFAULT_INTERVAL_MILLIS + "}")
    public void markOverduePaidAsNoShow() {
        int updated = appointmentNoShowMapper.markOverduePaidAsNoShow(DEFAULT_GRACE_MINUTES);
        if (updated > 0) {
            log.info("NO_SHOW 自动标记完成，影响行数={}，宽限期={}分钟", updated, DEFAULT_GRACE_MINUTES);
        }
    }
}
