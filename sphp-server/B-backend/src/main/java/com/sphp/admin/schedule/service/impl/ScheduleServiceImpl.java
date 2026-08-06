package com.sphp.admin.schedule.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.auth.entity.Doctor;
import com.sphp.admin.auth.mapper.DoctorMapper;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.DataScope;
import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.hospital.entity.Department;
import com.sphp.admin.hospital.mapper.DepartmentMapper;
import com.sphp.admin.schedule.dto.LockedSlotRow;
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
import com.sphp.admin.schedule.vo.ForceReleaseVO;
import com.sphp.admin.schedule.vo.LockedSlotVO;
import com.sphp.admin.schedule.vo.ScheduleCreateVO;
import com.sphp.admin.schedule.vo.ScheduleListVO;
import com.sphp.admin.schedule.vo.SchedulePublishVO;
import com.sphp.admin.schedule.vo.SlotConfigVO;
import com.sphp.admin.schedule.vo.SourcePoolVO;
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
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 排班与号源管理服务实现（系分 §5.4）。
 *
 * <p>数据权限遵循 §7.2：查询按当前用户角色显式过滤；
 * 写操作经 {@link CurrentUserService#getCurrentHospitalId()} 强制 ADMIN。
 * 号源缓存 Key 格式 {@code slot:remain:{slotId}}（§4.2.3）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ScheduleServiceImpl implements ScheduleService {

    private static final String ROLE_ADMIN = "ADMIN";
    private static final String ROLE_DEPT_HEAD = "DEPT_HEAD";
    private static final String ROLE_DOCTOR = "DOCTOR";

    /** 排班状态 */
    private static final String STATUS_DRAFT = "DRAFT";
    private static final String STATUS_PUBLISHED = "PUBLISHED";
    private static final String STATUS_CANCELLED = "CANCELLED";

    /** 号源快照状态 */
    private static final String SNAP_LOCKED = "LOCKED";
    private static final String SNAP_AVAILABLE = "AVAILABLE";
    private static final String SNAP_RELEASED = "RELEASED";

    /** 号源缓存 Key 前缀（系分 §4.2.3） */
    private static final String SLOT_REMAIN_KEY = "slot:remain:%d";

    /** 锁定号源展示的过期时长（分钟），与任务契约一致 */
    private static final int LOCK_EXPIRE_MINUTES = 15;

    private final ScheduleMapper scheduleMapper;
    private final SlotMapper slotMapper;
    private final SlotSnapshotMapper slotSnapshotMapper;
    private final DoctorMapper doctorMapper;
    private final DepartmentMapper departmentMapper;
    private final CurrentUserService currentUserService;
    private final StringRedisTemplate redisTemplate;

    @Override
    public PageResult<ScheduleListVO> page(LocalDate date, Long deptId, Long doctorId, String status, int page, int size) {
        DataScope scope = currentUserService.getCurrentDataScope();
        // 数据权限标识缺失（DEPT_HEAD 无科室 / DOCTOR 无本人医生）时按空数据返回，避免越权
        if ((ROLE_DEPT_HEAD.equals(scope.role()) && scope.deptId() == null)
                || (ROLE_DOCTOR.equals(scope.role()) && scope.doctorId() == null)) {
            return PageResult.of(0, List.of(), page, size);
        }

        LambdaQueryWrapper<Schedule> wrapper = Wrappers.<Schedule>lambdaQuery()
                // 不传日期时默认加载全部排班（不再兜底为今天）
                .eq(date != null, Schedule::getScheduleDate, date)
                .isNull(Schedule::getDeletedAt)
                // schedule 表无 hospital_id，医院范围经 doctor.hospital_id 关联过滤
                .apply("doctor_id IN (SELECT id FROM doctor WHERE hospital_id = {0} AND deleted_at IS NULL)",
                        scope.hospitalId());
        if (ROLE_ADMIN.equals(scope.role())) {
            wrapper.eq(deptId != null, Schedule::getDeptId, deptId)
                    .eq(doctorId != null, Schedule::getDoctorId, doctorId);
        } else if (ROLE_DEPT_HEAD.equals(scope.role())) {
            wrapper.eq(Schedule::getDeptId, scope.deptId());
        } else {
            wrapper.eq(Schedule::getDoctorId, scope.doctorId());
        }
        wrapper.eq(StringUtils.hasText(status), Schedule::getStatus, status)
                // 按排班日期倒序展示，最近的排班在最前
                .orderByDesc(Schedule::getScheduleDate)
                .orderByAsc(Schedule::getId);

        Page<Schedule> result = scheduleMapper.selectPage(new Page<>(page, size), wrapper);
        return PageResult.of(result.getTotal(), buildListVO(result.getRecords()), page, size);
    }

    @Override
    public PageResult<SourcePoolVO> sourcePool(LocalDate startDate, LocalDate endDate, Long deptId, Long doctorId, int page, int size) {
        // 默认区间：近 7 天（含今天）；显式区间需满足 start <= end
        LocalDate start = startDate != null ? startDate : LocalDate.now().minusDays(6);
        LocalDate end = endDate != null ? endDate : LocalDate.now();
        if (start.isAfter(end)) {
            throw new BusinessException("A0400", "日期范围无效，开始日期不能晚于结束日期");
        }
        DataScope scope = currentUserService.getCurrentDataScope();
        // 数据权限标识缺失（DEPT_HEAD 无科室 / DOCTOR 无本人医生）时按空数据返回，避免越权
        if ((ROLE_DEPT_HEAD.equals(scope.role()) && scope.deptId() == null)
                || (ROLE_DOCTOR.equals(scope.role()) && scope.doctorId() == null)) {
            return PageResult.of(0, List.of(), page, size);
        }
        // ADMIN 的 deptId/doctorId 为用户筛选条件；DEPT_HEAD / DOCTOR 强制数据权限范围
        Long filterDeptId = ROLE_ADMIN.equals(scope.role()) ? deptId : null;
        Long filterDoctorId = ROLE_ADMIN.equals(scope.role()) ? doctorId : null;
        Long scopeDeptId = ROLE_DEPT_HEAD.equals(scope.role()) ? scope.deptId() : null;
        Long scopeDoctorId = ROLE_DOCTOR.equals(scope.role()) ? scope.doctorId() : null;
        IPage<SourcePoolVO> result = slotMapper.selectSourcePoolPage(
                new Page<>(page, size), start, end, scope.hospitalId(),
                filterDeptId, filterDoctorId, scopeDeptId, scopeDoctorId);
        return PageResult.of(result.getTotal(), result.getRecords(), page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ScheduleCreateVO create(ScheduleCreateRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Doctor doctor = doctorMapper.selectById(request.getDoctorId());
        if (doctor == null || doctor.getDeletedAt() != null || !doctor.getHospitalId().equals(hospitalId)) {
            throw new BusinessException("A0402", "医生不存在或不属于本院");
        }
        if (!"ENABLED".equals(doctor.getStatus())) {
            throw new BusinessException("A0443", "医生当前状态不可排班");
        }
        LocalDate scheduleDate = LocalDate.parse(request.getScheduleDate());
        if (scheduleDate.isBefore(LocalDate.now())) {
            throw new BusinessException("A0400", "排班日期不能早于今天");
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
                throw new BusinessException("A0443", "该医生当天该班次已存在排班");
            }
        }
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
            return ScheduleCreateVO.builder()
                    .id(cancelled.getId())
                    .status(STATUS_DRAFT)
                    .createdAt(cancelled.getCreatedAt())
                    .build();
        }

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

        // DB 默认值不会回填实体，回查一次获取 created_at
        Schedule created = scheduleMapper.selectById(schedule.getId());
        return ScheduleCreateVO.builder()
                .id(schedule.getId())
                .status(STATUS_DRAFT)
                .createdAt(created != null ? created.getCreatedAt() : OffsetDateTime.now())
                .build();
    }

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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void configureSlots(Long id, SlotConfigRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Schedule schedule = getSchedule(id, hospitalId);
        if (!STATUS_DRAFT.equals(schedule.getStatus())) {
            throw new BusinessException("A0443", "仅草稿状态可配置号源时段");
        }
        List<SlotConfigRequest.SlotConfigItem> items = request.getSlotConfigs();
        List<Slot> slots = new ArrayList<>(items.size());
        int sum = 0;
        for (SlotConfigRequest.SlotConfigItem item : items) {
            LocalTime start = LocalTime.parse(item.getStartTime());
            LocalTime end = LocalTime.parse(item.getEndTime());
            if (!end.isAfter(start)) {
                throw new BusinessException("A0400", "时段结束时间必须晚于开始时间：" + item.getStartTime());
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
        // 时段号源数之和不超过排班总号源数（余量留作机动，不强制相等，§5.4.4）
        if (sum > schedule.getTotalSlots()) {
            throw new BusinessException("A0400",
                    "时段号源数之和(" + sum + ")不能超过排班总号源数(" + schedule.getTotalSlots() + ")");
        }
        // 时段不得重叠（按开始时间排序，相邻区间交叉即重叠）
        List<Slot> sorted = slots.stream().sorted(Comparator.comparing(Slot::getStartTime)).toList();
        for (int i = 1; i < sorted.size(); i++) {
            if (sorted.get(i - 1).getEndTime().isAfter(sorted.get(i).getStartTime())) {
                throw new BusinessException("A0400", "号源时段之间不能重叠");
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchedulePublishVO publish(Long id) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Schedule schedule = getSchedule(id, hospitalId);
        if (!STATUS_DRAFT.equals(schedule.getStatus())) {
            throw new BusinessException("A0443", "仅草稿状态可发布排班");
        }
        List<Slot> slots = listSlots(schedule.getId());
        if (slots.isEmpty()) {
            throw new BusinessException("A0443", "请先配置号源时段再发布");
        }
        // 时段号源数之和须等于排班总号源数方可发布（§5.4.4）
        int slotCountSum = slots.stream().mapToInt(Slot::getTotalCount).sum();
        if (slotCountSum != schedule.getTotalSlots()) {
            throw new BusinessException("A0443",
                    "时段号源数之和(" + slotCountSum + ")须等于排班总号源数(" + schedule.getTotalSlots() + ")，方可发布");
        }

        schedule.setStatus(STATUS_PUBLISHED);
        schedule.setPublishedAt(OffsetDateTime.now());
        schedule.setUpdatedAt(OffsetDateTime.now());
        scheduleMapper.updateById(schedule);

        // 初始化 Redis 号源缓存（幂等覆盖，§4.2.3）
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public SchedulePublishVO unpublish(Long id) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Schedule schedule = getSchedule(id, hospitalId);
        String status = schedule.getStatus();
        if (STATUS_CANCELLED.equals(status)) {
            throw new BusinessException("A0443", "排班已取消，不可重复操作");
        }
        List<Slot> slots = listSlots(schedule.getId());
        if (STATUS_PUBLISHED.equals(status)) {
            // 前置校验：存在 PAID 且 schedule_date >= 今天的有效挂号订单则禁止取消
            if (scheduleMapper.countPaidFutureAppointments(schedule.getId()) > 0) {
                throw new BusinessException("A0443", "该排班存在已支付预约，无法取消发布");
            }
            // 释放 LOCKED 快照为 AVAILABLE，并 Redis INCR 归还 remain_count（§5.4.6）
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

    @Override
    public PageResult<LockedSlotVO> pageLocked(LocalDate date, Long deptId, int page, int size) {
        DataScope scope = currentUserService.getCurrentDataScope();
        if ((ROLE_DEPT_HEAD.equals(scope.role()) && scope.deptId() == null)
                || (ROLE_DOCTOR.equals(scope.role()) && scope.doctorId() == null)) {
            return PageResult.of(0, List.of(), page, size);
        }
        Long scopeDeptId = ROLE_DEPT_HEAD.equals(scope.role()) ? scope.deptId() : null;
        Long scopeDoctorId = ROLE_DOCTOR.equals(scope.role()) ? scope.doctorId() : null;
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

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ForceReleaseVO forceRelease(Long snapshotId) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        SlotSnapshot snapshot = slotSnapshotMapper.selectById(snapshotId);
        if (snapshot == null || snapshot.getDeletedAt() != null) {
            throw new BusinessException("A0402", "号源不存在");
        }
        if (!SNAP_LOCKED.equals(snapshot.getStatus())) {
            throw new BusinessException("A0443", "号源状态非 LOCKED，不可释放");
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

    /** 分页记录组装为列表 VO（批量加载医生/科室名与号源聚合，避免 N+1） */
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

    /** 查询排班（校验存在 + 属于当前用户数据权限范围），供只读接口使用 */
    private Schedule getScheduleInScope(Long id) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null || schedule.getDeletedAt() != null) {
            throw new BusinessException("A0402", "排班不存在");
        }
        DataScope scope = currentUserService.getCurrentDataScope();
        Doctor doctor = doctorMapper.selectById(schedule.getDoctorId());
        Long hospitalId = doctor != null && doctor.getDeletedAt() == null ? doctor.getHospitalId() : null;
        if (!Objects.equals(scope.hospitalId(), hospitalId)) {
            throw new BusinessException("A0402", "排班不存在");
        }
        if (ROLE_DEPT_HEAD.equals(scope.role()) && !Objects.equals(scope.deptId(), schedule.getDeptId())) {
            throw new BusinessException("A0402", "排班不存在");
        }
        if (ROLE_DOCTOR.equals(scope.role()) && !Objects.equals(scope.doctorId(), schedule.getDoctorId())) {
            throw new BusinessException("A0402", "排班不存在");
        }
        return schedule;
    }

    /** 查询排班（校验存在 + 属于指定医院），供 ADMIN 写操作使用 */
    private Schedule getSchedule(Long id, Long hospitalId) {
        Schedule schedule = scheduleMapper.selectById(id);
        if (schedule == null || schedule.getDeletedAt() != null) {
            throw new BusinessException("A0402", "排班不存在");
        }
        Doctor doctor = doctorMapper.selectById(schedule.getDoctorId());
        if (doctor == null || doctor.getDeletedAt() != null || !doctor.getHospitalId().equals(hospitalId)) {
            throw new BusinessException("A0402", "排班不存在");
        }
        return schedule;
    }

    /** 取消发布时释放 LOCKED 快照为 AVAILABLE，并对所属时段 Redis INCR 归还（§5.4.6） */
    /**
     * 按时段号源数生成 AVAILABLE 号源快照（幂等：已有有效快照的时段跳过）。
     *
     * <p>号源池以 slot_snapshot 的 AVAILABLE 记录为准，C 端可约数与锁号均基于
     * AVAILABLE 快照计数（§4.2.3），故发布排班时必须同步生成快照，否则号源池为空、
     * 患者无法预约。单条 SQL 按 generate_series 批量插入，避免逐行循环。
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

    /** 批量删除排班下所有时段的 Redis 号源缓存（§5.4.6 缓存清理） */
    private void deleteSlotCache(List<Slot> slots) {
        if (slots.isEmpty()) {
            return;
        }
        List<String> keys = slots.stream().map(s -> redisKey(s.getId())).toList();
        redisTemplate.delete(keys);
    }

    /** 校验号源快照所属排班属于指定医院（经 slot → schedule → doctor） */
    private void ensureSnapshotInHospital(SlotSnapshot snapshot, Long hospitalId) {
        Slot slot = slotMapper.selectById(snapshot.getSlotId());
        Schedule schedule = slot == null ? null : scheduleMapper.selectById(slot.getScheduleId());
        Doctor doctor = schedule == null ? null : doctorMapper.selectById(schedule.getDoctorId());
        if (doctor == null || doctor.getDeletedAt() != null || !doctor.getHospitalId().equals(hospitalId)) {
            throw new BusinessException("A0402", "号源不存在");
        }
    }

    private List<Slot> listSlots(Long scheduleId) {
        return slotMapper.selectList(Wrappers.<Slot>lambdaQuery()
                .eq(Slot::getScheduleId, scheduleId)
                .isNull(Slot::getDeletedAt)
                .orderByAsc(Slot::getStartTime));
    }

    /** 批量加载医生姓名（仅有效医生） */
    private Map<Long, String> loadDoctorNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return doctorMapper.selectBatchIds(ids).stream()
                .filter(d -> d.getDeletedAt() == null)
                .collect(Collectors.toMap(Doctor::getId, Doctor::getName, (a, b) -> a));
    }

    /** 批量加载科室名称（仅有效科室） */
    private Map<Long, String> loadDeptNames(Collection<Long> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return departmentMapper.selectBatchIds(ids).stream()
                .filter(d -> d.getDeletedAt() == null)
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));
    }

    /** 就诊人姓名脱敏：保留首字符，其余以 * 掩码（如 张**） */
    private String maskName(String name) {
        if (name == null || name.isBlank()) {
            return "**";
        }
        if (name.length() == 1) {
            return name + "**";
        }
        return name.charAt(0) + "**";
    }

    private String redisKey(Long slotId) {
        return String.format(SLOT_REMAIN_KEY, slotId);
    }
}
