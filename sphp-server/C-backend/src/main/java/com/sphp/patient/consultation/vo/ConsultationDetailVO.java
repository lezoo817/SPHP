package com.sphp.patient.consultation.vo;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 问诊详情与文字消息响应。
 */
@Getter
@Builder
public class ConsultationDetailVO {

    /** 问诊记录 ID */
    private final Long id;

    /** 问诊状态 */
    private final String status;

    /** 接诊医生信息 */
    private final Doctor doctor;

    /** 预问诊内容 */
    private final PreConsultation preConsultation;

    /** 文字消息列表 */
    private final List<Message> messages;

    /** 关联的已批准处方 ID 列表 */
    private final List<Long> prescriptionIds;

    /**
     * 医生展示信息。
     */
    @Getter
    @Builder
    public static class Doctor {

        /** 医生 ID */
        private final Long id;

        /** 医生姓名 */
        private final String name;

        /** 医生职称 */
        private final String title;
    }

    /**
     * 预问诊展示内容。
     */
    @Getter
    @Builder
    public static class PreConsultation {

        /** 患者主诉 */
        private final String chiefComplaint;

        /** 现病史补充 */
        private final String historyOfPresentIllness;

        /** 附件列表 */
        private final List<ConsultationAttachmentVO> attachments;

        /** 最近保存时间 */
        private final OffsetDateTime savedAt;

        /** 提交时间，草稿时为空 */
        private final OffsetDateTime submittedAt;
    }

    /**
     * 文字消息展示项。
     */
    @Getter
    @Builder
    public static class Message {

        /** 消息 ID */
        private final Long id;
        /** 发送方类型 */
        private final String senderType;
        /** 消息内容 */
        private final String content;
        /** 创建时间 */
        private final OffsetDateTime createdAt;
    }
}
