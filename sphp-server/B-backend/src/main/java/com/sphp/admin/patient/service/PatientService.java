package com.sphp.admin.patient.service;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.patient.vo.PatientDetailVO;
import com.sphp.admin.patient.vo.PatientListVO;
import com.sphp.admin.patient.vo.PatientMedicationVO;
import com.sphp.admin.patient.vo.PatientPrescriptionVO;
import com.sphp.admin.patient.vo.PatientVisitVO;

/**
 * 患者管理服务（管理员视角）。
 *
 * <p>仅返回本院就诊过的患者（通过 consult_record → doctor.hospital_id 关联过滤）；
 * 所有操作基于当前登录管理员所属医院（{@code hospital_id}）做数据隔离。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
public interface PatientService {

    /**
     * 分页查询本院就诊过的患者列表。
     *
     * @param name 姓名模糊检索（{@code null} 或空表示不过滤）
     * @param page 页码（从 1 开始）
     * @param size 每页大小
     * @return 患者分页结果
     */
    PageResult<PatientListVO> page(String name, int page, int size);

    /**
     * 查询患者详情（含过敏史、既往史）。
     *
     * @param id 患者 ID
     * @return 患者详情 VO
     * @throws com.sphp.shared.exception.BusinessException 当患者不存在或已软删时抛出
     */
    PatientDetailVO detail(Long id);

    /**
     * 分页查询患者在本院的挂号/问诊就诊记录。
     *
     * @param patientId 患者 ID
     * @param page      页码（从 1 开始）
     * @param size      每页大小
     * @return 就诊记录分页结果
     * @throws com.sphp.shared.exception.BusinessException 当患者不存在或已软删时抛出
     */
    PageResult<PatientVisitVO> visits(Long patientId, int page, int size);

    /**
     * 分页查询患者历史处方。
     *
     * @param patientId 患者 ID
     * @param page      页码（从 1 开始）
     * @param size      每页大小
     * @return 历史处方分页结果
     * @throws com.sphp.shared.exception.BusinessException 当患者不存在或已软删时抛出
     */
    PageResult<PatientPrescriptionVO> prescriptions(Long patientId, int page, int size);

    /**
     * 查询患者当前用药计划（ACTIVE / PAUSED）与未完成随访计划
     * （排除 COMPLETED / CANCELLED）。
     *
     * @param patientId 患者 ID
     * @return 当前用药与随访计划 VO
     * @throws com.sphp.shared.exception.BusinessException 当患者不存在或已软删时抛出
     */
    PatientMedicationVO medications(Long patientId);
}