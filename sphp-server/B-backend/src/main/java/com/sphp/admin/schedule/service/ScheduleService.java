package com.sphp.admin.schedule.service;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.schedule.dto.ScheduleCreateRequest;
import com.sphp.admin.schedule.dto.SlotConfigRequest;
import com.sphp.admin.schedule.vo.ForceReleaseVO;
import com.sphp.admin.schedule.vo.LockedSlotVO;
import com.sphp.admin.schedule.vo.ScheduleCreateVO;
import com.sphp.admin.schedule.vo.ScheduleListVO;
import com.sphp.admin.schedule.vo.SchedulePublishVO;
import com.sphp.admin.schedule.vo.SlotConfigVO;
import com.sphp.admin.schedule.vo.SourcePoolVO;

import java.time.LocalDate;
import java.util.List;

/**
 * 排班与号源管理服务（系分 §5.4）。
 *
 * <p>查询接口按当前登录用户数据权限过滤（ADMIN 全院 / DEPT_HEAD 本科室 / DOCTOR 本人）；
 * 写操作（创建/配置时段/发布/取消发布/手动释放）仅 ADMIN。
 */
public interface ScheduleService {

    /**
     * 分页查询排班列表。
     *
     * @param date     排班日期，默认当天
     * @param deptId   科室过滤（仅 ADMIN 生效）
     * @param doctorId 医生过滤（仅 ADMIN 生效）
     * @param status   DRAFT / PUBLISHED / CANCELLED
     */
    PageResult<ScheduleListVO> page(LocalDate date, Long deptId, Long doctorId, String status, int page, int size);

    /**
     * 创建排班（ADMIN，自动填充 dept_id 与 DRAFT 状态）。
     */
    ScheduleCreateVO create(ScheduleCreateRequest request);

    /**
     * 查询排班号源时段配置。
     */
    List<SlotConfigVO> getSlots(Long id);

    /**
     * 配置号源时段（ADMIN，仅 DRAFT，删除旧时段并重建）。
     */
    void configureSlots(Long id, SlotConfigRequest request);

    /**
     * 发布排班（ADMIN，初始化 Redis 号源缓存）。
     */
    SchedulePublishVO publish(Long id);

    /**
     * 取消发布排班（ADMIN；DRAFT 直接作废，PUBLISHED 校验无未来有效 PAID 订单后取消）。
     */
    SchedulePublishVO unpublish(Long id);

    /**
     * 锁定号源看板分页查询。
     */
    PageResult<LockedSlotVO> pageLocked(LocalDate date, Long deptId, int page, int size);

    /**
     * 号源池分页查询：按日期+班次汇总每日上下午总号源/剩余/已约/锁定数（仅 PUBLISHED）。
     *
     * @param startDate 开始日期（默认近 7 天含今天）
     * @param endDate   结束日期（默认今天）
     * @param deptId    科室过滤（仅 ADMIN 生效）
     * @param doctorId  医生过滤（仅 ADMIN 生效）
     */
    PageResult<SourcePoolVO> sourcePool(LocalDate startDate, LocalDate endDate, Long deptId, Long doctorId, int page, int size);

    /**
     * 手动释放锁定号源（ADMIN，仅 LOCKED 可释放）。
     */
    ForceReleaseVO forceRelease(Long snapshotId);
}
