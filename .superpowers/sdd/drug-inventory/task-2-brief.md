# Task 2 Brief: 药品目录 DTO + Service + Controller

**目标:** 在 `sphp-server/B-backend/src/main/java/com/sphp/admin/pharmacy/` 下新建 6 个 Java 文件，实现药品目录管理 API（GET/POST/PUT/DELETE `/b/admin/drugs`）。纯新增文件，不修改任何既有文件。

**项目约束（必须遵守）：**
- **禁止 git 写操作**：不要执行 `git add`/`commit`/`push`/`stash`/`reset`/`checkout`/`worktree`。
- **禁止编译**：不要执行 `mvn`/`gradle`/`javac` 或任何构建命令。
- **禁止执行 SQL** / 连接数据库。
- 只需用 Write 工具创建下列文件。

**可复用的既有代码（只读引用，不要改动）：**
- `com.sphp.admin.prescription.entity.Drug`（实体，字段 id/hospitalId/name/specification/manufacturer/approvalNumber/unit/indication/contraindication/sideEffect/status/createdAt/updatedAt/deletedAt）
- `com.sphp.admin.prescription.mapper.DrugMapper`（`extends BaseMapper<Drug>`）
- `com.sphp.admin.pharmacy.entity.PharmacyDrugStock` + `com.sphp.admin.pharmacy.mapper.PharmacyDrugStockMapper`（Task 1 已建，字段 id/pharmacyId/drugId/availableCount/lockedCount/safetyStock/unitPriceCent/updatedAt）
- `com.sphp.admin.common.CurrentUserService`（`getCurrentHospitalId()` 返回 Long，强制 ADMIN）
- `com.sphp.admin.common.vo.PageResult`（`PageResult.of(long total, List<T> list, long page, long size)`）
- `com.sphp.shared.result.Result`（`Result.success(msg, data)`）
- `com.sphp.shared.exception.BusinessException`（`new BusinessException(String code, String message)`）
- 参考控制器风格：`com.sphp.admin.hospital.controller.DepartmentController`（含 `MAX_PAGE_SIZE=100` 与 `clampSize`）

## 待创建文件（6 个）

### 1. dto/DrugCreateRequest.java
```java
package com.sphp.admin.pharmacy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** 新增药品请求。 */
@Data
@Schema(description = "新增药品请求")
public class DrugCreateRequest {
    @Schema(description = "药品名称", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "药品名称不能为空")
    private String name;

    @Schema(description = "规格", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "规格不能为空")
    private String specification;

    @Schema(description = "单位，默认盒")
    private String unit;

    @Schema(description = "适应症")
    private String indication;

    @Schema(description = "生产厂家")
    private String manufacturer;

    @Schema(description = "批准文号", requiredMode = Schema.RequiredMode.REQUIRED)
    @NotBlank(message = "批准文号不能为空")
    private String approvalNumber;

    @Schema(description = "状态：ENABLED / DISABLED，默认 ENABLED")
    private String status;
}
```

### 2. dto/DrugUpdateRequest.java
```java
package com.sphp.admin.pharmacy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/** 编辑药品请求（所有字段均可选）。 */
@Data
@Schema(description = "编辑药品请求")
public class DrugUpdateRequest {
    @Schema(description = "药品名称")
    private String name;

    @Schema(description = "规格")
    private String specification;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "适应症")
    private String indication;

    @Schema(description = "生产厂家")
    private String manufacturer;

    @Schema(description = "批准文号")
    private String approvalNumber;

    @Schema(description = "状态：ENABLED / DISABLED")
    private String status;
}
```

### 3. dto/DrugListVO.java
```java
package com.sphp.admin.pharmacy.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/** 药品列表项。 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "药品列表项")
public class DrugListVO {
    @Schema(description = "药品ID")
    private Long id;

    @Schema(description = "药品名称")
    private String name;

    @Schema(description = "规格")
    private String specification;

    @Schema(description = "单位")
    private String unit;

    @Schema(description = "适应症")
    private String indication;

    @Schema(description = "生产厂家")
    private String manufacturer;

    @Schema(description = "批准文号")
    private String approvalNumber;

    @Schema(description = "状态：ENABLED / DISABLED")
    private String status;
}
```

