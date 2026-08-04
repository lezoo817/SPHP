package com.sphp.admin.hospital.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.auth.entity.Doctor;
import com.sphp.admin.auth.mapper.DoctorMapper;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.hospital.dto.DepartmentCreateRequest;
import com.sphp.admin.hospital.dto.DepartmentStatusRequest;
import com.sphp.admin.hospital.dto.DepartmentUpdateRequest;
import com.sphp.admin.hospital.entity.Department;
import com.sphp.admin.hospital.mapper.DepartmentMapper;
import com.sphp.admin.hospital.service.DepartmentService;
import com.sphp.admin.hospital.vo.DepartmentDetailVO;
import com.sphp.admin.hospital.vo.DepartmentListVO;
import com.sphp.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 科室管理服务实现。
 *
 * <p>全部操作按当前登录管理员所属医院（hospital_id）做数据隔离过滤。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DepartmentServiceImpl implements DepartmentService {

    private final DepartmentMapper departmentMapper;
    private final DoctorMapper doctorMapper;
    private final CurrentUserService currentUserService;

    @Override
    public PageResult<DepartmentListVO> page(String name, String headDoctorName, String status, int page, int size) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Page<Department> result = departmentMapper.selectPage(new Page<>(page, size),
                Wrappers.<Department>lambdaQuery()
                        .eq(Department::getHospitalId, hospitalId)
                        .like(StringUtils.hasText(name), Department::getName, name)
                        .eq(StringUtils.hasText(status), Department::getStatus, status)
                        .apply(StringUtils.hasText(headDoctorName),
                                "head_doctor_id IN (SELECT id FROM doctor WHERE name LIKE CONCAT('%', {0}::text, '%') AND deleted_at IS NULL)",
                                headDoctorName)
                        .isNull(Department::getDeletedAt)
                        .orderByDesc(Department::getId));

        // 批量加载科室负责人姓名（去重）
        Map<Long, String> doctorNames = loadDoctorNames(result.getRecords());
        List<DepartmentListVO> list = result.getRecords().stream()
                .map(d -> DepartmentListVO.builder()
                        .id(d.getId())
                        .name(d.getName())
                        .hospitalId(d.getHospitalId())
                        .headDoctorId(d.getHeadDoctorId())
                        .headDoctorName(d.getHeadDoctorId() == null ? null : doctorNames.get(d.getHeadDoctorId()))
                        .location(d.getLocation())
                        .status(d.getStatus())
                        .build())
                .toList();
        return PageResult.of(result.getTotal(), list, page, size);
    }

    @Override
    public DepartmentDetailVO detail(Long id) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Department dept = getDepartment(id, hospitalId);

        // 科室下医生数量（含停用/暂停，不含软删）
        long doctorCount = doctorMapper.selectCount(Wrappers.<Doctor>lambdaQuery()
                .eq(Doctor::getDeptId, dept.getId())
                .isNull(Doctor::getDeletedAt));

        // 科室负责人姓名
        String headDoctorName = null;
        if (dept.getHeadDoctorId() != null) {
            Doctor head = doctorMapper.selectById(dept.getHeadDoctorId());
            headDoctorName = (head != null && head.getDeletedAt() == null) ? head.getName() : null;
        }

        return DepartmentDetailVO.builder()
                .id(dept.getId())
                .name(dept.getName())
                .hospitalId(dept.getHospitalId())
                .headDoctorId(dept.getHeadDoctorId())
                .headDoctorName(headDoctorName)
                .location(dept.getLocation())
                .status(dept.getStatus())
                .doctorCount(doctorCount)
                .createdAt(dept.getCreatedAt())
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(DepartmentCreateRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();

        // 先插入科室（不含负责人，避免循环依赖）
        Department dept = new Department();
        dept.setHospitalId(hospitalId);
        dept.setName(request.getName());
        dept.setLocation(request.getLocation());
        dept.setStatus("ENABLED");
        departmentMapper.insert(dept);

        // 若指定负责人，必须存在、属本院且属于本科室
        if (request.getHeadDoctorId() != null) {
            ensureDoctorBelongsToDept(request.getHeadDoctorId(), hospitalId, dept.getId());
            dept.setHeadDoctorId(request.getHeadDoctorId());
            dept.setUpdatedAt(OffsetDateTime.now());
            departmentMapper.updateById(dept);
        }

        log.info("新增科室 name={}, hospitalId={}", dept.getName(), hospitalId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DepartmentUpdateRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Department dept = getDepartment(id, hospitalId);
        // 负责人医生（若指定）必须存在、属本院且属于本科室
        if (request.getHeadDoctorId() != null) {
            ensureDoctorBelongsToDept(request.getHeadDoctorId(), hospitalId, dept.getId());
        }
        dept.setName(request.getName());
        dept.setHeadDoctorId(request.getHeadDoctorId());
        dept.setLocation(request.getLocation());
        dept.setUpdatedAt(OffsetDateTime.now());
        departmentMapper.updateById(dept);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, DepartmentStatusRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Department dept = getDepartment(id, hospitalId);
        String status = request.getStatus();
        // 停用前置校验：无启用医生 / 已发布排班 / 进行中问诊
        if ("DISABLED".equals(status)) {
            assertNoEnabledDoctor(dept.getId());
            assertNoPublishedSchedule(dept.getId());
            assertNoInProgressConsult(dept.getId());
            // 同步停用该科室下所有医生（状态改为 DISABLED）
            doctorMapper.update(null,
                    Wrappers.<Doctor>lambdaUpdate()
                            .set(Doctor::getStatus, "DISABLED")
                            .set(Doctor::getUpdatedAt, OffsetDateTime.now())
                            .eq(Doctor::getDeptId, dept.getId())
                            .isNull(Doctor::getDeletedAt));
        }
        dept.setStatus(status);
        dept.setUpdatedAt(OffsetDateTime.now());
        departmentMapper.updateById(dept);
    }

    /** 按 id + 医院范围查询科室，不存在或越权返回 A0402 */
    private Department getDepartment(Long id, Long hospitalId) {
        Department dept = departmentMapper.selectById(id);
        if (dept == null || dept.getDeletedAt() != null || !dept.getHospitalId().equals(hospitalId)) {
            throw new BusinessException("A0402", "科室不存在");
        }
        return dept;
    }

    /** 校验医生存在、属于指定医院且属于指定科室 */
    private void ensureDoctorBelongsToDept(Long doctorId, Long hospitalId, Long deptId) {
        Doctor doctor = doctorMapper.selectById(doctorId);
        if (doctor == null || doctor.getDeletedAt() != null || !doctor.getHospitalId().equals(hospitalId)) {
            throw new BusinessException("A0402", "科室负责人医生不存在或不属于本院");
        }
        if (!deptId.equals(doctor.getDeptId())) {
            throw new BusinessException("A0402", "科室负责人必须是本科室的医生");
        }
    }

    /** 停用前置校验 0：科室下无 ENABLED 医生（否则 4001） */
    private void assertNoEnabledDoctor(Long deptId) {
        if (departmentMapper.countEnabledDoctorByDept(deptId) > 0) {
            throw new BusinessException("4001", "科室下存在启用医生，无法停用");
        }
    }

    /** 停用前置校验 1：科室下无 PUBLISHED 排班（否则 4002） */
    private void assertNoPublishedSchedule(Long deptId) {
        if (departmentMapper.countPublishedScheduleByDept(deptId) > 0) {
            throw new BusinessException("4002", "科室下存在已发布排班，无法停用");
        }
    }

    /** 停用前置校验 3：科室下无 IN_PROGRESS 问诊（否则 4003） */
    private void assertNoInProgressConsult(Long deptId) {
        if (departmentMapper.countInProgressConsultByDept(deptId) > 0) {
            throw new BusinessException("4003", "科室下存在进行中问诊，无法停用");
        }
    }

    /** 批量加载负责人医生姓名（仅有效医生） */
    private Map<Long, String> loadDoctorNames(List<Department> departments) {
        List<Long> ids = departments.stream()
                .map(Department::getHeadDoctorId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return doctorMapper.selectBatchIds(ids).stream()
                .filter(d -> d.getDeletedAt() == null)
                .collect(Collectors.toMap(Doctor::getId, Doctor::getName, (a, b) -> a));
    }
}
