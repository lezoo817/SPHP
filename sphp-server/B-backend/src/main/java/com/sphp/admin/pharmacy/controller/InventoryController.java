package com.sphp.admin.pharmacy.controller;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.pharmacy.dto.InventoryAlertVO;
import com.sphp.admin.pharmacy.dto.InventoryListVO;
import com.sphp.admin.pharmacy.dto.InventoryUnlockRequest;
import com.sphp.admin.pharmacy.dto.InventoryUpdateRequest;
import com.sphp.admin.pharmacy.service.InventoryService;
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

import java.util.List;

/**
 * 药品库存管理接口（管理员，系分 §5.7.3~5.7.6）。
 *
 * <p>外部完整 URL 前缀为 {@code /api/b/admin/inventory}。
 */
@RestController
@RequestMapping("/b/admin/inventory")
@Tag(name = "5-库存管理", description = "库存列表/更新/预警/释放锁定（管理员）")
@RequiredArgsConstructor
public class InventoryController {

    private static final int MAX_PAGE_SIZE = 100;

    private static int clampSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private final InventoryService inventoryService;

    @GetMapping
    @Operation(summary = "查询库存列表", description = "分页查询药房药品库存（按当前管理员所属医院过滤）")
    public Result<PageResult<InventoryListVO>> page(
            @Parameter(description = "药品ID过滤") @RequestParam(required = false) Long drugId,
            @Parameter(description = "药房ID过滤") @RequestParam(required = false) Long pharmacyId,
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") int size) {
        return Result.success("查询成功",
                inventoryService.page(drugId, pharmacyId, page, clampSize(size)));
    }

    @PutMapping("/{id}")
    @Operation(summary = "更新库存", description = "更新可售/锁定/安全库存与单价")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody InventoryUpdateRequest request) {
        inventoryService.update(id, request);
        return Result.success("更新成功", null);
    }

    @GetMapping("/alerts")
    @Operation(summary = "低库存预警", description = "返回 availableCount < safetyStock 的药品库存列表")
    public Result<List<InventoryAlertVO>> alerts(
            @Parameter(description = "药房ID过滤") @RequestParam(required = false) Long pharmacyId) {
        return Result.success("查询成功", inventoryService.alerts(pharmacyId));
    }

    @PostMapping("/{id}/unlock")
    @Operation(summary = "手动释放锁定库存", description = "前置校验订单确实锁定该库存后释放")
    public Result<Void> unlock(@PathVariable Long id, @Valid @RequestBody InventoryUnlockRequest request) {
        inventoryService.unlock(id, request);
        return Result.success("释放成功", null);
    }
}
