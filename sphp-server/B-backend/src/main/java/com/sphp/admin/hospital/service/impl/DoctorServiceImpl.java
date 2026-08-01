package com.sphp.admin.hospital.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.auth.entity.BUser;
import com.sphp.admin.auth.entity.Doctor;
import com.sphp.admin.auth.mapper.BUserMapper;
import com.sphp.admin.auth.mapper.DoctorMapper;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.hospital.dto.DoctorAccountRequest;
import com.sphp.admin.hospital.dto.DoctorCreateRequest;
import com.sphp.admin.hospital.dto.DoctorPasswordRequest;
import com.sphp.admin.hospital.dto.DoctorStatusRequest;
import com.sphp.admin.hospital.dto.DoctorUpdateRequest;
import com.sphp.admin.hospital.entity.Department;
import com.sphp.admin.hospital.mapper.DepartmentMapper;
import com.sphp.admin.hospital.service.DoctorService;
import com.sphp.admin.hospital.vo.DoctorListVO;
import com.sphp.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * 医生管理服务实现。
 *
 * <p>全部操作按当前登录管理员所属医院（hospital_id）做数据隔离过滤；
 * 医生状态与关联 b_user 账号状态保持联动（以 b_user.status 为权威来源）。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DoctorServiceImpl implements DoctorService {

    private final DoctorMapper doctorMapper;
    private final BUserMapper bUserMapper;
    private final DepartmentMapper departmentMapper;
    private final CurrentUserService currentUserService;

    @Override
    public PageResult<DoctorListVO> page(Long deptId, String name, String status, int page, int size) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Page<Doctor> result = doctorMapper.selectPage(new Page<>(page, size),
                Wrappers.<Doctor>lambdaQuery()
                        .eq(Doctor::getHospitalId, hospitalId)
                        .eq(deptId != null, Doctor::getDeptId, deptId)
                        .like(StringUtils.hasText(name), Doctor::getName, name)
                        .eq(StringUtils.hasText(status), Doctor::getStatus, status)
                        .isNull(Doctor::getDeletedAt)
                        .orderByDesc(Doctor::getId));

        // 批量加载科室名称与登录账号（仅有效记录）
        Map<Long, String> deptNames = loadDeptNames(result.getRecords());
        Map<Long, BUser> users = loadUsers(result.getRecords());
        List<DoctorListVO> list = result.getRecords().stream()
                .map(d -> {
                    BUser user = d.getBUserId() == null ? null : users.get(d.getBUserId());
                    boolean hasAccount = user != null;
                    return DoctorListVO.builder()
                            .id(d.getId())
                            .name(d.getName())
                            .deptId(d.getDeptId())
                            .deptName(deptNames.get(d.getDeptId()))
                            .title(d.getTitle())
                            .specialty(d.getSpecialty())
                            .licenseNo(d.getLicenseNo())
                            .phone(d.getPhone())
                            .registrationFeeCent(d.getRegistrationFeeCent())
                            .status(d.getStatus())
                            .hasAccount(hasAccount)
                            .account(hasAccount ? user.getAccount() : null)
                            .build();
                })
                .toList();
        return PageResult.of(result.getTotal(), list, page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(DoctorCreateRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        // 1. 校验科室存在且属本院
        Department dept = departmentMapper.selectById(request.getDeptId());
        if (dept == null || dept.getDeletedAt() != null || !dept.getHospitalId().equals(hospitalId)) {
            throw new BusinessException("A0402", "所属科室不存在");
        }
        // 2. 登录账号唯一性（全院唯一）
        if (currentUserService.accountExists(request.getAccount(), null)) {
            throw new BusinessException("A0112", "登录账号已存在");
        }
        // 3. 插入 doctor（b_user_id 先空，循环外键避免约束冲突）
        Doctor doctor = new Doctor();
        doctor.setHospitalId(hospitalId);
        doctor.setDeptId(request.getDeptId());
        doctor.setName(request.getName());
        doctor.setTitle(request.getTitle());
        doctor.setSpecialty(request.getSpecialty());
        doctor.setLicenseNo(request.getLicenseNo());
        doctor.setPhone(request.getPhone());
        doctor.setRegistrationFeeCent(request.getRegistrationFeeCent() != null ? request.getRegistrationFeeCent() : 0);
        doctor.setStatus(StringUtils.hasText(request.getStatus()) ? request.getStatus() : "ENABLED");
        doctorMapper.insert(doctor);
        // 4. 插入 b_user（role=DOCTOR）
        BUser user = new BUser();
        user.setAccount(request.getAccount());
        user.setPasswordHash(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
        user.setRole("DOCTOR");
        user.setHospitalId(hospitalId);
        user.setDoctorId(doctor.getId());
        user.setStatus("ENABLED");
        bUserMapper.insert(user);
        // 5. 回填 doctor.b_user_id（配合 idx_doctor_b_user 快速联查）
        Doctor backfill = new Doctor();
        backfill.setId(doctor.getId());
        backfill.setBUserId(user.getId());
        doctorMapper.updateById(backfill);
        log.info("新增医生 id={}, name={}, account={}", doctor.getId(), doctor.getName(), user.getAccount());
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DoctorUpdateRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Doctor doctor = getDoctor(id, hospitalId);
        // 仅更新传入的非空值（不修改所属科室）
        if (request.getName() != null) {
            doctor.setName(request.getName());
        }
        if (request.getTitle() != null) {
            doctor.setTitle(request.getTitle());
        }
        if (request.getSpecialty() != null) {
            doctor.setSpecialty(request.getSpecialty());
        }
        if (request.getIntroduction() != null) {
            doctor.setIntroduction(request.getIntroduction());
        }
        if (request.getPhone() != null) {
            doctor.setPhone(request.getPhone());
        }
        if (request.getRegistrationFeeCent() != null) {
            doctor.setRegistrationFeeCent(request.getRegistrationFeeCent());
        }
        doctor.setUpdatedAt(OffsetDateTime.now());
        doctorMapper.updateById(doctor);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateStatus(Long id, DoctorStatusRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Doctor doctor = getDoctor(id, hospitalId);
        String status = request.getStatus();
        // 停用/暂停前置校验：无 PUBLISHED 排班、无 IN_PROGRESS 问诊
        if (!"ENABLED".equals(status)) {
            if (doctorMapper.countPublishedScheduleByDoctor(doctor.getId()) > 0) {
                throw new BusinessException("A0443", "存在已发布排班，无法停用");
            }
            if (doctorMapper.countInProgressConsultByDoctor(doctor.getId()) > 0) {
                throw new BusinessException("A0443", "存在进行中问诊，无法停用");
            }
        }
        // 更新医生状态
        doctor.setStatus(status);
        doctor.setUpdatedAt(OffsetDateTime.now());
        doctorMapper.updateById(doctor);
        // 账号联动：关联 b_user 状态同步（b_user 无 SUSPENDED，映射为 DISABLED）
        if (doctor.getBUserId() != null) {
            String userStatus = "SUSPENDED".equals(status) ? "DISABLED" : status;
            BUser user = new BUser();
            user.setId(doctor.getBUserId());
            user.setStatus(userStatus);
            user.setUpdatedAt(OffsetDateTime.now());
            bUserMapper.updateById(user);
        }
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void updateAccount(Long id, DoctorAccountRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Doctor doctor = getDoctor(id, hospitalId);
        if (doctor.getBUserId() == null) {
            throw new BusinessException("A0121", "该医生未开通登录账号");
        }
        if (currentUserService.accountExists(request.getAccount(), doctor.getBUserId())) {
            throw new BusinessException("A0112", "登录账号已存在");
        }
        BUser user = new BUser();
        user.setId(doctor.getBUserId());
        user.setAccount(request.getAccount());
        user.setUpdatedAt(OffsetDateTime.now());
        bUserMapper.updateById(user);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void resetPassword(Long id, DoctorPasswordRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Doctor doctor = getDoctor(id, hospitalId);
        if (doctor.getBUserId() == null) {
            throw new BusinessException("A0121", "该医生未开通登录账号");
        }
        BUser user = new BUser();
        user.setId(doctor.getBUserId());
        user.setPasswordHash(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
        user.setUpdatedAt(OffsetDateTime.now());
        bUserMapper.updateById(user);
        log.info("重置医生密码 doctorId={}", doctor.getId());
    }

    /** 按 id + 医院范围查询医生，不存在或越权返回 A0402 */
    private Doctor getDoctor(Long id, Long hospitalId) {
        Doctor doctor = doctorMapper.selectById(id);
        if (doctor == null || doctor.getDeletedAt() != null || !doctor.getHospitalId().equals(hospitalId)) {
            throw new BusinessException("A0402", "医生不存在");
        }
        return doctor;
    }

    /** 批量加载科室名称（仅有效科室） */
    private Map<Long, String> loadDeptNames(List<Doctor> doctors) {
        List<Long> ids = doctors.stream()
                .map(Doctor::getDeptId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return departmentMapper.selectBatchIds(ids).stream()
                .filter(d -> d.getDeletedAt() == null)
                .collect(Collectors.toMap(Department::getId, Department::getName, (a, b) -> a));
    }

    /** 批量加载登录账号（仅有效 b_user） */
    private Map<Long, BUser> loadUsers(List<Doctor> doctors) {
        List<Long> ids = doctors.stream()
                .map(Doctor::getBUserId)
                .filter(Objects::nonNull)
                .distinct()
                .toList();
        if (ids.isEmpty()) {
            return Map.of();
        }
        return bUserMapper.selectBatchIds(ids).stream()
                .filter(u -> u.getDeletedAt() == null)
                .collect(Collectors.toMap(BUser::getId, u -> u, (a, b) -> a));
    }
}
