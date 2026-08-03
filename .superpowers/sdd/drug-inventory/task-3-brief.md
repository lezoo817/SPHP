# Task 3 Brief: 库存 DTO + Service + Controller

**目标:** 在 `sphp-server/B-backend/src/main/java/com/sphp/admin/pharmacy/` 下新建 7 个 Java 文件，实现库存管理 API（列表/更新/预警/释放锁定，`/b/admin/inventory`）。纯新增文件，不修改任何既有文件。

**项目约束（必须遵守）：**
- **禁止 git 写操作**：不要执行 `git add`/`commit`/`push`/`stash`/`reset`/`checkout`/`worktree`。
- **禁止编译**：不要执行 `mvn`/`gradle`/`javac` 或任何构建命令。
- **禁止执行 SQL** / 连接数据库。
- 只需用 Write 工具创建下列文件。

**可复用的既有代码（只读引用，不要改动）：**
- Task 1 已建的 4 实体 + 4 Mapper（`com.sphp.admin.pharmacy.entity.*` / `mapper.*`）：`Pharmacy`（`getHospitalId()/getDeletedAt()`）、`PharmacyDrugStock`（`getPharmacyId()/getDrugId()/getAvailableCount()/getLockedCount()/getSafetyStock()/getUnitPriceCent()/setUpdatedAt()`）、`DrugOrder`（`getPharmacyId()/getDeletedAt()`）、`DrugOrderItem`（`getDrugOrderId()/getDrugId()/getQuantity()`）
- `com.sphp.admin.prescription.entity.Drug` + `com.sphp.admin.prescription.mapper.DrugMapper`
- `com.sphp.admin.common.CurrentUserService`（`getCurrentHospitalId()` 返回 Long，强制 ADMIN）
- `com.sphp.admin.common.vo.PageResult`（`PageResult.of(long total, List<T> list, long page, long size)`）
- `com.sphp.shared.result.Result`、`com.sphp.shared.exception.BusinessException`（`new BusinessException(String code, String message)`）

## 待创建文件（7 个）

### 1. dto/InventoryListVO.java
```java
package com.sphp.admin.pharmacy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 库存列表项。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "库存列表项")
public class InventoryListVO {
    @Schema(description = "库存记录ID")
    private Long id;

    @Schema(description = "药品ID")
    private Long drugId;

    @Schema(description = "药品名称")
    private String drugName;

    @Schema(description = "规格")
    private String specification;

    @Schema(description = "可售库存")
    private Integer availableCount;

    @Schema(description = "锁定库存")
    private Integer lockedCount;

    @Schema(description = "安全库存")
    private Integer safetyStock;

    @Schema(description = "单价（分）")
    private Integer unitPriceCent;

    @Schema(description = "库存状态：NORMAL / LOW / ALERT")
    private String status;
}
```

### 2. dto/InventoryAlertVO.java
```java
package com.sphp.admin.pharmacy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 低库存预警项。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "低库存预警项")
public class InventoryAlertVO {
    @Schema(description = "库存记录ID")
    private Long id;

    @Schema(description = "药品ID")
    private Long drugId;

    @Schema(description = "药品名称")
    private String drugName;

    @Schema(description = "规格")
    private String specification;

    @Schema(description = "可售库存")
    private Integer availableCount;

    @Schema(description = "安全库存")
    private Integer safetyStock;

    @Schema(description = "单价（分）")
    private Integer unitPriceCent;
}
```

### 3. dto/InventoryUpdateRequest.java
```java
package com.sphp.admin.pharmacy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 更新库存请求（所有字段均可选）。 */
@Data
@Schema(description = "更新库存请求")
public class InventoryUpdateRequest {
    @Schema(description = "可售库存")
    private Integer availableCount;

    @Schema(description = "锁定库存")
    private Integer lockedCount;

    @Schema(description = "安全库存")
    private Integer safetyStock;

    @Schema(description = "单价（分）")
    private Integer unitPriceCent;
}
```

### 4. dto/InventoryUnlockRequest.java
```java
package com.sphp.admin.pharmacy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** 释放锁定库存请求。 */
@Data
@Schema(description = "释放锁定库存请求")
public class InventoryUnlockRequest {
    @Schema(description = "购药订单ID", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotNull(message = "购药订单ID不能为空")
    private Long drugOrderId;

    @Schema(description = "释放原因")
    private String reason;
}
```

### 5. service/InventoryService.java
```java
package com.sphp.admin.pharmacy.service;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.pharmacy.dto.InventoryAlertVO;
import com.sphp.admin.pharmacy.dto.InventoryListVO;
import com.sphp.admin.pharmacy.dto.InventoryUnlockRequest;
import com.sphp.admin.pharmacy.dto.InventoryUpdateRequest;

import java.util.List;

/** 药品库存管理服务（系分 §5.7.3~5.7.6）。 */
public interface InventoryService {
    PageResult<InventoryListVO> page(Long drugId, Long pharmacyId, int page, int size);

    void update(Long id, InventoryUpdateRequest request);

    List<InventoryAlertVO> alerts(Long pharmacyId);

    void unlock(Long id, InventoryUnlockRequest request);
}
```

