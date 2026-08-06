package com.sphp.admin.statistics.service.impl;

import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.statistics.mapper.StatisticsMapper;
import com.sphp.admin.statistics.service.StatisticsService;
import com.sphp.admin.statistics.vo.DailyStatVO;
import com.sphp.admin.statistics.vo.DepartmentStatVO;
import com.sphp.admin.statistics.vo.StatisticsOverviewVO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * 统计报表服务实现。
 *
 * <p>所有统计基于当前登录管理员所属医院（{@code hospital_id}）做数据隔离。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class StatisticsServiceImpl implements StatisticsService {

    private final CurrentUserService currentUserService;
    private final StatisticsMapper statisticsMapper;

    @Override
    public StatisticsOverviewVO overview(String startDate, String endDate) {
        LocalDate[] range = resolveDateRange(startDate, endDate);
        String sd = range[0].toString();
        String ed = range[1].toString();

        Long hospitalId = currentUserService.getCurrentHospitalId();

        long totalAppointments = statisticsMapper.countAppointments(hospitalId, sd, ed);
        long completedConsults = statisticsMapper.countCompletedConsults(hospitalId, sd, ed);
        long totalRevenueCent = statisticsMapper.sumRevenueCent(hospitalId, sd, ed);
        long totalPrescriptions = statisticsMapper.countPrescriptions(hospitalId, sd, ed);
        double avgWaitTime = statisticsMapper.avgWaitTimeMinutes(hospitalId, sd, ed);

        double completedRate = totalAppointments > 0
                ? (double) completedConsults / totalAppointments
                : 0.0;

        return StatisticsOverviewVO.builder()
                .totalAppointments(totalAppointments)
                .completedRate(completedRate)
                .totalRevenueCent(totalRevenueCent)
                .totalPrescriptions(totalPrescriptions)
                .avgWaitTime(Math.round(avgWaitTime))
                .build();
    }

    @Override
    public List<DepartmentStatVO> departmentStats(Long deptId, String startDate, String endDate) {
        LocalDate[] range = resolveDateRange(startDate, endDate);
        Long hospitalId = currentUserService.getCurrentHospitalId();

        return statisticsMapper.selectDepartmentStats(hospitalId, range[0].toString(), range[1].toString(), deptId);
    }

    @Override
    public List<DailyStatVO> dailyStats(String startDate, String endDate) {
        LocalDate[] range = resolveDateRange(startDate, endDate);
        Long hospitalId = currentUserService.getCurrentHospitalId();

        return statisticsMapper.selectDailyStats(hospitalId, range[0].toString(), range[1].toString());
    }

    /**
     * 解析统计区间。
     *
     * <p>任意一端为 null 时使用默认值：起始为当月 1 日，截止为当天；两端同时为 null
     * 退化为「本月至今」。入参格式须为 {@code yyyy-MM-dd}，与 Controller
     * {@code @Parameter} 描述一致。
     *
     * @param startDate 起始日期（{@code yyyy-MM-dd}），可为 null
     * @param endDate   截止日期（{@code yyyy-MM-dd}），可为 null
     * @return 二元组 {@code [start, end]}，均非 null
     */
    private LocalDate[] resolveDateRange(String startDate, String endDate) {
        LocalDate today = LocalDate.now();
        LocalDate start = startDate != null ? LocalDate.parse(startDate) : today.withDayOfMonth(1);
        LocalDate end = endDate != null ? LocalDate.parse(endDate) : today;
        return new LocalDate[]{start, end};
    }
}