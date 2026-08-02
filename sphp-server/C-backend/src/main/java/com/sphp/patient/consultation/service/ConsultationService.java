package com.sphp.patient.consultation.service;

import com.sphp.patient.consultation.dto.PreConsultationSaveRequest;
import com.sphp.patient.consultation.vo.PreConsultationSaveVO;

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
}
