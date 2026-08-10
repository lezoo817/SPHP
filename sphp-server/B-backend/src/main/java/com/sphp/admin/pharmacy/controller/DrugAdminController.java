package com.sphp.admin.pharmacy.controller;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.pharmacy.dto.DrugCreateRequest;
import com.sphp.admin.pharmacy.dto.DrugListVO;
import com.sphp.admin.pharmacy.dto.DrugUpdateRequest;
import com.sphp.admin.pharmacy.service.DrugService;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 药品目录管理接口（管理员视角）。
 *
 * <p>外部完整 URL 前缀为 {@code /api/b/admin/drugs}。所有接口按当前登录管理员
 * 所属医院（{@code hospital_id}）做数据隔离；药品的批准文号唯一性也按医院维度校验。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
@RestController
@RequestMapping("/b/admin/drugs")
@Tag(name = "6-药品目录", description = "药品列表/新增/编辑/删除（管理员）")
@RequiredArgsConstructor
public class DrugAdminController {

    private static final int MAX_PAGE_SIZE = 100;

    private static int clampSize(int size) {
        return Math.clamp(size, 1, MAX_PAGE_SIZE);
    }

    private final DrugService drugService;

    @GetMapping
    @Operation(summary = "查询药品目录", description = "分页查询药品（按当前管理员所属医院过滤）")
    public Result<PageResult<DrugListVO>> page(
            @Parameter(description = "药品名称模糊检索") @RequestParam(required = false) String name,
            @Parameter(description = "状态过滤：ENABLED / DISABLED") @RequestParam(required = false) String status,
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") int size) {
        return Result.success("查询成功", drugService.page(name, status, page, clampSize(size)));
    }

    @PostMapping
    @Operation(summary = "新增药品", description = "医院归属由后端根据当前管理员所属医院自动填充")
    public Result<Void> create(@Valid @RequestBody DrugCreateRequest request) {
        drugService.create(request);
        return Result.success("新增成功", null);
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑药品", description = "编辑药品基本信息与状态")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody DrugUpdateRequest request) {
        drugService.update(id, request);
        return Result.success("编辑成功", null);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "删除药品", description = "软删除；存在库存记录的药品拒绝删除")
    public Result<Void> delete(@PathVariable Long id) {
        drugService.delete(id);
        return Result.success("删除成功", null);
    }
}
