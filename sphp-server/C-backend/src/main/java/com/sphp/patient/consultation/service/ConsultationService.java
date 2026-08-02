package com.sphp.patient.consultation.service;

import com.sphp.patient.consultation.dto.ConsultationMessageSendRequest;
import com.sphp.patient.consultation.dto.PreConsultationSaveRequest;
import com.sphp.patient.consultation.vo.ConsultationDetailVO;
import com.sphp.patient.consultation.vo.ConsultationMessageSendVO;
import com.sphp.patient.consultation.vo.ConsultationPageVO;
import com.sphp.patient.consultation.vo.PreConsultationSaveVO;

/**
 * C端预问诊和文字问诊服务。
 */
public interface ConsultationService {

    /**
     * 创建、保存或提交当前账号可访问就诊人的预问诊。
     *
     * @param request 预问诊请求
     * @return 保存后的问诊信息
     */
    PreConsultationSaveVO savePreConsultation(PreConsultationSaveRequest request);

    /**
     * 分页查询当前账号指定就诊人的问诊记录。
     *
     * @param patientId 可选就诊人 ID，未传时查询本人
     * @param status 可选问诊状态
     * @param pageNo 可选页码
     * @param pageSize 可选每页数量
     * @return 问诊记录分页响应
     */
    ConsultationPageVO listConsultations(Long patientId, String status, Integer pageNo, Integer pageSize);

    /**
     * 查询当前账号可访问的问诊详情与文字消息。
     *
     * @param consultationId 问诊记录 ID
     * @return 问诊详情与文字消息
     */
    ConsultationDetailVO getConsultationDetail(Long consultationId);

    /**
     * 向进行中的问诊发送患者文字消息。
     *
     * @param consultationId 问诊记录 ID
     * @param request 文字消息请求参数
     * @return 已发送消息信息
     */
    ConsultationMessageSendVO sendConsultationMessage(Long consultationId, ConsultationMessageSendRequest request);
}
