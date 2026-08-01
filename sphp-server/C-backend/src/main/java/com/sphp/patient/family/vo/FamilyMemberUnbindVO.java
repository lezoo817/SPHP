package com.sphp.patient.family.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 停用解绑家庭成员响应对象。
 */
@Getter
@Builder
public class FamilyMemberUnbindVO {

    /** 已解绑的就诊人 ID */
    private final Long patientId;
    /** 是否已完成解绑 */
    private final Boolean unbound;
    /** 关系停用时间 */
    private final OffsetDateTime unboundAt;
}