### 4. service/DrugService.java
```java
package com.sphp.admin.pharmacy.service;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.pharmacy.dto.DrugCreateRequest;
import com.sphp.admin.pharmacy.dto.DrugListVO;
import com.sphp.admin.pharmacy.dto.DrugUpdateRequest;

/** 药品目录服务（系分 §5.7.1~5.7.2）。 */
public interface DrugService {
    PageResult<DrugListVO> page(String name, String status, int page, int size);

    void create(DrugCreateRequest request);

    void update(Long id, DrugUpdateRequest request);

    void delete(Long id);
}
```

### 5. service/impl/DrugServiceImpl.java
```java
package com.sphp.admin.pharmacy.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.pharmacy.dto.DrugCreateRequest;
import com.sphp.admin.pharmacy.dto.DrugListVO;
import com.sphp.admin.pharmacy.dto.DrugUpdateRequest;
import com.sphp.admin.pharmacy.entity.PharmacyDrugStock;
import com.sphp.admin.pharmacy.mapper.PharmacyDrugStockMapper;
import com.sphp.admin.pharmacy.service.DrugService;
import com.sphp.admin.prescription.entity.Drug;
import com.sphp.admin.prescription.mapper.DrugMapper;
import com.sphp.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.List;

/** 药品目录服务实现（系分 §5.7.1~5.7.2）。 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DrugServiceImpl implements DrugService {

    private final DrugMapper drugMapper;
    private final PharmacyDrugStockMapper stockMapper;
    private final CurrentUserService currentUserService;

    @Override
    public PageResult<DrugListVO> page(String name, String status, int page, int size) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Page<Drug> result = drugMapper.selectPage(new Page<>(page, size),
                Wrappers.<Drug>lambdaQuery()
                        .eq(Drug::getHospitalId, hospitalId)
                        .like(StringUtils.hasText(name), Drug::getName, name)
                        .eq(StringUtils.hasText(status), Drug::getStatus, status)
                        .isNull(Drug::getDeletedAt)
                        .orderByDesc(Drug::getId));
        List<DrugListVO> list = result.getRecords().stream()
                .map(this::toDrugListVO)
                .toList();
        return PageResult.of(result.getTotal(), list, page, size);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void create(DrugCreateRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        String unit = StringUtils.hasText(request.getUnit()) ? request.getUnit().trim() : "盒";
        String status = StringUtils.hasText(request.getStatus()) ? request.getStatus().trim() : "ENABLED";
        assertStatusValid(status);
        assertApprovalUnique(hospitalId, request.getApprovalNumber().trim(), null);

        Drug drug = new Drug();
        drug.setHospitalId(hospitalId);
        drug.setName(request.getName().trim());
        drug.setSpecification(request.getSpecification().trim());
        drug.setUnit(unit);
        drug.setIndication(request.getIndication());
        drug.setManufacturer(request.getManufacturer());
        drug.setApprovalNumber(request.getApprovalNumber().trim());
        drug.setStatus(status);
        drugMapper.insert(drug);
        log.info("新增药品 drugId={}, name={}, hospitalId={}", drug.getId(), drug.getName(), hospitalId);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, DrugUpdateRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Drug drug = getDrugInHospital(id, hospitalId);
        if (StringUtils.hasText(request.getName())) drug.setName(request.getName().trim());
        if (StringUtils.hasText(request.getSpecification())) drug.setSpecification(request.getSpecification().trim());
        if (StringUtils.hasText(request.getUnit())) drug.setUnit(request.getUnit().trim());
        if (request.getIndication() != null) drug.setIndication(request.getIndication());
        if (request.getManufacturer() != null) drug.setManufacturer(request.getManufacturer());
        if (StringUtils.hasText(request.getApprovalNumber())) {
            assertApprovalUnique(hospitalId, request.getApprovalNumber().trim(), id);
            drug.setApprovalNumber(request.getApprovalNumber().trim());
        }
        if (StringUtils.hasText(request.getStatus())) {
            assertStatusValid(request.getStatus().trim());
            drug.setStatus(request.getStatus().trim());
        }
        drug.setUpdatedAt(OffsetDateTime.now());
        drugMapper.updateById(drug);
        log.info("编辑药品 drugId={}", id);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void delete(Long id) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Drug drug = getDrugInHospital(id, hospitalId);
        // 存在库存记录时拒绝删除，避免库存出现孤儿药品
        Long stockCount = stockMapper.selectCount(
                Wrappers.<PharmacyDrugStock>lambdaQuery()
                        .eq(PharmacyDrugStock::getDrugId, id)
                        .apply("pharmacy_id IN (SELECT id FROM pharmacy WHERE hospital_id = {0} AND deleted_at IS NULL)",
                                hospitalId));
        if (stockCount != null && stockCount > 0) {
            throw new BusinessException("A0401", "该药品存在库存记录，无法删除");
        }
        drug.setDeletedAt(OffsetDateTime.now());
        drug.setStatus("DISABLED");
        drug.setUpdatedAt(OffsetDateTime.now());
        drugMapper.updateById(drug);
        log.info("删除药品 drugId={}", id);
    }

    private DrugListVO toDrugListVO(Drug d) {
        return DrugListVO.builder()
                .id(d.getId())
                .name(d.getName())
                .specification(d.getSpecification())
                .unit(d.getUnit())
                .indication(d.getIndication())
                .manufacturer(d.getManufacturer())
                .approvalNumber(d.getApprovalNumber())
                .status(d.getStatus())
                .build();
    }

    /** 按 id + 医院范围查询药品，不存在或越权返回 A0402 */
    private Drug getDrugInHospital(Long id, Long hospitalId) {
        Drug drug = drugMapper.selectById(id);
        if (drug == null || drug.getDeletedAt() != null || !drug.getHospitalId().equals(hospitalId)) {
            throw new BusinessException("A0402", "药品不存在");
        }
        return drug;
    }

    /** 同医院 + 批准文号唯一性校验（编辑时排除自身） */
    private void assertApprovalUnique(Long hospitalId, String approvalNumber, Long excludeId) {
        Long dup = drugMapper.selectCount(
                Wrappers.<Drug>lambdaQuery()
                        .eq(Drug::getHospitalId, hospitalId)
                        .eq(Drug::getApprovalNumber, approvalNumber)
                        .ne(excludeId != null, Drug::getId, excludeId)
                        .isNull(Drug::getDeletedAt));
        if (dup != null && dup > 0) {
            throw new BusinessException("A0401", "同医院已存在相同批准文号的药品");
        }
    }

    private void assertStatusValid(String status) {
        if (!"ENABLED".equals(status) && !"DISABLED".equals(status)) {
            throw new BusinessException("A0401", "状态仅支持 ENABLED / DISABLED");
        }
    }
}
```

### 6. controller/DrugAdminController.java
```java
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
 * 药品目录管理接口（管理员，系分 §5.7.1~5.7.2）。
 *
 * <p>外部完整 URL 前缀为 {@code /api/b/admin/drugs}。
 */
@RestController
@RequestMapping("/b/admin/drugs")
@Tag(name = "5-药品目录", description = "药品列表/新增/编辑/删除（管理员）")
@RequiredArgsConstructor
public class DrugAdminController {

    private static final int MAX_PAGE_SIZE = 100;

    private static int clampSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
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
```

## 完成标准
- 6 个文件路径、包声明、类名与简报一致。
- `DrugServiceImpl` 逻辑与简报一致（特别是 `create` 的默认值兜底、`delete` 的库存前置校验、`assertApprovalUnique` 排除自身）。

## 报告
将完成情况写入 `D:\IdeaProjects\SPHP\SPHP\.superpowers\sdd\drug-inventory\task-2-report.md`：逐文件路径+行数、逐文件确认与简报一致、遇到的问题。

返回给 controller 的内容仅限：状态、创建文件清单、问题说明。
