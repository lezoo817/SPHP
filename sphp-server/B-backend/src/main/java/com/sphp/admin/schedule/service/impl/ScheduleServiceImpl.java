package com.sphp.admin.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.auth.entity.Doctor;
import com.sphp.admin.auth.mapper.DoctorMapper;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.DataScope;
import com.sphp.admin.common.enums.BRoleEnum;
import com.sphp.admin.common.enums.BUserStatusEnum;
import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.hospital.entity.Department;
import com.sphp.admin.hospital.mapper.DepartmentMapper;
import com.sphp.admin.schedule.dto.LockedSlotRow;
import com.sphp.admin.schedule.dto.BatchPublishRequest;
import com.sphp.admin.schedule.dto.BatchScheduleRequest;
import com.sphp.admin.schedule.dto.ScheduleCreateRequest;
import com.sphp.admin.schedule.dto.ScheduleSlotStat;
import com.sphp.admin.schedule.dto.SlotConfigRequest;
import com.sphp.admin.schedule.entity.Schedule;
import com.sphp.admin.schedule.entity.Slot;
import com.sphp.admin.schedule.entity.SlotSnapshot;
import com.sphp.admin.schedule.mapper.ScheduleMapper;
import com.sphp.admin.schedule.mapper.SlotMapper;
import com.sphp.admin.schedule.mapper.SlotSnapshotMapper;
import com.sphp.admin.schedule.service.ScheduleService;
import com.sphp.admin.schedule.vo.BatchCreateReportVO;
import com.sphp.admin.schedule.vo.BatchPreviewVO;
import com.sphp.admin.schedule.vo.BatchPublishReportVO;
import com.sphp.admin.schedule.vo.ForceReleaseVO;
import com.sphp.admin.schedule.vo.LockedSlotVO;
import com.sphp.admin.schedule.vo.ScheduleCreateVO;
import com.sphp.admin.schedule.vo.ScheduleListVO;
import com.sphp.admin.schedule.vo.SchedulePublishVO;
import com.sphp.admin.schedule.vo.SlotConfigVO;
import com.sphp.admin.schedule.vo.SourcePoolVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import com.sphp.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 排班与号源管理服务实现。
 *
 * <p><b>管理员视角：</b>提供排班创建/配置/发布/取消发布、号源池与锁定号源看板、手动释放等能力。
 *
 * <p><b>数据隔离边界：</b>查询接口按当前登录用户数据权限过滤（{@code DataScope}）：
 * <ul>
 *     <li>ADMIN：可按 deptId / doctorId 在本院范围内筛选</li>
 *     <li>DEPT_HEAD：强制 scope.deptId() = 本人科室</li>
 *     <li>DOCTOR：强制 scope.doctorId() = 本人医生</li>
 * </ul>
 * 写操作经 {@link CurrentUserService#getCurrentHospitalId()} 校验本院归属，
 * DEPT_HEAD / DOCTOR 无写权限。号源缓存 Key 格式 {@code cend:slot:remain:{slotId}}。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    /** 排班状态 */
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    /**
     * 排班列表一级排序的状态优先级（数值越小越靠前）：已发布（未过期）> 草稿 > 已过期 > 已作废。
     * "已过期"为虚拟状态，由 status=PUBLISHED AND schedule_date<today 派生。
     */
    private static final int SORT_PRIO_PUBLISHED_ACTIVE = 0;
    private static final int SORT_PRIO_DRAFT = 1;
    private static final int SORT_PRIO_EXPIRED = 2;
    private static final int SORT_PRIO_CANCELLED = 3;

    /** 号源快照状态 */
    private static final String SNAP_LOCKED = "LOCKED";
    private static final String SNAP_AVAILABLE = "AVAILABLE";
    private static final String SNAP_RELEASED = "RELEASED";

    /** 号源缓存 Key 前缀（与 C 端约定） */
    private static final String SLOT_REMAIN_KEY = "cend:slot:remain:%d";

    /** 锁定号源展示的过期时长（分钟），与任务契约一致 */
    private static final int LOCK_EXPIRE_MINUTES = 15;

    /** 就诊人姓名脱敏占位（单字姓名 / 空值时回退） */
    private static final String NAME_MASK_PLACEHOLDER = "**";
    /** 就诊人姓名脱敏后缀（保留首字符后的掩码） */
    private static final String NAME_MASK_SUFFIX = "**";

    /** 号源池默认查询窗口：以今天为中心前后各 N 天（含今天，总窗口 2N+1 天） */
    private static final int SOURCE_POOL_LOOKBACK_DAYS = 3;
    private static final int SOURCE_POOL_LOOKAHEAD_DAYS = 3;

    /** 批量排班：班次时间窗（小时），与前端 SHIFT_WINDOWS 保持一致 */
    private static final Map<String, int[]> SHIFT_WINDOWS = Map.of(
            "MORNING", new int[]{8, 12},
            "AFTERNOON", new int[]{14, 18}
    );

    /** 批量排班：默认时段拆分方式（每段分钟数） */
    private static final Map<String, Integer> SPLIT_MINUTES = Map.of(
            "HOURLY", 60,
            "HALF_HOUR", 30,
            "FULL", -1
    );

    /** 创建排班"立即发布"的默认时段拆分方式（1小时/段），与批量排班默认一致 */
    private static final String SLOT_SPLIT_DEFAULT = "HOURLY";

    /** 批量预览：候选去向 */
    private static final String ACTION_CREATE = "CREATE";
    private static final String ACTION_REUSE = "REUSE";
    private static final String ACTION_SKIP = "SKIP";

    /** 批量提交：跳过原因文案 */
    private static final String SKIP_REASON_DRAFT = "该日期班次已存在草稿排班";
    private static final String SKIP_REASON_PUBLISHED = "该日期班次已发布排班";

    private final ScheduleMapper scheduleMapper;
    private final SlotMapper slotMapper;
    private final SlotSnapshotMapper slotSnapshotMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final CurrentUserService currentUserService;
    private final StringRedisTemplate redisTemplate;

    /**
     * 分页查询排班列表。
     *
     * <p>数据权限：ADMIN 可按 deptId/doctorId 筛选；DEPT_HEAD 强制本科室；DOCTOR 强制本人。
     * 数据权限标识缺失时按空数据返回避免越权。
     *
     * @param date     排班日期（可空；不传则加载全部）
     * @param deptId   科室过滤（仅 ADMIN 生效）
     * @param doctorId 医生过滤（仅 ADMIN 生效）
     * @param status      排班状态过滤（DRAFT / PUBLISHED / CANCELLED / EXPIRED；可空）
     *                    "EXPIRED" 为虚拟查询值，识别后改写为 {@code status=PUBLISHED AND schedule_date<today}（不入库）
     * @param hideInvalid 隐藏失效排班（可空）；true 时排除 {@code CANCELLED} 与 {@code (PUBLISHED AND schedule_date<today)}
     * @param page        页码（1 起）
     * @param size        每页大小（调用方已钳制到 [1, MAX_PAGE_SIZE]）
     * @return 排班分页结果（含号源聚合计数）
     *
     * <p>排序见 {@link #buildScheduleListOrderBy()}：状态 4 档优先级（已发布未过期 > 草稿 > 已过期 > 已作废）→ 日期倒序 → id 升序。
     */
    @Override
    public PageResult<ScheduleListVO> page(LocalDate date, Long deptId, Long doctorId, String status, Boolean hideInvalid, int page, int size) {
        DataScope scope = currentUserService.getCurrentDataScope();
        // 数据权限标识缺失（DEPT_HEAD 无科室 / DOCTOR 无本人医生）时按空数据返回，避免越权
        if ((BRoleEnum.DEPT_HEAD.equalsCode(scope.role()) && scope.deptId() == null)
                || (BRoleEnum.DOCTOR.equalsCode(scope.role()) && scope.doctorId() == null)) {
            return PageResult.of(0, List.of(), page, size);
        }

        LambdaQueryWrapper<Schedule> wrapper = Wrappers.<Schedule>lambdaQuery()
                // 不传日期时默认加载全部排班（不再兜底为今天）
                .eq(date != null, Schedule::getScheduleDate, date)
                .isNull(Schedule::getDeletedAt)
                // schedule 表无 hospital_id，医院范围经 doctor.hospital_id 关联过滤
                .apply("doctor_id IN (SELECT id FROM doctor WHERE hospital_id = {0} AND deleted_at IS NULL)",
                        scope.hospitalId());
        if (BRoleEnum.ADMIN.equalsCode(scope.role())) {
            wrapper.eq(deptId != null, Schedule::getDeptId, deptId)
                    .eq(doctorId != null, Schedule::getDoctorId, doctorId);
        } else if (BRoleEnum.DEPT_HEAD.equalsCode(scope.role())) {
            wrapper.eq(Schedule::getDeptId, scope.deptId());
        } else {
            wrapper.eq(Schedule::getDoctorId, scope.doctorId());
        }
        // 特殊值"EXPIRED"：展开为 status=PUBLISHED AND schedule_date<today（虚拟状态，不入库）
        boolean expired = "EXPIRED".equals(status);
        wrapper.eq(!expired && StringUtils.hasText(status), Schedule::getStatus, status)
                .eq(expired, Schedule::getStatus, STATUS_PUBLISHED)
                .lt(expired, Schedule::getScheduleDate, LocalDate.now())
                // hideInvalid=true：排除 CANCELLED + (PUBLISHED AND schedule_date<today)；与 status 过滤 AND 组合
                .apply(hideInvalid != null && hideInvalid,
                        "NOT (status = {0} OR (status = {1} AND schedule_date < {2}))",
                        STATUS_CANCELLED, STATUS_PUBLISHED, LocalDate.now())
                // 排序：日期倒序 + 状态优先级 + id 升序。MyBatis-Plus 的 apply() 只能拼到 WHERE 段，
                // 写不进 ORDER BY，所以这里用 last() 整体追加；状态值与优先级均为常量，安全拼接。
                .last(buildScheduleListOrderBy());

        Page<Schedule> result = scheduleMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), buildListVO(result.getRecords()), page, size);
    }

    /**
     * 号源池分页查询。
     *
     * <p>仅统计 PUBLISHED 排班；数据权限同 {@link #page}；默认日期区间近 7 天含今天。
     *
     * @param startDate 开始日期（可空；默认 {@code today - 6}）
     * @param endDate   结束日期（可空；默认今天）
     * @param deptId    科室过滤（仅 ADMIN 生效）
     * @param doctorId  医生过滤（仅 ADMIN 生效）
     * @param page      页码
     * @param size      每页大小
     * @return 号源池分页结果
     * @throws BusinessException 日期范围无效时抛出
     */
    @Override
    public PageResult<SourcePoolVO> sourcePool(LocalDate startDate, LocalDate endDate, Long deptId, Long doctorId, int page, int size) {
        // 默认区间：以今天为中心前后各 SOURCE_POOL_LOOKBACK_DAYS / SOURCE_POOL_LOOKAHEAD_DAYS 天；显式区间需满足 start <= end
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(SOURCE_POOL_LOOKBACK_DAYS);
        LocalDate end = endDate != null ? endDate : LocalDate.now().plusDays(SOURCE_POOL_LOOKAHEAD_DAYS);
        if (start.isAfter(end)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAMETER, "日期范围无效，开始日期不能晚于结束日期");
        }
        DataScope scope = currentUserService.getCurrentDataScope();
        // 数据权限标识缺失（DEPT_HEAD 无科室 / DOCTOR 无本人医生）时按空数据返回，避免越权
        if ((BRoleEnum.DEPT_HEAD.equalsCode(scope.role()) && scope.deptId() == null)
                || (BRoleEnum.DOCTOR.equalsCode(scope.role()) && scope.doctorId() == null)) {
            return PageResult.of(0, List.of(), page, size);
        }
        // ADMIN 的 deptId/doctorId 为用户筛选条件；DEPT_HEAD / DOCTOR 强制数据权限范围
        Long filterDeptId = BRoleEnum.ADMIN.equalsCode(scope.role()) ? deptId : null;
        Long filterDoctorId = BRoleEnum.ADMIN.equalsCode(scope.role()) ? doctorId : null;
        Long scopeDeptId = BRoleEnum.DEPT_HEAD.equalsCode(scope.role()) ? scope.deptId() : null;
        Long scopeDoctorId = BRoleEnum.DOCTOR.equalsCode(scope.role()) ? scope.doctorId() : null;
        IPage<SourcePoolVO> result = slotMapper.selectSourcePoolPage(
                new Page<>(page, size), start, end, scope.hospitalId(),
                filterDeptId, filterDoctorId, scopeDeptId, scopeDoctorId);
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    /**
     * 创建排班（ADMIN）。
     *
     * <p>同医生同日期同班次仅允许一条有效排班；存在 CANCELLED 时原地复用并重置为 DRAFT；
     * DRAFT/PUBLISHED 重复时拒绝。自动填充 dept_id（取自医生所属科室）。
     * {@code publishImmediately=true} 时按默认拆分（1小时/段）自动配置号源时段并发布，返回状态为 PUBLISHED。
     *
     * @param request 创建请求（医生 / 日期 / 班次 / 总号源 / 是否立即发布）
     * @return 创建结果（排班 ID、状态 DRAFT 或 PUBLISHED、创建时间）
     * @throws BusinessException 医生不存在 / 跨院 / 停用 / 日期过早 / 重复排班 / 号源过少无法按默认拆分时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleCreateVO create(ScheduleCreateRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Doctor doctor = doctorMapper.selectById(request.getDoctorId());
        if (doctor == null || doctor.getDeletedAt() != null || !doctor.getHospitalId().equals(hospitalId)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_USER_INPUT, "医生不存在或不属于本院");
        }
        if (!BUserStatusEnum.isEnabled(doctor.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID, "医生当前状态不可排班");
        }
        LocalDate scheduleDate = LocalDate.parse(request.getScheduleDate());
        if (scheduleDate.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAMETER, "排班日期不能早于今天");
        }
        // 立即发布需按默认拆分（1小时/段）切分时段，号源过少无法切分时提前拦截，避免创建后回滚
        if (Boolean.TRUE.equals(request.getPublishImmediately())) {
            validateImmediatePublishSlots(request.getTotalSlots());
        }
        // 唯一校验：同医生同日期同班次。已作废（CANCELLED）的排班允许重新建立——
        // 原地复用该行并重置为草稿；存在 DRAFT/PUBLISHED 有效排班时禁止重复创建
        List<Schedule> exists = scheduleMapper.selectList(Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getDoctorId, doctor.getId())
                .eq(Schedule::getScheduleDate, scheduleDate)
                .eq(Schedule::getShift, request.getShift())
                .isNull(Schedule::getDeletedAt));
        Schedule cancelled = null;
        for (Schedule s : exists) {
            if (STATUS_CANCELLED.equals(s.getStatus())) {
                cancelled = s;
            } else {
                throw new BusinessException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID, "该医生当天该班次已存在排班");
            }
        }
        Long scheduleId;
        OffsetDateTime createdAt;
        if (cancelled != null) {
            // 复用已作废排班：重置为草稿并清理旧时段，等价于重新建立该排班
            scheduleMapper.update(null, Wrappers.<Schedule>lambdaUpdate()
                    .set(Schedule::getStatus, STATUS_DRAFT)
                    .set(Schedule::getTotalSlots, request.getTotalSlots())
                    .set(Schedule::getPublishedAt, null)
                    .set(Schedule::getUpdatedAt, OffsetDateTime.now())
                    .eq(Schedule::getId, cancelled.getId()));
            slotMapper.update(null, Wrappers.<Slot>lambdaUpdate()
                    .set(Slot::getDeletedAt, OffsetDateTime.now())
                    .set(Slot::getUpdatedAt, OffsetDateTime.now())
                    .eq(Slot::getScheduleId, cancelled.getId())
                    .isNull(Slot::getDeletedAt));
            log.info("重新建立已作废排班 scheduleId={}, doctorId={}, date={}, shift={}, totalSlots={}",
                    cancelled.getId(), doctor.getId(), scheduleDate, request.getShift(), request.getTotalSlots());
            scheduleId = cancelled.getId();
            createdAt = cancelled.getCreatedAt();
        } else {
            Schedule schedule = new Schedule();
            schedule.setDoctorId(doctor.getId());
            schedule.setDeptId(doctor.getDeptId());
            schedule.setScheduleDate(scheduleDate);
            schedule.setShift(request.getShift());
            schedule.setTotalSlots(request.getTotalSlots());
            schedule.setStatus(STATUS_DRAFT);
            scheduleMapper.insert(schedule);
            log.info("创建排班 scheduleId={}, doctorId={}, date={}, shift={}, totalSlots={}",
                    schedule.getId(), doctor.getId(), scheduleDate, request.getShift(), request.getTotalSlots());
            scheduleId = schedule.getId();
            // DB 默认值不会回填实体，回查一次获取 created_at
            Schedule created = scheduleMapper.selectById(scheduleId);
            createdAt = created != null ? created.getCreatedAt() : OffsetDateTime.now();
        }

        // 创建成功后立即发布：复用 configureSlots + publish 按默认拆分配号源时段并发布（与批量排班口径一致）；
        // 三者同处 create() 事务内，任一失败整体回滚，不会留下半成品
        if (Boolean.TRUE.equals(request.getPublishImmediately())) {
            configureSlots(scheduleId,
                    slotItemsOf(buildSlotConfigItems(request.getShift(), SLOT_SPLIT_DEFAULT, request.getTotalSlots())));
            publish(scheduleId);
            return ScheduleCreateVO.builder()
                    .id(scheduleId)
                    .status(STATUS_PUBLISHED)
                    .createdAt(createdAt)
                    .build();
        }
        return ScheduleCreateVO.builder()
                .id(scheduleId)
                .status(STATUS_DRAFT)
                .createdAt(createdAt)
                .build();
    }

    /**
     * 查询排班号源时段配置（按开始时间升序）。
     *
     * @param id 排班 ID
     * @return 时段配置列表（可能为空）
     * @throws BusinessException 排班不存在或越权时抛出
     */
    @Override
    public List<SlotConfigVO> getSlots(Long id) {
        Schedule schedule = getScheduleInScope(id);
        return slotMapper.selectList(Wrappers.<Slot>lambdaQuery()
                        .eq(Slot::getScheduleId, schedule.getId())
                        .isNull(Slot::getDeletedAt)
                        .orderByAsc(Slot::getStartTime))
                .stream()
                .map(s -> SlotConfigVO.builder()
                        .id(s.getId())
                        .startTime(s.getStartTime())
                        .endTime(s.getEndTime())
                        .totalCount(s.getTotalCount())
                        .remainCount(s.getRemainCount())
                        .build())
                .toList();
    }

    /**
     * 配置号源时段（ADMIN）。
     *
     * <p>仅 DRAFT 状态可配置；旧时段逻辑删除后插入新时段；时段号源数之和不超过排班总号源数。
     *
     * @param id      排班 ID
     * @param request 时段配置请求（时段列表）
     * @throws BusinessException 非 DRAFT / 时段不合法 / 总数越界 / 时段重叠时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void configureSlots(Long id, SlotConfigRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Schedule schedule = getSchedule(id, hospitalId);
        if (!STATUS_DRAFT.equals(schedule.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID, "仅草稿状态可配置号源时段");
        }
        List<SlotConfigRequest.SlotConfigItem> items = request.getSlotConfigs();
        List<Slot> slots = new ArrayList<>(items.size());
        int sum = 0;
        for (SlotConfigRequest.SlotConfigItem item : items) {
            LocalTime start = LocalTime.parse(item.getStartTime());
            LocalTime end = LocalTime.parse(item.getEndTime());
            if (!end.isAfter(start)) {
                throw new BusinessException(ErrorCodeEnum.INVALID_PARAMETER, "时段结束时间必须晚于开始时间：" + item.getStartTime());
            }
            sum += item.getCount();
            Slot slot = new Slot();
            slot.setScheduleId(schedule.getId());
            slot.setStartTime(start);
            slot.setEndTime(end);
            slot.setTotalCount(item.getCount());
            slot.setRemainCount(item.getCount());
            slots.add(slot);
        }
        // 时段号源数之和不超过排班总号源数（余量留作机动，不强制相等）
        if (sum > schedule.getTotalSlots()) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAMETER,
                    "时段号源数之和(" + sum + ")不能超过排班总号源数(" + schedule.getTotalSlots() + ")");
        }
        // 时段不得重叠（按开始时间排序，相邻区间交叉即重叠）
        List<Slot> sorted = slots.stream().sorted(Comparator.comparing(Slot::getStartTime)).toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).getEndTime().isAfter(sorted.get(i).getStartTime())) {
                throw new BusinessException(ErrorCodeEnum.INVALID_PARAMETER, "号源时段之间不能重叠");
            }
        }

        // 重建时段：旧时段逻辑删除（复用已作废排班时旧时段可能仍关联历史快照，
        // 物理删除会触发 slot_snapshot 外键约束），再插入新时段
        slotMapper.update(null, Wrappers.<Slot>lambdaUpdate()
                .set(Slot::getDeletedAt, OffsetDateTime.now())
                .set(Slot::getUpdatedAt, OffsetDateTime.now())
                .eq(Slot::getScheduleId, schedule.getId())
                .isNull(Slot::getDeletedAt));
        slots.forEach(slotMapper::insert);
        log.info("配置排班号源时段 scheduleId={}, 时段数={}, 号源和={}/{}",
                schedule.getId(), slots.size(), sum, schedule.getTotalSlots());
    }

    /**
     * 发布排班（ADMIN；DRAFT → PUBLISHED）。
     *
     * <p>校验门禁：仅 DRAFT、已配置时段、时段号源数之和等于总号源数。发布后初始化
     * Redis 号源缓存并按 generate_series 批量生成 AVAILABLE 号源快照。
     *
     * @param id 排班 ID
     * @return 发布结果（ID、状态、发布时间）
     * @throws BusinessException 非 DRAFT / 未配置时段 / 号源和不匹配时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchedulePublishVO publish(Long id) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Schedule schedule = getSchedule(id, hospitalId);
        if (!STATUS_DRAFT.equals(schedule.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID, "仅草稿状态可发布排班");
        }
        List<Slot> slots = listSlots(schedule.getId());
        if (slots.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID, "请先配置号源时段再发布");
        }
        // 时段号源数之和须等于排班总号源数方可发布
        int slotCountSum = slots.stream().mapToInt(Slot::getTotalCount).sum();
        if (slotCountSum != schedule.getTotalSlots()) {
            throw new BusinessException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID,
                    "时段号源数之和(" + slotCountSum + ")须等于排班总号源数(" + schedule.getTotalSlots() + ")，方可发布");
        }

        schedule.setStatus(STATUS_PUBLISHED);
        schedule.setPublishedAt(OffsetDateTime.now());
        schedule.setUpdatedAt(OffsetDateTime.now());
        scheduleMapper.updateById(schedule);

        // 初始化 Redis 号源缓存（幂等覆盖）
        for (Slot slot : slots) {
            redisTemplate.opsForValue().set(redisKey(slot.getId()), String.valueOf(slot.getRemainCount()));
        }
        // 生成号源快照：号源池以 slot_snapshot 的 AVAILABLE 记录为准，发布后患者即可预约
        generateSlotSnapshots(slots);
        log.info("发布排班 scheduleId={}, 时段数={}", schedule.getId(), slots.size());
        return SchedulePublishVO.builder()
                .id(schedule.getId())
                .status(STATUS_PUBLISHED)
                .publishedAt(schedule.getPublishedAt())
                .build();
    }

    /**
     * 取消发布排班（ADMIN）。
     *
     * <p>DRAFT 直接作废；PUBLISHED 校验无未来有效 PAID 订单后释放 LOCKED 快照为
     * AVAILABLE、清空 AVAILABLE 快照、清理 Redis 号源缓存。
     *
     * @param id 排班 ID
     * @return 取消结果（ID、状态、发布时间为 null）
     * @throws BusinessException 已取消 / 存在已支付预约时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchedulePublishVO unpublish(Long id) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Schedule schedule = getSchedule(id, hospitalId);
        String status = schedule.getStatus();
        if (STATUS_CANCELLED.equals(status)) {
            throw new BusinessException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID, "排班已取消，不可重复操作");
        }
        List<Slot> slots = listSlots(schedule.getId());
        if (STATUS_PUBLISHED.equals(status)) {
            // 前置校验：存在 PAID 且 schedule_date >= 今天的有效挂号订单则禁止取消
            if (scheduleMapper.countPaidFutureAppointments(schedule.getId()) > 0) {
                throw new BusinessException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID, "该排班存在已支付预约，无法取消发布");
            }
            // 释放 LOCKED 快照为 AVAILABLE，并 Redis INCR 归还 remain_count
            releaseLockedSnapshots(slots);
            // 清空该排班全部 AVAILABLE 号源快照：取消发布后号源池随之清空，避免残留可约数据
            slotSnapshotMapper.clearAvailableSnapshotsBySchedule(schedule.getId());
        }
        // 批量删除该排班下所有时段的 Redis 号源缓存（防残留误读）
        deleteSlotCache(slots);

        schedule.setStatus(STATUS_CANCELLED);
        schedule.setUpdatedAt(OffsetDateTime.now());
        scheduleMapper.updateById(schedule);
        // TODO 通知已预约患者（待通知渠道接入；DRAFT 作废无预约无需通知）
        log.info("取消发布排班 scheduleId={}, 原状态={}", schedule.getId(), status);
        return SchedulePublishVO.builder()
                .id(schedule.getId())
                .status(STATUS_CANCELLED)
                .publishedAt(null)
                .build();
    }

    /**
     * 锁定号源看板分页查询（status=LOCKED 快照，按日期+科室+数据权限过滤）。
     *
     * <p>就诊人姓名在 Service 层脱敏（保留首字符 + 掩码）；expireAt = lockedAt + 15 分钟。
     *
     * @param date   排班日期（必填）
     * @param deptId 科室过滤（仅 ADMIN 生效；可空）
     * @param page   页码
     * @param size   每页大小
     * @return 锁定号源分页结果
     */
    @Override
    public PageResult<LockedSlotVO> pageLocked(LocalDate date, Long deptId, int page, int size) {
        DataScope scope = currentUserService.getCurrentDataScope();
        if ((BRoleEnum.DEPT_HEAD.equalsCode(scope.role()) && scope.deptId() == null)
                || (BRoleEnum.DOCTOR.equalsCode(scope.role()) && scope.doctorId() == null)) {
            return PageResult.of(0, List.of(), page, size);
        }
        Long scopeDeptId = BRoleEnum.DEPT_HEAD.equalsCode(scope.role()) ? scope.deptId() : null;
        Long scopeDoctorId = BRoleEnum.DOCTOR.equalsCode(scope.role()) ? scope.doctorId() : null;
        IPage<LockedSlotRow> result = slotSnapshotMapper.selectLockedPage(
                new Page<>(page, size), date, deptId, scope.hospitalId(), scopeDeptId, scopeDoctorId);

        List<LockedSlotVO> list = result.getRecords().stream()
                .map(r -> LockedSlotVO.builder()
                        .slotId(r.getSlotId())
                        .scheduleId(r.getScheduleId())
                        .doctorName(r.getDoctorName())
                        .patientName(maskName(r.getPatientName()))
                        .lockedAt(r.getLockedAt())
                        .status(r.getStatus())
                        .expireAt(r.getLockedAt() == null ? null : r.getLockedAt().plusMinutes(LOCK_EXPIRE_MINUTES))
                        .build())
                .toList();
        return PageResult.of(result.getTotal(), list, page, size);
    }

    /**
     * 手动释放锁定号源（ADMIN）。
     *
     * <p>仅 LOCKED 快照可释放，状态回到 AVAILABLE，patient_id 清空，
     * Redis 号源 INCR 归还；B 端"剩余"统计与 C 端可约池同步。
     *
     * @param snapshotId 号源快照 ID
     * @return 释放结果（快照 ID、状态、释放时间）
     * @throws BusinessException 快照不存在 / 非 LOCKED / 跨院时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ForceReleaseVO forceRelease(Long snapshotId) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        SlotSnapshot snapshot = slotSnapshotMapper.selectById(snapshotId);
        if (snapshot == null || snapshot.getDeletedAt() != null) {
            throw new BusinessException(ErrorCodeEnum.INVALID_USER_INPUT, "号源不存在");
        }
        if (!SNAP_LOCKED.equals(snapshot.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID, "号源状态非 LOCKED，不可释放");
        }
        // 归属校验：快照所属排班须属于本院，防越权释放他院号源
        ensureSnapshotInHospital(snapshot, hospitalId);

        // 与"取消发布"路径（releaseLockedSnapshots）保持一致：LOCKED → AVAILABLE，
        // 让 B 端"剩余"统计（aggregateByScheduleIds / selectSourcePoolPage 数 AVAILABLE + RELEASED）、
        // 以及 C 端可约池都把该号源视为可约；patient_id 清空以符合"未占用为空"的字段语义。
        // 注：C 端取消订单路径走 RELEASED，B 端"剩余"同步包含 RELEASED，所以两侧口径一致。
        snapshot.setStatus(SNAP_AVAILABLE);
        snapshot.setPatientId(null);
        snapshot.setUpdatedAt(OffsetDateTime.now());
        slotSnapshotMapper.updateById(snapshot);
        redisTemplate.opsForValue().increment(redisKey(snapshot.getSlotId()));
        log.info("手动释放锁定号源 snapshotId={}, slotId={}", snapshot.getId(), snapshot.getSlotId());
        return ForceReleaseVO.builder()
                .slotId(snapshot.getId())
                .status(SNAP_AVAILABLE)
                .releasedAt(OffsetDateTime.now())
                .build();
    }

    /**
     * 批量排班预览（ADMIN，只读）。
     *
     * <p>展开 (医生 × 日期范围 × 星期模式 × 班次) 笛卡尔积为候选集，预判每个候选的去向：
     * 同一日期同一班次已存在 DRAFT/PUBLISHED 排班时跳过；CANCELLED 视为可复用；不存在时新建。
     *
     * @param request 批量请求
     * @return 预览结果（含默认时段拆分 + 每个候选的去向）
     * @throws BusinessException 医生越权 / 日期不合法 / 时段拆分与号源数不匹配时抛出
     */
    @Override
    public BatchPreviewVO previewBatch(BatchScheduleRequest request) {
        Doctor doctor = resolveBatchDoctor(request.getDoctorId());
        List<Candidate> candidates = expandCandidates(request);
        Map<String, String> existingStatus = loadExistingScheduleStatuses(doctor.getId(), candidates);

        List<BatchPreviewVO.BatchPreviewItem> items = new ArrayList<>(candidates.size());
        int toCreate = 0, toSkip = 0;
        for (Candidate c : candidates) {
            String key = c.date() + "|" + c.shift();
            String existStatus = existingStatus.get(key);
            BatchPreviewVO.BatchPreviewItem.BatchPreviewItemBuilder b = BatchPreviewVO.BatchPreviewItem.builder()
                    .scheduleDate(c.date())
                    .shift(c.shift());
            if (existStatus == null) {
                b.action(ACTION_CREATE);
                toCreate++;
            } else if (STATUS_CANCELLED.equals(existStatus)) {
                b.action(ACTION_REUSE);
                toCreate++;
            } else {
                b.action(ACTION_SKIP)
                        .skipReason(STATUS_DRAFT.equals(existStatus) ? SKIP_REASON_DRAFT : SKIP_REASON_PUBLISHED);
                toSkip++;
            }
            items.add(b.build());
        }

        return BatchPreviewVO.builder()
                .doctorId(doctor.getId())
                .doctorName(doctor.getName())
                .startDate(request.getStartDate())
                .endDate(request.getEndDate())
                .weekdays(request.getWeekdays())
                .shifts(request.getShifts())
                .totalSlots(request.getTotalSlots())
                .slotSplitMode(request.getSlotSplitMode())
                .slotSplitPreview(buildSlotSplitPreview(request))
                .items(items)
                .toCreateCount(toCreate)
                .toSkipCount(toSkip)
                .build();
    }

    /**
     * 批量排班提交（ADMIN；按预览结果执行实际写入）。
     *
     * <p>复用 {@link #create(ScheduleCreateRequest)} 与 {@link #configureSlots(Long, SlotConfigRequest)}
     * 逐条处理；每对 (create, configureSlots) 独立事务（Spring REQUIRED 传播 + 调用方非事务）。
     * 跳过 DRAFT/PUBLISHED 冲突、复用 CANCELLED、新建 DRAFT。
     *
     * @param request 批量请求
     * @return 提交结果报告（新建/复用/跳过分类汇总）
     */
    @Override
    public BatchCreateReportVO createBatch(BatchScheduleRequest request) {
        Doctor doctor = resolveBatchDoctor(request.getDoctorId());
        List<Candidate> candidates = expandCandidates(request);
        Map<String, String> existingStatus = loadExistingScheduleStatuses(doctor.getId(), candidates);

        // 不同班次使用不同时间窗，slot 配置按 shift 缓存复用
        Map<String, List<SlotConfigRequest.SlotConfigItem>> slotConfigByShift = new HashMap<>();
        for (String shift : request.getShifts()) {
            slotConfigByShift.put(shift, buildSlotConfigItems(shift, request.getSlotSplitMode(), request.getTotalSlots()));
        }

        List<BatchCreateReportVO.BatchItem> createdItems = new ArrayList<>();
        List<BatchCreateReportVO.BatchItem> reusedItems = new ArrayList<>();
        List<BatchCreateReportVO.BatchSkipItem> skippedItems = new ArrayList<>();
        int createdCount = 0, reusedCount = 0, skippedCount = 0;

        for (Candidate c : candidates) {
            String key = c.date() + "|" + c.shift();
            String existStatus = existingStatus.get(key);
            if (existStatus != null && !STATUS_CANCELLED.equals(existStatus)) {
                String reason = STATUS_DRAFT.equals(existStatus) ? SKIP_REASON_DRAFT : SKIP_REASON_PUBLISHED;
                skippedItems.add(BatchCreateReportVO.BatchSkipItem.builder()
                        .scheduleDate(c.date()).shift(c.shift()).reason(reason).build());
                skippedCount++;
                continue;
            }
            ScheduleCreateRequest single = new ScheduleCreateRequest();
            single.setDoctorId(doctor.getId());
            single.setScheduleDate(c.date().toString());
            single.setShift(c.shift());
            single.setTotalSlots(request.getTotalSlots());
            ScheduleCreateVO created = create(single);
            configureSlots(created.getId(), slotItemsOf(slotConfigByShift.get(c.shift())));
            if (STATUS_CANCELLED.equals(existStatus)) {
                reusedItems.add(BatchCreateReportVO.BatchItem.builder()
                        .scheduleId(created.getId()).scheduleDate(c.date()).shift(c.shift()).build());
                reusedCount++;
            } else {
                createdItems.add(BatchCreateReportVO.BatchItem.builder()
                        .scheduleId(created.getId()).scheduleDate(c.date()).shift(c.shift()).build());
                createdCount++;
            }
        }

        log.info("批量排班提交 doctorId={}, range={}~{}, weekdays={}, shifts={}, totalSlots={}, split={}, created={}, reused={}, skipped={}",
                doctor.getId(), request.getStartDate(), request.getEndDate(), request.getWeekdays(),
                request.getShifts(), request.getTotalSlots(), request.getSlotSplitMode(),
                createdCount, reusedCount, skippedCount);

        return BatchCreateReportVO.builder()
                .createdCount(createdCount)
                .reusedCount(reusedCount)
                .skippedCount(skippedCount)
                .createdItems(createdItems)
                .reusedItems(reusedItems)
                .skippedItems(skippedItems)
                .build();
    }

    /**
     * 批量发布排班（ADMIN）。
     *
     * <p>逐条复用 {@link #publish(Long)}；任何失败（非 DRAFT / 越权 / 未配置时段等）以明细形式返回，不抛错中断整批。
     * 每条 publish() 自身为独立事务，部分失败不影响其他条目提交。
     *
     * @param request 批量发布请求
     * @return 发布结果报告
     */
    @Override
    public BatchPublishReportVO batchPublish(BatchPublishRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        List<BatchPublishReportVO.FailedItem> failedItems = new ArrayList<>();
        int published = 0, failed = 0;

        for (Long id : request.getScheduleIds()) {
            Schedule s = scheduleMapper.selectById(id);
            // 跨院/不存在/已逻辑删除统一视为不可发布（对外统一为"排班不存在"，不暴露医院隔离细节）
            if (s == null || s.getDeletedAt() != null) {
                failedItems.add(BatchPublishReportVO.FailedItem.builder()
                        .scheduleId(id).reason("排班不存在").build());
                failed++;
                continue;
            }
            Doctor doctor = doctorMapper.selectById(s.getDoctorId());
            // 跨院/不存在/已逻辑删除统一视为不可发布（对外统一为"排班不存在"，不暴露医院隔离细节）
            if (doctor == null || doctor.getDeletedAt() != null
                    || !hospitalId.equals(doctor.getHospitalId())) {
                failedItems.add(BatchPublishReportVO.FailedItem.builder()
                        .scheduleId(id).reason("排班不存在").build());
                failed++;
                continue;
            }
            if (!STATUS_DRAFT.equals(s.getStatus())) {
                String reason = STATUS_PUBLISHED.equals(s.getStatus()) ? "已发布，无需重复发布" : "已作废，不可发布";
                failedItems.add(BatchPublishReportVO.FailedItem.builder()
                        .scheduleId(id).reason(reason).build());
                failed++;
                continue;
            }
            try {
                publish(id);
                published++;
            } catch (BusinessException ex) {
                // 时段未配置 / 号源和不匹配等业务校验失败，计入失败明细继续处理其他条目
                failedItems.add(BatchPublishReportVO.FailedItem.builder()
                        .scheduleId(id).reason(ex.getMessage()).build());
                failed++;
            }
        }
        log.info("批量发布排班 total={}, published={}, failed={}",
                request.getScheduleIds().size(), published, failed);
        return BatchPublishReportVO.builder()
                .publishedCount(published)
                .failedCount(failed)
                .failedItems(failedItems)
                .build();
    }

    /**
     * 校验批量请求中的医生归属：必须存在、启用、属于本院。
     */
    private Doctor resolveBatchDoctor(Long doctorId) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null || doctor.getDeletedAt() != null || !doctor.getHospitalId().equals(hospitalId)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_USER_INPUT, "医生不存在或不属于本院");
        }
        if (!BUserStatusEnum.isEnabled(doctor.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID, "医生当前状态不可排班");
        }
        return doctor;
    }

    /**
     * 解析日期范围 + 星期模式 + 班次为 (date, shift) 候选列表，并校验日期范围与时段拆分兼容性。
     */
    private List<Candidate> expandCandidates(BatchScheduleRequest request) {
        LocalDate start = LocalDate.parse(request.getStartDate());
        LocalDate end = LocalDate.parse(request.getEndDate());
        if (end.isBefore(start)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAMETER, "结束日期不能早于开始日期");
        }
        if (start.isBefore(LocalDate.now())) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAMETER, "开始日期不能早于今天");
        }
        long span = end.toEpochDay() - start.toEpochDay() + 1;
        if (span > BatchScheduleRequest.MAX_DATE_RANGE_DAYS) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAMETER,
                    "日期范围不能超过 " + BatchScheduleRequest.MAX_DATE_RANGE_DAYS + " 天");
        }

        Set<Integer> weekdaySet = request.getWeekdays().stream().collect(Collectors.toSet());
        // 候选去重：使用 LinkedHashMap 保持展开顺序
        Map<String, Candidate> map = new LinkedHashMap<>();
        for (LocalDate d = start; !d.isAfter(end); d = d.plusDays(1)) {
            if (!weekdaySet.contains(d.getDayOfWeek().getValue())) {
                continue;
            }
            for (String shift : request.getShifts()) {
                map.put(d + "|" + shift, new Candidate(d, shift));
            }
        }
        if (map.isEmpty()) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAMETER, "在所选范围内没有匹配的日期");
        }

        validateSplitCompatibility(request.getSlotSplitMode(), request.getTotalSlots());
        return new ArrayList<>(map.values());
    }

    /**
     * 一次性加载医生在 [min(candidates), max(candidates)] 区间内所有排班状态，
     * 用 (date|shift) 作为 key 索引，避免逐条查询。
     */
    private Map<String, String> loadExistingScheduleStatuses(Long doctorId, List<Candidate> candidates) {
        LocalDate min = candidates.get(0).date();
        LocalDate max = candidates.get(0).date();
        for (Candidate c : candidates) {
            if (c.date().isBefore(min)) min = c.date();
            if (c.date().isAfter(max)) max = c.date();
        }
        List<Schedule> existing = scheduleMapper.selectList(Wrappers.<Schedule>lambdaQuery()
                .eq(Schedule::getDoctorId, doctorId)
                .between(Schedule::getScheduleDate, min, max)
                .isNull(Schedule::getDeletedAt));
        Map<String, String> result = new HashMap<>(existing.size() * 2);
        for (Schedule s : existing) {
            result.put(s.getScheduleDate() + "|" + s.getShift(), s.getStatus());
        }
        return result;
    }

    /**
     * 校验时段拆分与号源总数兼容性：FULL 模式无要求；其余模式需每段至少 1 个号源。
     */
    private void validateSplitCompatibility(String splitMode, int totalSlots) {
        int maxSegments = maxSegmentsOf(splitMode);
        if (maxSegments <= 0) {
            return; // FULL 模式
        }
        if (totalSlots < maxSegments) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAMETER,
                    "号源总数(" + totalSlots + ")小于所选拆分方式最少需要的段数(" + maxSegments + ")，请减少拆分粒度或增加号源");
        }
    }

    /**
     * 计算某拆分方式下任一班次的最大时段段数。
     *
     * <p>以最大 4 小时班次时间窗为基准（MORNING / AFTERNOON 均为 4 小时）。
     *
     * @param splitMode 拆分方式（HOURLY / HALF_HOUR / FULL）
     * @return 最大时段段数；FULL 整段无分段概念返回 0
     */
    private int maxSegmentsOf(String splitMode) {
        int minutes = SPLIT_MINUTES.get(splitMode);
        if (minutes < 0) {
            return 0; // FULL 模式
        }
        int maxSegments = 0;
        for (int[] win : SHIFT_WINDOWS.values()) {
            int segs = (win[1] - win[0]) * 60 / minutes;
            if (segs > maxSegments) maxSegments = segs;
        }
        return maxSegments;
    }

    /**
     * 校验"创建后立即发布"的号源总数足以按默认拆分切分时段。
     *
     * <p>默认拆分（1小时/段）下任一班次最多 4 段，号源总数小于段数会产生 0 号源时段，
     * 影响号源池可约性展示，故创建时即拦截（与批量排班的拆分兼容校验同源）。
     *
     * @param totalSlots 号源总数
     * @throws BusinessException 号源总数小于默认拆分最少段数时抛出
     */
    private void validateImmediatePublishSlots(int totalSlots) {
        int maxSegments = maxSegmentsOf(SLOT_SPLIT_DEFAULT);
        if (totalSlots < maxSegments) {
            throw new BusinessException(ErrorCodeEnum.INVALID_PARAMETER,
                    "号源总数(" + totalSlots + ")小于按 1小时/段 拆分所需的最少段数(" + maxSegments + ")，请增加号源或取消立即发布");
        }
    }

    /**
     * 生成默认时段拆分预览（取首个班次的拆分结果展示）。
     */
    private List<BatchPreviewVO.SlotSplitItem> buildSlotSplitPreview(BatchScheduleRequest request) {
        List<SlotConfigRequest.SlotConfigItem> items =
                buildSlotConfigItems(request.getShifts().get(0), request.getSlotSplitMode(), request.getTotalSlots());
        return items.stream()
                .map(it -> BatchPreviewVO.SlotSplitItem.builder()
                        .startTime(it.getStartTime())
                        .endTime(it.getEndTime())
                        .count(it.getCount())
                        .build())
                .toList();
    }

    /**
     * 构造排班列表分页查询的 ORDER BY 子句。
     *
     * <p>排序规则：状态 4 档优先级（已发布未过期 > 草稿 > 已过期 > 已作废）→ 日期倒序（最近的排班在最前）→
     * id 升序（保证分页结果稳定）。"已过期"是虚拟状态，对应 {@code status=PUBLISHED AND schedule_date<today}。
     *
     * <p>必须用 {@code last()} 追加（{@code apply()} 只能拼到 WHERE 段）。
     * 拼接的常量均为 {@code private static final}，无 SQL 注入风险。
     */
    private String buildScheduleListOrderBy() {
        // 此处是 MP last() 运行时拼接的原始 SQL，不走 MyBatis XML <script> 解析，
        // 与 SlotMapper/StatisticsMapper 中 @Select 注解（须转义 &lt;）不同，< 与 >= 直接写原始符号
        return String.format(
                "ORDER BY "
                        + "CASE "
                        + "  WHEN status = '%s' AND schedule_date >= CURRENT_DATE THEN %d "
                        + "  WHEN status = '%s' THEN %d "
                        + "  WHEN status = '%s' AND schedule_date < CURRENT_DATE THEN %d "
                        + "  ELSE %d "
                        + "END ASC, "
                        + "schedule_date DESC, "
                        + "id ASC",
                STATUS_PUBLISHED, SORT_PRIO_PUBLISHED_ACTIVE,
                STATUS_DRAFT, SORT_PRIO_DRAFT,
                STATUS_PUBLISHED, SORT_PRIO_EXPIRED,
                SORT_PRIO_CANCELLED);
    }

    /**
     * 按 (班次时段窗 + 拆分方式 + 号源总数) 生成等比时段配置，余量从首段开始 +1。
     */
    private List<SlotConfigRequest.SlotConfigItem> buildSlotConfigItems(String shift, String splitMode, int totalSlots) {
        int minutes = SPLIT_MINUTES.get(splitMode);
        int[] win = SHIFT_WINDOWS.get(shift);
        if (minutes < 0) {
            return List.of(buildItem(timeOf(win[0]), timeOf(win[1]), totalSlots));
        }
        int segs = (win[1] - win[0]) * 60 / minutes;
        int base = totalSlots / segs;
        int rem = totalSlots % segs;
        List<SlotConfigRequest.SlotConfigItem> items = new ArrayList<>(segs);
        for (int i = 0; i < segs; i++) {
            int count = base + (i < rem ? 1 : 0);
            int startMin = win[0] * 60 + i * minutes;
            int endMin = startMin + minutes;
            items.add(buildItem(timeOf(startMin), timeOf(endMin), count));
        }
        return items;
    }

    private SlotConfigRequest.SlotConfigItem buildItem(String start, String end, int count) {
        SlotConfigRequest.SlotConfigItem it = new SlotConfigRequest.SlotConfigItem();
        it.setStartTime(start);
        it.setEndTime(end);
        it.setCount(count);
        return it;
    }

    /** 时段配置项装入请求（configureSlots 期望 {@code SlotConfigRequest}） */
    private SlotConfigRequest slotItemsOf(List<SlotConfigRequest.SlotConfigItem> items) {
        SlotConfigRequest req = new SlotConfigRequest();
        req.setSlotConfigs(items);
        return req;
    }

    /** 把小时数（可含小数）格式化为 HH:mm；批量拆分只用整点偏移 */
    private static String timeOf(int minutesOfDay) {
        int h = minutesOfDay / 60;
        int m = minutesOfDay % 60;
        return String.format("%02d:%02d", h, m);
    }

    /** 候选 (日期, 班次) 不可变记录 */
    private record Candidate(LocalDate date, String shift) { }

    /**
     * 分页记录组装为列表 VO（批量加载医生/科室名与号源聚合，避免 N+1）。
     *
     * @param records 当前页排班记录（可能为空）
     * @return 列表 VO（空时返回空列表）
     */
    private List<ScheduleListVO> buildListVO(List<Schedule> records) {
        if (records.isEmpty()) {
            return List.of();
        }
        List<Long> scheduleIds = records.stream().map(Schedule::getId).toList();
        Map<Long, ScheduleSlotStat> statMap = slotMapper.aggregateByScheduleIds(scheduleIds).stream()
                .collect(Collectors.toMap(ScheduleSlotStat::getScheduleId, s -> s, (a, b) -> a));

        List<Long> doctorIds = records.stream().map(Schedule::getDoctorId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> doctorNames = loadDoctorNames(doctorIds);
        List<Long> deptIds = records.stream().map(Schedule::getDeptId)
                .filter(Objects::nonNull).distinct().toList();
        Map<Long, String> deptNames = loadDeptNames(deptIds);

        return records.stream().map(s -> {
            ScheduleSlotStat stat = statMap.get(s.getId());
            long remain = stat != null ? stat.getRemainTotal() : 0;
            long sold = stat != null ? stat.getSoldTotal() : 0;
            long locked = stat != null ? stat.getLockedTotal() : 0;
            return ScheduleListVO.builder()
                    .id(s.getId())
                    .doctorId(s.getDoctorId())
                    .doctorName(doctorNames.get(s.getDoctorId()))
                    .deptId(s.getDeptId())
                    .deptName(deptNames.get(s.getDeptId()))
                    .scheduleDate(s.getScheduleDate())
                    .shift(s.getShift())
                    .totalSlots(s.getTotalSlots())
                    .bookedCount(sold + locked)
                    .remainCount(remain)
                    .lockedCount(locked)
                    .status(s.getStatus())
                    .publishedAt(s.getPublishedAt())
                    .build();
        }).toList();
    }

    /**
     * 查询排班（校验存在 + 属于当前用户数据权限范围），供只读接口使用。
     *
     * <p>校验链：医院 → 科室（DEPT_HEAD）→ 医生（DOCTOR），任一不通过则视为越权，
     * 统一抛"排班不存在"避免暴露资源存在性。
     *
     * @param id 排班 ID
     * @return 排班实体
     * @throws BusinessException 不存在 / 跨院 / 越权时抛出
     */
    private Schedule getScheduleInScope(Long id) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null || schedule.getDeletedAt() != null) {
            throw new BusinessException(ErrorCodeEnum.INVALID_USER_INPUT, "排班不存在");
        }
        DataScope scope = currentUserService.getCurrentDataScope();
        Doctor doctor = doctorMapper.selectById(schedule.getDoctorId());
        Long hospitalId = doctor != null && doctor.getDeletedAt() == null ? doctor.getHospitalId() : null;
        if (!Objects.equals(scope.hospitalId(), hospitalId)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_USER_INPUT, "排班不存在");
        }
        if (BRoleEnum.DEPT_HEAD.equalsCode(scope.role()) && !Objects.equals(scope.deptId(), schedule.getDeptId())) {
            throw new BusinessException(ErrorCodeEnum.INVALID_USER_INPUT, "排班不存在");
        }
        if (BRoleEnum.DOCTOR.equalsCode(scope.role()) && !Objects.equals(scope.doctorId(), schedule.getDoctorId())) {
            throw new BusinessException(ErrorCodeEnum.INVALID_USER_INPUT, "排班不存在");
        }
        return schedule;
    }

    /**
     * 查询排班（校验存在 + 属于指定医院），供 ADMIN 写操作使用。
     *
     * @param id         排班 ID
     * @param hospitalId 当前用户所属医院（ADMIN 来源）
     * @return 排班实体
     * @throws BusinessException 不存在 / 跨院时抛出
     */
    private Schedule getSchedule(Long id, Long hospitalId) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null || schedule.getDeletedAt() != null) {
            throw new BusinessException(ErrorCodeEnum.INVALID_USER_INPUT, "排班不存在");
        }
        Doctor doctor = doctorMapper.selectById(schedule.getDoctorId());
        if (doctor == null || doctor.getDeletedAt() != null || !doctor.getHospitalId().equals(hospitalId)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_USER_INPUT, "排班不存在");
        }
        return schedule;
    }

    /**
     * 按时段号源数生成 AVAILABLE 号源快照（幂等：已有有效快照的时段跳过）。
     *
     * <p>号源池以 slot_snapshot 的 AVAILABLE 记录为准，C 端可约数与锁号均基于
     * AVAILABLE 快照计数，故发布排班时必须同步生成快照，否则号源池为空、
     * 患者无法预约。单条 SQL 按 generate_series 批量插入，避免逐行循环。
     *
     * @param slots 待生成快照的时段列表
     */
    private void generateSlotSnapshots(List<Slot> slots) {
        if (slots.isEmpty()) {
            return;
        }
        List<Long> slotIds = slots.stream().map(Slot::getId).toList();
        // 已存在有效快照的时段跳过，防止重复生成（发布仅草稿态可进入，正常不会触发）
        Set<Long> existSlotIds = slotSnapshotMapper.selectList(Wrappers.<SlotSnapshot>lambdaQuery()
                        .in(SlotSnapshot::getSlotId, slotIds)
                        .isNull(SlotSnapshot::getDeletedAt))
                .stream()
                .map(SlotSnapshot::getSlotId)
                .collect(Collectors.toSet());
        for (Slot slot : slots) {
            if (!existSlotIds.contains(slot.getId())) {
                slotSnapshotMapper.generateAvailableSnapshots(slot.getId(), slot.getTotalCount());
            }
        }
    }

    /**
     * 取消发布时释放 LOCKED 快照为 AVAILABLE，并对所属时段 Redis INCR 归还。
     *
     * @param slots 排班下全部有效时段（用于定位快照与归还 Redis 号源）
     */
    private void releaseLockedSnapshots(List<Slot> slots) {
        if (slots.isEmpty()) {
            return;
        }
        List<Long> slotIds = slots.stream().map(Slot::getId).toList();
        List<SlotSnapshot> locked = slotSnapshotMapper.selectList(Wrappers.<SlotSnapshot>lambdaQuery()
                .in(SlotSnapshot::getSlotId, slotIds)
                .eq(SlotSnapshot::getStatus, SNAP_LOCKED)
                .isNull(SlotSnapshot::getDeletedAt));
        if (locked.isEmpty()) {
            return;
        }
        List<Long> snapshotIds = locked.stream().map(SlotSnapshot::getId).toList();
        slotSnapshotMapper.update(null, Wrappers.<SlotSnapshot>lambdaUpdate()
                .set(SlotSnapshot::getStatus, SNAP_AVAILABLE)
                .set(SlotSnapshot::getUpdatedAt, OffsetDateTime.now())
                .in(SlotSnapshot::getId, snapshotIds)
                .eq(SlotSnapshot::getStatus, SNAP_LOCKED)
                .isNull(SlotSnapshot::getDeletedAt));
        // 归还 remain_count：被释放快照所属时段去重后逐个 INCR
        locked.stream().map(SlotSnapshot::getSlotId).distinct()
                .forEach(slotId -> redisTemplate.opsForValue().increment(redisKey(slotId)));
        log.info("取消发布：释放 LOCKED 快照 {} 个", locked.size());
    }

    /**
     * 批量删除排班下所有时段的 Redis 号源缓存（取消发布后清理防残留误读）。
     *
     * @param slots 排班下全部有效时段
     */
    private void deleteSlotCache(List<Slot> slots) {
        if (slots.isEmpty()) {
            return;
        }
        List<String> keys = slots.stream().map(s -> redisKey(s.getId())).toList();
        redisTemplate.delete(keys);
    }

    /**
     * 校验号源快照所属排班属于指定医院（经 slot → schedule → doctor）。
     *
     * <p>用于手动释放时防越权释放他院号源；任一节点缺失或医院不符即视为号源不存在。
     *
     * @param snapshot   号源快照
     * @param hospitalId 当前用户所属医院
     * @throws BusinessException 越权时抛出
     */
    private void ensureSnapshotInHospital(SlotSnapshot snapshot, Long hospitalId) {
        Slot slot = slotMapper.selectById(snapshot.getSlotId());
        Schedule schedule = slot == null ? null : scheduleMapper.selectById(slot.getScheduleId());
        Doctor doctor = schedule == null ? null : doctorMapper.selectById(schedule.getDoctorId());
        if (doctor == null || doctor.getDeletedAt() != null || !doctor.getHospitalId().equals(hospitalId)) {
            throw new BusinessException(ErrorCodeEnum.INVALID_USER_INPUT, "号源不存在");
        }
    }

    /**
     * 查询排班下全部有效时段（按开始时间升序）。
     *
     * @param scheduleId 排班 ID
     * @return 时段列表（可能为空）
     */
    private List<Slot> listSlots(Long scheduleId) {
        return slotMapper.selectList(Wrappers.<Slot>lambdaQuery()
                .eq(Slot::getScheduleId, scheduleId)
                .isNull(Slot::getDeletedAt)
                .orderByAsc(Slot::getStartTime));
    }

    /**
     * 批量加载医生姓名（仅有效医生），用于排班列表 N+1 优化。
     *
     * @param ids 医生 ID 集合（可能为空）
     * @return 医生 ID → 姓名 映射（缺失键返回 {@code null}）
     */
    private Map<Long, String> loadDoctorNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return doctorMapper.selectBatchIds(ids).stream()
                .filter(d -> d.getDeletedAt() == null)
                .collect(Collectors.toMap(Doctor::getId, Doctor::getName, (a, b) -> a));
    }

    /**
     * 批量加载科室名称（仅有效科室），用于排班列表 N+1 优化。
     *
     * @param ids 科室 ID 集合（可能为空）
     * @return 科室 ID → 名称 映射（缺失键返回 {@code null}）
     */
    private Map<Long, String> loadDeptNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return departmentMapper.selectBatchIds(ids).stream()
                .filter(d -> d.getDeletedAt() == null)
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));
    }

    /**
     * 就诊人姓名脱敏：保留首字符，其余以 * 掩码（如 张**）；空值 / 单字统一回退。
     *
     * @param name 原始姓名（可空）
     * @return 脱敏后姓名
     */
    private String maskName(String name) {
        if (name == null || name.isBlank()) {
            return NAME_MASK_PLACEHOLDER;
        }
        if (name.length() == 1) {
            return name + NAME_MASK_SUFFIX;
        }
        return name.charAt(0) + NAME_MASK_SUFFIX;
    }

    /**
     * 拼接号源 Redis 缓存 Key（与 C 端约定 {@code cend:slot:remain:{slotId}}）。
     *
     * @param slotId 时段 ID
     * @return Redis Key 字符串
     */
    private String redisKey(Long slotId) {
        return String.format(SLOT_REMAIN_KEY, slotId);
    }
}
