# Task 1 Report: pharmacy 包实体与 Mapper

## 创建文件清单

| # | 文件路径 | 行数 |
|---|----------|------|
| 1 | `D:\IdeaProjects\SPHP\SPHP\sphp-server\B-backend\src\main\java\com\sphp\admin\pharmacy\entity\Pharmacy.java` | 25 |
| 2 | `D:\IdeaProjects\SPHP\SPHP\sphp-server\B-backend\src\main\java\com\sphp\admin\pharmacy\entity\PharmacyDrugStock.java` | 23 |
| 3 | `D:\IdeaProjects\SPHP\SPHP\sphp-server\B-backend\src\main\java\com\sphp\admin\pharmacy\entity\DrugOrder.java` | 23 |
| 4 | `D:\IdeaProjects\SPHP\SPHP\sphp-server\B-backend\src\main\java\com\sphp\admin\pharmacy\entity\DrugOrderItem.java` | 22 |
| 5 | `D:\IdeaProjects\SPHP\SPHP\sphp-server\B-backend\src\main\java\com\sphp\admin\pharmacy\mapper\PharmacyMapper.java` | 8 |
| 6 | `D:\IdeaProjects\SPHP\SPHP\sphp-server\B-backend\src\main\java\com\sphp\admin\pharmacy\mapper\PharmacyDrugStockMapper.java` | 8 |
| 7 | `D:\IdeaProjects\SPHP\SPHP\sphp-server\B-backend\src\main\java\com\sphp\admin\pharmacy\mapper\DrugOrderMapper.java` | 8 |
| 8 | `D:\IdeaProjects\SPHP\SPHP\sphp-server\B-backend\src\main\java\com\sphp\admin\pharmacy\mapper\DrugOrderItemMapper.java` | 8 |

## 逐文件确认（与简报代码一致）

- [x] **entity/Pharmacy.java** — 包声明 `com.sphp.admin.pharmacy.entity`、`@TableName("pharmacy")`、字段 id/hospitalId/name/address/phone/isDefault/status/createdAt/updatedAt/deletedAt，与简报第 1 段完全一致。
- [x] **entity/PharmacyDrugStock.java** — 包声明正确、`@TableName("pharmacy_drug_stock")`、字段 id/pharmacyId/drugId/availableCount/lockedCount/safetyStock/unitPriceCent/updatedAt，与简报第 2 段完全一致。
- [x] **entity/DrugOrder.java** — 包声明正确、`@TableName("drug_order")`、字段 id/patientId/prescriptionId/pharmacyId/status/createdAt/updatedAt/deletedAt，与简报第 3 段完全一致。
- [x] **entity/DrugOrderItem.java** — 包声明正确、`@TableName("drug_order_item")`、字段 id/drugOrderId/drugId/drugNameSnapshot/quantity/unitPriceCent/createdAt，与简报第 4 段完全一致。
- [x] **mapper/PharmacyMapper.java** — 包声明 `com.sphp.admin.pharmacy.mapper`、`extends BaseMapper<Pharmacy>`，与简报第 5 段完全一致。
- [x] **mapper/PharmacyDrugStockMapper.java** — 包声明正确、`extends BaseMapper<PharmacyDrugStock>`，与简报第 6 段完全一致。
- [x] **mapper/DrugOrderMapper.java** — 包声明正确、`extends BaseMapper<DrugOrder>`，与简报第 7 段完全一致。
- [x] **mapper/DrugOrderItemMapper.java** — 包声明正确、`extends BaseMapper<DrugOrderItem>`，与简报第 8 段完全一致。

## 遇到的问题 / 关注点

- 无问题。所有文件均为纯新增，未修改任何既有文件。
- 4 个实体均使用 `@TableId(type = IdType.AUTO)`，字段与简报逐字一致，未做任何增删改。
- 本任务未执行任何 git 写操作、编译命令或 SQL/数据库操作。
- 注意：4 个 Mapper 接口未标注 `@Mapper` 注解，与简报一致（MyBatis-Plus 通常通过 `@MapperScan` 扫描，不属本任务范围）。如项目使用显式 `@Mapper` 扫描方式，后续集成时需确认 `@MapperScan` 覆盖 `com.sphp.admin.pharmacy.mapper` 包。
