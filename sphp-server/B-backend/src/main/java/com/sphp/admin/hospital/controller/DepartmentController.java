package com.sphp.admin.hospital.controller;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.hospital.dto.DepartmentCreateRequest;
import com.sphp.admin.hospital.dto.DepartmentStatusRequest;
import com.sphp.admin.hospital.dto.DepartmentUpdateRequest;
import com.sphp.admin.hospital.service.DepartmentService;
import com.sphp.admin.hospital.vo.DepartmentDetailVO;
import com.sphp.admin.hospital.vo.DepartmentListVO;
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
 * 科室管理接口（管理员，系分 §5.3.3~5.3.7）。
 *
 * <p>外部完整 URL 前缀为 {@code /api/b/admin/departments}。
 */
@RestController
@RequestMapping("/b/admin/departments")
@Tag(name = "B端科室管理", description = "科室列表/详情/新增/编辑/启停（管理员）")
@RequiredArgsConstructor
public class DepartmentController {

    /** 每页大小上限，防止超大数据量查询 */
    private static final int MAX_PAGE_SIZE = 100;

    /** 每页大小钳制到 [1, MAX_PAGE_SIZE] */
    private static int clampSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private final DepartmentService departmentService;

    @GetMapping
    @Operation(summary = "查询科室列表", description = "分页查询科室（按当前管理员所属医院过滤）")
    public Result<PageResult<DepartmentListVO>> page(
            @Parameter(description = "科室名称模糊检索") @RequestParam(required = false) String name,
            @Parameter(description = "科室主任姓名模糊检索") @RequestParam(required = false) String headDoctorName,
            @Parameter(description = "状态过滤：ENABLED / DISABLED") @RequestParam(required = false) String status,
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") int size) {
        return Result.success("查询成功", departmentService.page(name, headDoctorName, status, page, clampSize(size)));
    }

    @GetMapping("/{id}")
    @Operation(summary = "查询科室详情", description = "科室详情（含医生数量、负责人姓名）")
    public Result<DepartmentDetailVO> detail(@PathVariable Long id) {
        return Result.success("查询成功", departmentService.detail(id));
    }

    @PostMapping
    @Operation(summary = "新增科室", description = "医院归属由后端根据当前管理员所属医院自动填充")
    public Result<Void> create(@Valid @RequestBody DepartmentCreateRequest request) {
        departmentService.create(request);
        return Result.success("新增成功", null);
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑科室", description = "编辑科室名称/负责人/简介")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DepartmentUpdateRequest request) {
        departmentService.update(id, request);
        return Result.success("编辑成功", null);
    }

    @PutMapping("/{id}/status")
    @Operation(summary = "启用/停用科室", description = "停用时前置校验：科室下无启用医生(4001)/已发布排班(4002)/进行中问诊(4003)")
    public Result<Void> updateStatus(@PathVariable Long id, @Valid @RequestBody DepartmentStatusRequest request) {
        departmentService.updateStatus(id, request);
        return Result.success("操作成功", null);
    }
}
