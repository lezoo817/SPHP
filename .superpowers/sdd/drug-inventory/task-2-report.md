# Task 2 Report: 药品目录 DTO + Service + Controller

## 状态
DONE_WITH_CONCERNS

## 创建文件清单（6 个，均在 `D:\IdeaProjects\SPHP\SPHP\sphp-server\B-backend\src\main\java\com\sphp\admin\pharmacy\` 下）

| 文件路径 | 行数 |
|---|---|
| `dto/DrugCreateRequest.java` | 34 |
| `dto/DrugUpdateRequest.java` | 30 |
| `dto/DrugListVO.java` | 39 |
| `service/DrugService.java` | 17 |
| `service/impl/DrugServiceImpl.java` | 157 |
| `controller/DrugAdminController.java` | 73 |
| 合计 | 350 |

## 逐文件确认与简报一致

- **DrugCreateRequest.java**：内容与简报逐字一致。包声明 `com.sphp.admin.pharmacy.dto`、导入（Schema / NotBlank / Data）、注解顺序（`@Data` + `@Schema`）、7 个字段及其 `@Schema`/`@NotBlank` 注解均与简报完全一致。
- **DrugUpdateRequest.java**：内容与简报逐字一致。7 个字段全可选，无校验注解，注解顺序一致。
- **DrugListVO.java**：内容与简报逐字一致。`@Data @Builder @NoArgsConstructor @AllArgsConstructor` 注解组合及 8 个字段一致。
- **DrugService.java**：内容与简报逐字一致。接口方法签名 `page/create/update/delete` 与参数一致。
- **DrugServiceImpl.java**：内容与简报逐字一致。关键逻辑已核对：
  - `create` 默认值兜底：unit 默认 `"盒"`、status 默认 `"ENABLED"`；
  - `delete` 库存前置校验：`pharmacy_drug_stock` 按 drug_id + 同医院 pharmacy 子查询计数，>0 抛 A0401；
  - `assertApprovalUnique` 编辑时 `ne(excludeId != null, ...)` 排除自身；
  - `getDrugInHospital` 越权/已删除返回 A0402。
- **DrugAdminController.java**：内容与简报逐字一致。`MAX_PAGE_SIZE=100`、`clampSize`、四个端点（GET/POST/PUT/DELETE）及 Swagger 注解均一致。

## 约束遵守情况
- 未执行任何 git 写操作、未执行编译/构建命令、未执行 SQL / 连接数据库。
- 仅用 Write 工具创建了上述 6 个新文件，未修改任何既有文件。

## 遇到的问题 / 关注点
1. **Controller 缺少显式权限注解**：`DrugAdminController` 无 `@PreAuthorize` 等权限注解，依赖 `CurrentUserService.getCurrentHospitalId()` 的"强制 ADMIN"校验兜底。与简报一致（简报即如此），不视为缺陷，但提示注意后续若引入方法级鉴权需要补注解。
2. **`Drug` 实体的 `contraindication` / `sideEffect` 字段未被 DTO/Service 使用**：`Drug` 实体包含这两个字段，但本任务 DTO 与 Service 未涉及，符合简报设计（简报即如此），不影响功能。
3. **`DrugServiceImpl` 第 163 行 `Wrappers.lambdaQuery()` 泛型推断**：`drugMapper.selectPage(new Page<>(page, size), Wrappers.<Drug>lambdaQuery()...)` 泛型显式指定，可编译。未运行编译验证（按红线禁止），建议用户后续手动 `mvn compile` 校验。
4. **未检查 `Result.success` 与 `BusinessException` 的构造签名**：简报代码假定 `Result.success(msg, data)` 与 `new BusinessException(code, message)` 存在。这两个类在共享模块 `sphp-core`（`com.sphp.shared.*`），不在本次改动范围内，未做读取验证。
