package com.sphp.admin.patient.service;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.patient.vo.PatientDetailVO;
import com.sphp.admin.patient.vo.PatientListVO;
import com.sphp.admin.patient.vo.PatientMedicationVO;
import com.sphp.admin.patient.vo.PatientPrescriptionVO;
import com.sphp.admin.patient.vo.PatientVisitVO;

/**
 * 患者管理服务（系分 §5.8）。
 */
public interface PatientService {

    /**
     * 分页查询本院患者列表。
     *
     * @param name 姓名模糊检索（可空）
     * @param page 页码
     * @param size 每页大小
     */
    PageResult<PatientListVO> page(String name, int page, int size);

    /**
     * 查询患者详情（含过敏史、既往史）。
     *
     * @param id 患者ID
     */
    PatientDetailVO detail(Long id);

    /**
     * 分页查询患者就诊记录。
     *
     * @param patientId 患者ID
     * @param page      页码
     * @param size      每页大小
     */
    PageResult<PatientVisitVO> visits(Long patientId, int page, int size);

    /**
     * 分页查询患者历史处方。
     *
     * @param patientId 患者ID
     * @param page      页码
     * @param size      每页大小
     */
    PageResult<PatientPrescriptionVO> prescriptions(Long patientId, int page, int size);

    /**
     * 查询患者当前用药与随访计划。
     *
     * @param patientId 患者ID
     */
    PatientMedicationVO medications(Long patientId);
}