### 6. service/impl/InventoryServiceImpl.java
```java
package com.sphp.admin.pharmacy.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.pharmacy.dto.InventoryAlertVO;
import com.sphp.admin.pharmacy.dto.InventoryListVO;
import com.sphp.admin.pharmacy.dto.InventoryUnlockRequest;
import com.sphp.admin.pharmacy.dto.InventoryUpdateRequest;
import com.sphp.admin.pharmacy.entity.DrugOrder;
import com.sphp.admin.pharmacy.entity.DrugOrderItem;
import com.sphp.admin.pharmacy.entity.Pharmacy;
import com.sphp.admin.pharmacy.entity.PharmacyDrugStock;

import com.sphp.admin.pharmacy.mapper.BDrugOrderItemMapper;
import com.sphp.admin.pharmacy.mapper.BDrugOrderMapper;

import com.sphp.admin.pharmacy.mapper.PharmacyDrugStockMapper;
import com.sphp.admin.pharmacy.mapper.PharmacyMapper;
import com.sphp.admin.pharmacy.service.InventoryService;
import com.sphp.admin.prescription.entity.Drug;
import com.sphp.admin.prescription.mapper.DrugMapper;
import com.sphp.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

/** 药品库存管理服务实现（系分 §5.7.3~5.7.6）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class InventoryServiceImpl implements InventoryService {

    private final PharmacyDrugStockMapper stockMapper;
    private final PharmacyMapper pharmacyMapper;
    private final DrugOrderMapper drugOrderMapper;
    private final DrugOrderItemMapper drugOrderItemMapper;
    private final DrugMapper drugMapper;
    private final CurrentUserService currentUserService;

    @Override
    public PageResult<InventoryListVO> page(Long drugId, Long pharmacyId, int page, int size) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Page<PharmacyDrugStock> result = stockMapper.selectPage(new Page<>(page, size),
                Wrappers.<PharmacyDrugStock>lambdaQuery()
                        .apply("pharmacy_id IN (SELECT id FROM pharmacy WHERE hospital_id = {0} AND deleted_at IS NULL)",
                                hospitalId)
                        .eq(drugId != null, PharmacyDrugStock::getDrugId, drugId)
                        .eq(pharmacyId != null, PharmacyDrugStock::getPharmacyId, pharmacyId)
                        .orderByDesc(PharmacyDrugStock::getId));
        Map<Long, Drug> drugMap = loadDrugMap(result.getRecords().stream()
                .map(PharmacyDrugStock::getDrugId).toList());
        List<InventoryListVO> list = result.getRecords().stream()
                .map(s -> {
                    Drug d = drugMap.get(s.getDrugId());
                    return InventoryListVO.builder()
                            .id(s.getId())
                            .drugId(s.getDrugId())
                            .drugName(d != null ? d.getName() : null)
                            .specification(d != null ? d.getSpecification() : null)
                            .availableCount(s.getAvailableCount())
                            .lockedCount(s.getLockedCount())
                            .safetyStock(s.getSafetyStock())
                            .unitPriceCent(s.getUnitPriceCent())
                            .status(computeStatus(s.getAvailableCount()))
                            .build();
                })
                .toList();
        return PageResult.of(result.getTotal(), list, page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, InventoryUpdateRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        PharmacyDrugStock stock = getStockInHospital(id, hospitalId);
        if (request.getAvailableCount() != null) {
            assertNonNegative(request.getAvailableCount(), "可售库存");
            stock.setAvailableCount(request.getAvailableCount());
        }
        if (request.getLockedCount() != null) {
            assertNonNegative(request.getLockedCount(), "锁定库存");
            stock.setLockedCount(request.getLockedCount());
        }
        if (request.getSafetyStock() != null) {
            assertNonNegative(request.getSafetyStock(), "安全库存");
            stock.setSafetyStock(request.getSafetyStock());
        }
        if (request.getUnitPriceCent() != null) {
            assertNonNegative(request.getUnitPriceCent(), "单价");
            stock.setUnitPriceCent(request.getUnitPriceCent());
        }
        stock.setUpdatedAt(OffsetDateTime.now());
        stockMapper.updateById(stock);
        log.info("更新库存 stockId={}", id);
    }

    @Override
    public List<InventoryAlertVO> alerts(Long pharmacyId) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        List<PharmacyDrugStock> rows = stockMapper.selectList(
                Wrappers.<PharmacyDrugStock>lambdaQuery()
                        .apply("pharmacy_id IN (SELECT id FROM pharmacy WHERE hospital_id = {0} AND deleted_at IS NULL)",
                                hospitalId)
                        .eq(pharmacyId != null, PharmacyDrugStock::getPharmacyId, pharmacyId)
                        .apply("available_count < safety_stock")
                        .orderByAsc(PharmacyDrugStock::getAvailableCount));
        Map<Long, Drug> drugMap = loadDrugMap(rows.stream().map(PharmacyDrugStock::getDrugId).toList());
        return rows.stream()
                .map(s -> {
                    Drug d = drugMap.get(s.getDrugId());
                    return InventoryAlertVO.builder()
                            .id(s.getId())
                            .drugId(s.getDrugId())
                            .drugName(d != null ? d.getName() : null)
                            .specification(d != null ? d.getSpecification() : null)
                            .availableCount(s.getAvailableCount())
                            .safetyStock(s.getSafetyStock())
                            .unitPriceCent(s.getUnitPriceCent())
                            .build();
                })
                .toList();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void unlock(Long id, InventoryUnlockRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        PharmacyDrugStock stock = getStockInHospital(id, hospitalId);
        if (stock.getLockedCount() == null || stock.getLockedCount() <= 0) {
            throw new BusinessException("A0402", "当前库存无锁定可释放");
        }

        // 近似预校验：订单存在且未软删、与库存同药房、含该药品明细
        DrugOrder order = drugOrderMapper.selectById(request.getDrugOrderId());
        if (order == null || order.getDeletedAt() != null) {
            throw new BusinessException("A0402", "购药订单不存在");
        }
        if (!order.getPharmacyId().equals(stock.getPharmacyId())) {
            throw new BusinessException("A0401", "购药订单与库存药房不一致，无法释放");
        }
        DrugOrderItem item = drugOrderItemMapper.selectOne(
                Wrappers.<DrugOrderItem>lambdaQuery()
                        .eq(DrugOrderItem::getDrugOrderId, order.getId())
                        .eq(DrugOrderItem::getDrugId, stock.getDrugId())
                        .last("LIMIT 1"));
        if (item == null) {
            throw new BusinessException("A0401", "购药订单未锁定该药品，无法释放");
        }

        // 释放量 = 订单该药品数量，上限 locked_count 兜底
        int release = Math.min(item.getQuantity(), stock.getLockedCount());
        stock.setLockedCount(stock.getLockedCount() - release);
        stock.setAvailableCount(stock.getAvailableCount() + release);
        stock.setUpdatedAt(OffsetDateTime.now());
        stockMapper.updateById(stock);
        log.info("释放锁定库存 stockId={}, drugOrderId={}, release={}, reason={}",
                id, request.getDrugOrderId(), release, request.getReason());
    }

    /** 库存状态：>=10 NORMAL；4~9 LOW；<=3 ALERT */
    private String computeStatus(int availableCount) {
        if (availableCount >= 10) return "NORMAL";
        if (availableCount >= 4) return "LOW";
        return "ALERT";
    }

    private void assertNonNegative(Integer value, String field) {
        if (value < 0) throw new BusinessException("A0401", field + "不能为负");
    }

    /** 批量加载药品信息（去重，剔除软删药品） */
    private Map<Long, Drug> loadDrugMap(List<Long> drugIds) {
        List<Long> ids = drugIds.stream().filter(Objects::nonNull).distinct().toList();
        if (ids.isEmpty()) return Map.of();
        return drugMapper.selectBatchIds(ids).stream()
                .filter(d -> d.getDeletedAt() == null)
                .collect(Collectors.toMap(Drug::getId, d -> d, (a, b) -> a));
    }

    /** 按 id + 医院范围查询库存（经 pharmacy.hospital_id），不存在或越权返回 A0402 */
    private PharmacyDrugStock getStockInHospital(Long id, Long hospitalId) {
        PharmacyDrugStock stock = stockMapper.selectById(id);
        if (stock == null) throw new BusinessException("A0402", "库存记录不存在");
        Pharmacy pharmacy = pharmacyMapper.selectById(stock.getPharmacyId());
        if (pharmacy == null || pharmacy.getDeletedAt() != null
                || !pharmacy.getHospitalId().equals(hospitalId)) {
            throw new BusinessException("A0402", "库存记录不存在");
        }
        return stock;
    }
}
```

### 7. controller/InventoryController.java
```java
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
```

## 完成标准
- 7 个文件路径、包声明、类名与简报一致。
- `InventoryServiceImpl` 关键逻辑与简报一致：`unlock` 的近似预校验顺序（无锁定→订单存在→同药房→含明细→释放 min(quantity, locked_count)）、`computeStatus` 阈值、`loadDrugMap` 批量加载、`getStockInHospital` 医院隔离。

## 报告
将完成情况写入 `D:\IdeaProjects\SPHP\SPHP\.superpowers\sdd\drug-inventory\task-3-report.md`：逐文件路径+行数、逐文件确认与简报一致、遇到的问题。

返回给 controller 的内容仅限：状态、创建文件清单、问题说明。
