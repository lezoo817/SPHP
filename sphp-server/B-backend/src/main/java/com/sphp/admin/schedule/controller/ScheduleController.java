package com.sphp.admin.schedule.controller;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.schedule.dto.ScheduleCreateRequest;
import com.sphp.admin.schedule.dto.SlotConfigRequest;
import com.sphp.admin.schedule.service.ScheduleService;
import com.sphp.admin.schedule.vo.ForceReleaseVO;
import com.sphp.admin.schedule.vo.LockedSlotVO;
import com.sphp.admin.schedule.vo.ScheduleCreateVO;
import com.sphp.admin.schedule.vo.ScheduleListVO;
import com.sphp.admin.schedule.vo.SchedulePublishVO;
import com.sphp.admin.schedule.vo.SlotConfigVO;
import com.sphp.admin.schedule.vo.SourcePoolVO;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

/**
 * 排班与号源管理接口。
 *
 * <p><b>管理员视角：</b>对外暴露排班列表/创建/时段配置/发布/取消发布/锁定号源看板/手动释放能力。
 *
 * <p><b>数据隔离边界：</b>查询按当前用户数据权限过滤（ADMIN/DEPT_HEAD/DOCTOR）；
 * 写操作（创建/配置时段/发布/取消发布/手动释放）仅 ADMIN，Service 层校验本院归属。
 * 外部完整 URL 前缀为 {@code /api/b/admin/...}。
 */
@RestController
@RequestMapping("/b/admin")
@Tag(name = "3-排班管理", description = "排班列表/创建/时段配置/发布/取消发布/锁定号源看板/手动释放")
@RequiredArgsConstructor
public class ScheduleController {

    /** 每页大小上限（与全局一致），防止超大数据量查询 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 每页大小钳制到 [1, MAX_PAGE_SIZE]，避免越界 */
    private static int clampSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private final ScheduleService scheduleService;

    @GetMapping("/schedules")
    @Operation(summary = "查询排班列表", description = "分页查询排班（按当前用户数据权限过滤，含号源聚合计数）")
    public Result<PageResult<ScheduleListVO>> page(
            @Parameter(description = "排班日期 yyyy-MM-dd，默认当天") @RequestParam(required = false) LocalDate date,
            @Parameter(description = "科室ID（仅 ADMIN 生效）") @RequestParam(required = false) Long deptId,
            @Parameter(description = "医生ID（仅 ADMIN 生效）") @RequestParam(required = false) Long doctorId,
            @Parameter(description = "状态过滤：DRAFT / PUBLISHED / CANCELLED") @RequestParam(required = false) String status,
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") int size) {
        return Result.success("查询成功",
                scheduleService.page(date, deptId, doctorId, status, page, clampSize(size)));
    }

    @PostMapping("/schedules")
    @Operation(summary = "创建排班", description = "仅 ADMIN；自动填充 dept_id（取自医生所属科室），状态为 DRAFT")
    public Result<ScheduleCreateVO> create(@Valid @RequestBody ScheduleCreateRequest request) {
        return Result.success("创建成功", scheduleService.create(request));
    }

    @PutMapping("/schedules/{id}/slots")
    @Operation(summary = "配置号源时段", description = "仅 ADMIN；仅 DRAFT 可配置，重建时段（删除旧时段后插入），时段号源数之和不超过排班总号源数")
    public Result<Void> configureSlots(@PathVariable Long id, @Valid @RequestBody SlotConfigRequest request) {
        scheduleService.configureSlots(id, request);
        return Result.success("配置成功", null);
    }

    @PutMapping("/schedules/{id}/publish")
    @Operation(summary = "发布排班", description = "仅 ADMIN；DRAFT→PUBLISHED，初始化 Redis 号源缓存 slot:remain:{slotId}")
    public Result<SchedulePublishVO> publish(@PathVariable Long id) {
        return Result.success("发布成功", scheduleService.publish(id));
    }

    @GetMapping("/schedules/{id}/slots")
    @Operation(summary = "查询号源时段配置", description = "返回该排班已配置的号源时段列表")
    public Result<List<SlotConfigVO>> getSlots(@PathVariable Long id) {
        return Result.success("查询成功", scheduleService.getSlots(id));
    }

    @PutMapping("/schedules/{id}/unpublish")
    @Operation(summary = "取消发布排班", description = "仅 ADMIN；PUBLISHED 前置校验无未来有效 PAID 订单，释放 LOCKED 快照并清理号源缓存；DRAFT 直接作废")
    public Result<SchedulePublishVO> unpublish(@PathVariable Long id) {
        return Result.success("已取消发布", scheduleService.unpublish(id));
    }

    @GetMapping("/source-pool")
    @Operation(summary = "号源池", description = "按已发布排班明细返回（每行=医生某天某班次，含科室/医生/总号源/剩余/已约/锁定），日期倒序；按当前用户数据权限过滤；日期区间默认近 7 天含今天")
    public Result<PageResult<SourcePoolVO>> sourcePool(
            @Parameter(description = "开始日期 yyyy-MM-dd，默认近 7 天（含今天）") @RequestParam(required = false) LocalDate startDate,
            @Parameter(description = "结束日期 yyyy-MM-dd，默认今天") @RequestParam(required = false) LocalDate endDate,
            @Parameter(description = "科室ID（仅 ADMIN 生效）") @RequestParam(required = false) Long deptId,
            @Parameter(description = "医生ID（仅 ADMIN 生效）") @RequestParam(required = false) Long doctorId,
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") int size) {
        return Result.success("查询成功",
                scheduleService.sourcePool(startDate, endDate, deptId, doctorId, page, clampSize(size)));
    }

    @GetMapping("/slots/locked")
    @Operation(summary = "锁定号源看板", description = "分页查询 LOCKED 号源（按日期+科室+数据权限过滤，就诊人姓名脱敏，expireAt=lockedAt+15分钟）")
    // TODO 联调依赖：数据来源于 C 端患者挂号产生的 LOCKED 快照，需等 C 端挂号流程完成后联调测试
    public Result<PageResult<LockedSlotVO>> pageLocked(
            @Parameter(description = "排班日期 yyyy-MM-dd（必填）") @RequestParam LocalDate date,
            @Parameter(description = "科室ID（仅 ADMIN 生效）") @RequestParam(required = false) Long deptId,
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") int size) {
        return Result.success("查询成功",
                scheduleService.pageLocked(date, deptId, page, clampSize(size)));
    }

    @PostMapping("/slots/{id}/force-release")
    @Operation(summary = "手动释放锁定号源", description = "仅 ADMIN；仅 LOCKED 快照可释放，释放后状态回到 AVAILABLE，B 端剩余与 C 端可约池均恢复")
    public Result<ForceReleaseVO> forceRelease(@PathVariable Long id) {
        return Result.success("号源已释放", scheduleService.forceRelease(id));
    }
}
