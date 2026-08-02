package com.sphp.patient.consultation.service;

import com.sphp.patient.consultation.dto.PreConsultationSaveRequest;
import com.sphp.patient.consultation.vo.PreConsultationSaveVO;
import com.sphp.patient.consultation.vo.ConsultationPageVO;

/**
 * C端问诊与处方查询服务。
 */
public interface ConsultationService {

    /**
     * 创建、保存或提交当前账号可访问就诊人的预问诊。
     *
     * @param request 预问诊请求参数
     * @return 保存后的问诊信息
     */
    PreConsultationSaveVO savePreConsultation(PreConsultationSaveRequest request);

    /**
     * 分页查询当前账号指定就诊人的问诊记录。
     *
     * @param patientId 可选就诊人 ID，未传时查询本人
     * @param status 可选问诊状态
     * @param pageNo 可选页码
     * @param pageSize 可选页大小
     * @return 问诊记录分页响应
     */
    ConsultationPageVO listConsultations(Long patientId, String status, Integer pageNo, Integer pageSize);
}
