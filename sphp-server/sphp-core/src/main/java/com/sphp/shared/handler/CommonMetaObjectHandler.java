package com.sphp.shared.handler;

import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import org.apache.ibatis.reflection.MetaObject;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

/**
 * MyBatis-Plus 公共审计字段自动填充处理器。
 */
@Component
public class CommonMetaObjectHandler implements MetaObjectHandler {

    /**
     * 在插入数据时填充未显式指定的创建和更新时间。
     *
     * @param metaObject MyBatis-Plus 元对象
     */
    @Override
    public void insertFill(MetaObject metaObject) {
        // 使用同一时刻填充创建和更新时间，保证新记录审计时间一致
        OffsetDateTime now = OffsetDateTime.now();
        // strictFill 仅在字段为空时赋值，保留历史数据导入的显式时间
        this.strictInsertFill(metaObject, "createdAt", OffsetDateTime.class, now);
        this.strictInsertFill(metaObject, "updatedAt", OffsetDateTime.class, now);
    }

    /**
     * 在更新数据时填充未显式指定的更新时间。
     *
     * @param metaObject MyBatis-Plus 元对象
     */
    @Override
    public void updateFill(MetaObject metaObject) {
        // strictFill 仅在字段为空时赋值，避免覆盖业务层显式维护的时间
        this.strictUpdateFill(metaObject, "updatedAt", OffsetDateTime.class, OffsetDateTime.now());
    }
}
