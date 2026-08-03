# Task 3 Report: 库存 DTO + Service + Controller

**状态**: DONE

创建于 `sphp-server/B-backend/src/main/java/com/sphp/admin/pharmacy/` 下 7 个 Java 文件，纯新增，未修改任何既有文件，未执行任何 git 写操作 / 编译 / SQL。

## 逐文件清单

| # | 文件路径（相对 sphp-server/B-backend） | 行数 | 与简报一致性 |
|---|------|------|------|
| 1 | `src/main/java/com/sphp/admin/pharmacy/dto/InventoryListVO.java` | 42 | 与简报逐字一致 |
| 2 | `src/main/java/com/sphp/admin/pharmacy/dto/InventoryAlertVO.java` | 36 | 与简报逐字一致 |
| 3 | `src/main/java/com/sphp/admin/pharmacy/dto/InventoryUpdateRequest.java` | 21 | 与简报逐字一致 |
| 4 | `src/main/java/com/sphp/admin/pharmacy/dto/InventoryUnlockRequest.java` | 17 | 与简报逐字一致 |
| 5 | `src/main/java/com/sphp/admin/pharmacy/service/InventoryService.java` | 20 | 与简报逐字一致 |
| 6 | `src/main/java/com/sphp/admin/pharmacy/service/impl/InventoryServiceImpl.java` | 198 | 与简报逐字一致 |
| 7 | `src/main/java/com/sphp/admin/pharmacy/controller/InventoryController.java` | 76 | 与简报逐字一致 |

合计 410 行。

## 逐文件确认

- 包声明、import、类名、注解、字段、方法签名与简报完全相同，未重命名、未改动类型、未调整方法顺序、未做任何"改进"。
- `InventoryServiceImpl` 关键逻辑逐字复现：
  - `page`：医院范围子查询（`pharmacy.hospital_id` 过滤）+ 可选 drugId/pharmacyId 过滤 + `orderByDesc(id)`，批量加载药品名称/规格，`computeStatus` 计算状态。
  - `update`：`@Transactional(rollbackFor = Exception.class)`，逐字段非空判断 + `assertNonNegative`，`setUpdatedAt(OffsetDateTime.now())`。
  - `alerts`：`available_count < safety_stock` 子查询 + `orderByAsc(availableCount)`。
  - `unlock`：近似预校验顺序严格一致——①无锁定（`lockedCount <= 0`）→②订单存在且未软删→③订单与库存同药房→④订单含该药品明细（`LIMIT 1`）→释放量 `Math.min(item.getQuantity(), lockedCount)`。
  - `computeStatus` 阈值：`>=10 NORMAL / >=4 LOW / <=3 ALERT`。
  - `loadDrugMap`：去重 + 剔除软删药品 + `toMap` 合并。
  - `getStockInHospital`：医院隔离（经 `pharmacy.hospital_id`），不存在/越权返回 A0402。
  - Controller 侧 `clampSize`（1~100 上限）与四个端点（列表 GET / 更新 PUT / 预警 GET / 释放 POST）与简报一致。

## 依赖确认（只读核对，未改动）

引用的既有类全部存在且签名匹配：
- Task 1 已建：`Pharmacy`、`PharmacyDrugStock`、`DrugOrder`、`DrugOrderItem` 4 实体 + 4 Mapper（`pharmacy/entity`、`pharmacy/mapper`）。
- `com.sphp.admin.common.CurrentUserService.getCurrentHospitalId()` → `Long`（common 包）。
- `com.sphp.admin.common.vo.PageResult.of(long, List, long, long)`（common/vo）。
- `com.sphp.shared.result.Result.success(String, T)`、`com.sphp.shared.exception.BusinessException(String, String)`（均位于 sphp-core 模块）。
- `com.sphp.admin.prescription.entity.Drug`（含 `name`/`specification`/`deletedAt`）+ `DrugMapper`（MyBatis-Plus BaseMapper，`selectBatchIds` 可用）。

## 遇到的问题 / 关注点

1. **未编译验证**：按项目红线未执行 mvn/gradle/javac，编译正确性未实际验证；但已对上述依赖签名做了静态核对，未见不匹配。
2. **`computeStatus` 潜在 NPE（低风险，按简报逐字保留）**：若某条库存记录 `available_count` 为 NULL（表字段允许为空时），`computeStatus(s.getAvailableCount())` 会对 `Integer` 自动拆箱抛 NPE。简报原样如此，未做改动。
3. **`unlock` 释放量语义**：`release = Math.min(item.getQuantity(), stock.getLockedCount())`，简报注明"近似预校验"，未与实际下单锁定流水强一致，属简报既定设计，逐字保留。
4. **无数据库访问 / 无 git 写操作 / 无既有文件改动**：均已遵守。

## 完成标准对照

- 7 个文件路径、包声明、类名与简报一致：通过。
- `InventoryServiceImpl` 关键逻辑（unlock 校验顺序、computeStatus 阈值、loadDrugMap 批量加载、getStockInHospital 医院隔离）：通过。
