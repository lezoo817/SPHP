package com.sphp.admin.statistics.controller;

import com.sphp.admin.statistics.service.StatisticsService;
import com.sphp.admin.statistics.vo.DailyStatVO;
import com.sphp.admin.statistics.vo.DepartmentStatVO;
import com.sphp.admin.statistics.vo.StatisticsOverviewVO;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 统计报表接口（管理员视角）。
 *
 * <p>外部完整 URL 前缀为 {@code /api/b/admin/statistics}。所有接口按当前登录管理员
 * 所属医院（{@code hospital_id}）做数据隔离。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@RestController
@RequestMapping("/b/admin/statistics")
@Tag(name = "8-统计报表", description = "运营总览/科室统计/日统计（管理员）")
@RequiredArgsConstructor
public class StatisticsController {

    private final StatisticsService statisticsService;

    @GetMapping("/overview")
    @Operation(summary = "运营总览", description = "统计指定时间范围内的总挂号量、完成率、总收入、处方量、平均等待时间")
    public Result<StatisticsOverviewVO> overview(
            @Parameter(description = "起始日期（yyyy-MM-dd，默认当月1日）")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "截止日期（yyyy-MM-dd，默认当天）")
            @RequestParam(required = false) String endDate) {
        return Result.success("查询成功", statisticsService.overview(startDate, endDate));
    }

    @GetMapping("/department")
    @Operation(summary = "按科室统计", description = "按科室统计挂号量、接诊量、处方量、号源利用率")
    public Result<List<DepartmentStatVO>> departmentStats(
            @Parameter(description = "科室 ID（可选，不传则统计全部科室）")
            @RequestParam(required = false) Long deptId,
            @Parameter(description = "起始日期（yyyy-MM-dd，默认当月1日）")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "截止日期（yyyy-MM-dd，默认当天）")
            @RequestParam(required = false) String endDate) {
        return Result.success("查询成功", statisticsService.departmentStats(deptId, startDate, endDate));
    }

    @GetMapping("/daily")
    @Operation(summary = "按日期统计", description = "按日期统计每日挂号量、接诊量、处方量、收入")
    public Result<List<DailyStatVO>> dailyStats(
            @Parameter(description = "起始日期（yyyy-MM-dd，默认当月1日）")
            @RequestParam(required = false) String startDate,
            @Parameter(description = "截止日期（yyyy-MM-dd，默认当天）")
            @RequestParam(required = false) String endDate) {
        return Result.success("查询成功", statisticsService.dailyStats(startDate, endDate));
    }
}