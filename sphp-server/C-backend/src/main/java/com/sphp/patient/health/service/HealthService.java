package com.sphp.patient.health.service;

import com.sphp.patient.health.vo.HealthRecordVO;
import com.sphp.patient.health.dto.AllergyCreateRequest;
import com.sphp.patient.health.dto.AllergyUpdateRequest;
import com.sphp.patient.health.dto.MedicalHistoryCreateRequest;
import com.sphp.patient.health.dto.MedicalHistoryUpdateRequest;
import com.sphp.patient.health.vo.AllergyCreateVO;
import com.sphp.patient.health.vo.AllergyUpdateVO;
import com.sphp.patient.health.vo.MedicalHistoryCreateVO;
import com.sphp.patient.health.vo.MedicalHistoryUpdateVO;
import com.sphp.patient.health.vo.HealthRecordDeleteVO;

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

    /**
     * 为当前账号可访问就诊人新增过敏史。
     *
     * @param request 新增过敏史请求
     * @return 新建过敏史信息
     */
    AllergyCreateVO createAllergy(AllergyCreateRequest request);

    /**
     * 更新当前账号可访问就诊人的过敏史。
     *
     * @param allergyId 过敏史 ID，所属就诊人由服务端反查
     * @param request 更新过敏史请求
     * @return 更新后的过敏史信息
     */
    AllergyUpdateVO updateAllergy(Long allergyId, AllergyUpdateRequest request);

    /**
     * 软删除当前账号可访问就诊人的过敏史。
     *
     * @param allergyId 过敏史 ID，所属就诊人由服务端反查
     * @return 删除记录 ID 和删除时间
     */
    HealthRecordDeleteVO deleteAllergy(Long allergyId);

    /**
     * 为当前账号可访问就诊人新增既往史。
     *
     * @param request 新增既往史请求
     * @return 新建既往史信息
     */
    MedicalHistoryCreateVO createMedicalHistory(MedicalHistoryCreateRequest request);

    /**
     * 更新当前账号可访问就诊人的既往史。
     *
     * @param historyId 既往史 ID，所属就诊人由服务端反查
     * @param request 更新既往史请求
     * @return 更新后的既往史信息
     */
    MedicalHistoryUpdateVO updateMedicalHistory(Long historyId, MedicalHistoryUpdateRequest request);

    /**
     * 软删除当前账号可访问就诊人的既往史。
     *
     * @param historyId 既往史 ID，所属就诊人由服务端反查
     * @return 删除记录 ID 和删除时间
     */
    HealthRecordDeleteVO deleteMedicalHistory(Long historyId);
}
