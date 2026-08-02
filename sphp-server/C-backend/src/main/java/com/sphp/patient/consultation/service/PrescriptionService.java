package com.sphp.patient.consultation.service;

import com.sphp.patient.consultation.vo.ConsultationPrescriptionDetailVO;
import com.sphp.patient.consultation.vo.ConsultationPrescriptionPageVO;
import com.sphp.patient.consultation.vo.PrescriptionInterpretationVO;

/**
 * C端处方查询与解读服务。
 */
public interface PrescriptionService {

    /**
     * 分页查询当前账号可访问患者的已批准处方。
     *
     * @param patientId 可选就诊人 ID，未传时使用本人
     * @param pageNo 可选页码
     * @param pageSize 可选每页数量
     * @return 已批准处方分页结果
     */
    ConsultationPrescriptionPageVO prescriptionList(Long patientId, Integer pageNo, Integer pageSize);

    /**
     * 查询当前账号可访问的已批准处方详情。
     *
     * @param prescriptionId 处方 ID
     * @return 处方详情及药品明细
     */
    ConsultationPrescriptionDetailVO prescriptionGetDetail(Long prescriptionId);

    /**
     * 查询当前账号可访问处方的已生成解读。
     *
     * @param prescriptionId 处方 ID
     * @return READY 状态的处方解读
     */
    PrescriptionInterpretationVO prescriptionGetInterpretation(Long prescriptionId);
}
