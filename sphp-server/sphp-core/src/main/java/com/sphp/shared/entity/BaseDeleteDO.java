package com.sphp.shared.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

/**
 * 带软删除时间的持久化实体公共基类。
 *
 * <p>删除时间由业务状态机或软删除规则显式维护，不启用自动逻辑删除。
 */
@Getter
@Setter
public class BaseDeleteDO extends BaseDO {

    /** 软删除时间，为空表示未删除 */
    @TableField("deleted_at")
    protected OffsetDateTime deletedAt;
}
