package com.sphp.patient.health.service;

import com.sphp.patient.health.vo.HealthRecordVO;

/**
 * C端健康档案服务。
 */
public interface HealthService {

    /**
     * 查询当前账号可访问就诊人的健康档案。
     *
     * @param patientId 可选就诊人 ID，未传时查询本人
     * @return 健康档案资料、过敏史、既往史和摘要
     */
    HealthRecordVO getHealthRecord(Long patientId);
}
