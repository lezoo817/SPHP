package com.sphp.admin.statistics.service;

import com.sphp.admin.statistics.vo.DailyStatVO;
import com.sphp.admin.statistics.vo.DepartmentStatVO;
import com.sphp.admin.statistics.vo.StatisticsOverviewVO;

import java.util.List;

/**
 * 统计报表服务接口。
 */
public interface StatisticsService {

    /**
     * 运营总览。
     *
     * @param startDate 起始日期（yyyy-MM-dd），可选
     * @param endDate   截止日期（yyyy-MM-dd），可选
     * @return 运营总览数据
     */
    StatisticsOverviewVO overview(String startDate, String endDate);

    /**
     * 按科室统计。
     *
     * @param deptId    科室 ID，可选
     * @param startDate 起始日期（yyyy-MM-dd）
     * @param endDate   截止日期（yyyy-MM-dd）
     * @return 科室统计列表
     */
    List<DepartmentStatVO> departmentStats(Long deptId, String startDate, String endDate);

    /**
     * 按日期统计。
     *
     * @param startDate 起始日期（yyyy-MM-dd）
     * @param endDate   截止日期（yyyy-MM-dd）
     * @return 日统计列表
     */
    List<DailyStatVO> dailyStats(String startDate, String endDate);
}