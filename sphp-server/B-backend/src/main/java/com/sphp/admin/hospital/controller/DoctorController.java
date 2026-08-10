package com.sphp.admin.hospital.controller;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.hospital.dto.DoctorAccountRequest;
import com.sphp.admin.hospital.dto.DoctorCreateRequest;
import com.sphp.admin.hospital.dto.DoctorPasswordRequest;
import com.sphp.admin.hospital.dto.DoctorStatusRequest;
import com.sphp.admin.hospital.dto.DoctorUpdateRequest;
import com.sphp.admin.hospital.service.DoctorService;
import com.sphp.admin.hospital.vo.DoctorListVO;
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

/**
 * 医生管理接口（管理员视角）。
 *
 * <p>外部完整 URL 前缀为 {@code /api/b/admin/doctors}。所有接口按当前登录管理员
 * 所属医院（{@code hospital_id}）做数据隔离；医生状态与关联 {@code b_user} 状态联动
 * （以 {@code b_user.status} 为权威来源）。
 */
@RestController
@RequestMapping("/b/admin/doctors")
@Tag(name = "医生管理", description = "医生列表/新增/编辑/启停/改账号/重置密码（管理员）")
@RequiredArgsConstructor
public class DoctorController {

    /** 每页大小上限，防止超大数据量查询 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 每页大小钳制到 [1, MAX_PAGE_SIZE] */
    private static int clampSize(int size) {
        return Math.clamp(size, 1, MAX_PAGE_SIZE);
    }

    private final DoctorService doctorService;

    @GetMapping
    @Operation(summary = "查询医生列表", description = "分页查询医生（按当前管理员所属医院过滤，含科室名与登录账号）")
    public Result<PageResult<DoctorListVO>> page(
            @Parameter(description = "科室过滤") @RequestParam(required = false) Long deptId,
            @Parameter(description = "姓名模糊检索") @RequestParam(required = false) String name,
            @Parameter(description = "状态过滤：ENABLED / DISABLED / SUSPENDED") @RequestParam(required = false) String status,
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") int size) {
        return Result.success("查询成功", doctorService.page(deptId, name, status, page, clampSize(size)));
    }

    @PostMapping
    @Operation(summary = "新增医生", description = "同一事务开通 DOCTOR 登录账号并回填 doctor.b_user_id")
    public Result<Void> create(@Valid @RequestBody DoctorCreateRequest request) {
        doctorService.create(request);
        return Result.success("新增成功", null);
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑医生", description = "编辑姓名/职称/擅长领域/简介/电话/挂号费（不修改所属科室）")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DoctorUpdateRequest request) {
        doctorService.update(id, request);
        return Result.success("编辑成功", null);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "启用/停用/暂停医生", description = "停用校验无已发布排班/进行中问诊；关联 b_user 状态同步联动")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody DoctorStatusRequest request) {
        doctorService.updateStatus(id, request);
        return Result.success("操作成功", null);
    }

    @PutMapping("/{id}/account")
    @Operation(summary = "修改医生登录账号", description = "无关联登录账号返回 A0121；账号重复返回 A0112")
    public Result<Void> updateAccount(@PathVariable Long id, @Valid @RequestBody DoctorAccountRequest request) {
        doctorService.updateAccount(id, request);
        return Result.success("修改成功", null);
    }

    @PutMapping("/{id}/password")
    @Operation(summary = "重置医生登录密码", description = "管理员直接重置，不校验旧密码，jBCrypt 哈希入库")
    public Result<Void> resetPassword(@PathVariable Long id, @Valid @RequestBody DoctorPasswordRequest request) {
        doctorService.resetPassword(id, request);
        return Result.success("重置成功", null);
    }
}
