# Task 1 Brief: pharmacy 包实体与 Mapper

**目标:** 在 `sphp-server/B-backend/src/main/java/com/sphp/admin/pharmacy/` 下新建 8 个 Java 文件（4 实体 + 4 Mapper）。此为纯新增文件，不修改任何既有文件。

**项目约束（必须遵守）：**
- **禁止 git 写操作**：不要执行 `git add` / `git commit` / `git push` / `git stash` / `git checkout` / `git worktree` 等任何会改变仓库状态或工作区的命令。
- **禁止编译**：不要执行 `mvn` / `gradle` / `javac` 或任何构建命令。
- **禁止执行 SQL** / 连接数据库。
- 只需用 Write 工具创建下列文件，然后汇报。

## 待创建文件（8 个）

### 1. entity/Pharmacy.java
```java
package com.sphp.admin.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** 药房表实体（对应表 pharmacy）。 */
@Data
@TableName("pharmacy")
public class Pharmacy {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long hospitalId;
    private String name;
    private String address;
    private String phone;
    private Boolean isDefault;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}
```

### 2. entity/PharmacyDrugStock.java
```java
package com.sphp.admin.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** 药房药品库存表实体（对应表 pharmacy_drug_stock）。 */
@Data
@TableName("pharmacy_drug_stock")
public class PharmacyDrugStock {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long pharmacyId;
    private Long drugId;
    private Integer availableCount;
    private Integer lockedCount;
    private Integer safetyStock;
    private Integer unitPriceCent;
    private OffsetDateTime updatedAt;
}
```

### 3. entity/DrugOrder.java
```java
package com.sphp.admin.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** 购药订单表实体（对应表 drug_order，仅 B 端预校验只读使用）。 */
@Data
@TableName("drug_order")
public class DrugOrder {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long patientId;
    private Long prescriptionId;
    private Long pharmacyId;
    private String status;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
    private OffsetDateTime deletedAt;
}
```

### 4. entity/DrugOrderItem.java
```java
package com.sphp.admin.pharmacy.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.OffsetDateTime;

/** 购药订单明细表实体（对应表 drug_order_item，仅 B 端预校验只读使用）。 */
@Data
@TableName("drug_order_item")
public class DrugOrderItem {
    @TableId(type = IdType.AUTO)
    private Long id;
    private Long drugOrderId;
    private Long drugId;
    private String drugNameSnapshot;
    private Integer quantity;
    private Integer unitPriceCent;
    private OffsetDateTime createdAt;
}
```

### 5. mapper/PharmacyMapper.java
```java
package com.sphp.admin.pharmacy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.admin.pharmacy.entity.Pharmacy;

/** 药房表 Mapper。 */
public interface PharmacyMapper extends BaseMapper<Pharmacy> {
}
```

### 6. mapper/PharmacyDrugStockMapper.java
```java
package com.sphp.admin.pharmacy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.admin.pharmacy.entity.PharmacyDrugStock;

/** 药房药品库存表 Mapper。 */
public interface PharmacyDrugStockMapper extends BaseMapper<PharmacyDrugStock> {
}
```

### 7. mapper/DrugOrderMapper.java
```java
package com.sphp.admin.pharmacy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.admin.pharmacy.entity.DrugOrder;

/** 购药订单表 Mapper（只读校验用）。 */
public interface DrugOrderMapper extends BaseMapper<DrugOrder> {
}
```

### 8. mapper/DrugOrderItemMapper.java
```java
package com.sphp.admin.pharmacy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.admin.pharmacy.entity.DrugOrderItem;

/** 购药订单明细表 Mapper（只读校验用）。 */
public interface DrugOrderItemMapper extends BaseMapper<DrugOrderItem> {
}
```

## 完成标准
- 8 个文件均创建成功，路径正确，包声明为 `com.sphp.admin.pharmacy.entity` / `com.sphp.admin.pharmacy.mapper`。
- 每个实体字段与表列一一对应（参考 1-初始创表.sql）。

## 报告
将完成情况写入文件 `D:\IdeaProjects\SPHP\SPHP\.superpowers\sdd\drug-inventory\task-1-report.md`：
- 每个创建文件的路径与文件行数
- 是否全部与简报代码一致（逐文件确认）
- 遇到的任何问题

返回给 controller 的内容仅限：状态（DONE / DONE_WITH_CONCERNS / NEEDS_CONTEXT / BLOCKED）、创建文件清单、问题说明。
