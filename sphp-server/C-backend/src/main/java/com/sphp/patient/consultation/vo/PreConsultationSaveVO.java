package com.sphp.patient.consultation.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

/**
 * 预问诊保存结果。
 */
@Getter
@Builder
public class PreConsultationSaveVO {

    /** 问诊记录 ID */
    private final Long consultationId;
    /** 问诊当前状态 */
    private final String status;
    /** 最近保存时间 */
    private final OffsetDateTime savedAt;
    /** 提交为待接诊的时间，草稿时为空 */
    private final OffsetDateTime submittedAt;
}
