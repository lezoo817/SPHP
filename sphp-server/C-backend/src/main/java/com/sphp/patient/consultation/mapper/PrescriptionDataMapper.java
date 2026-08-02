package com.sphp.patient.consultation.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * C端处方查询与解读的数据访问接口。
 */
@Mapper
public interface PrescriptionDataMapper {

    /**
     * 查询当前用户有效的本人就诊人 ID。
     *
     * @param userId C端用户 ID
     * @return 本人就诊人 ID，不存在时返回 null
     */
    Long prescriptionSelectSelfPatientId(@Param("userId") Long userId);

    /**
     * 判断就诊人是否未被软删除。
     *
     * @param patientId 就诊人 ID
     * @return 存在时返回 true
     */
    boolean prescriptionExistsActivePatient(@Param("patientId") Long patientId);

    /**
     * 判断当前用户是否拥有有效的就诊人关系。
     *
     * @param userId C端用户 ID
     * @param patientId 就诊人 ID
     * @return 存在有效关系时返回 true
     */
    boolean prescriptionHasActivePatientRelation(@Param("userId") Long userId,
                                                 @Param("patientId") Long patientId);

    /**
     * 分页查询患者的已批准处方。
     *
     * @param patientId 就诊人 ID
     * @param limit 分页大小
     * @param offset 分页偏移量
     * @return 处方列表投影
     */
    List<PrescriptionListRecord> prescriptionSelectApprovedList(@Param("patientId") Long patientId,
                                                                 @Param("limit") int limit,
                                                                 @Param("offset") long offset);

    /**
     * 统计患者的已批准处方数量。
     *
     * @param patientId 就诊人 ID
     * @return 已批准处方数量
     */
    long prescriptionCountApprovedList(@Param("patientId") Long patientId);

    /**
     * 查询处方资源归属及状态。
     *
     * @param prescriptionId 处方 ID
     * @return 资源投影，不存在时返回 null
     */
    PrescriptionResourceRecord prescriptionSelectResource(@Param("prescriptionId") Long prescriptionId);

    /**
     * 查询已批准处方及开方医生信息。
     *
     * @param prescriptionId 处方 ID
     * @return 处方详情投影，不可展示时返回 null
     */
    PrescriptionDetailRecord prescriptionSelectApprovedDetail(@Param("prescriptionId") Long prescriptionId);

    /**
     * 查询处方药品明细。
     *
     * @param prescriptionId 处方 ID
     * @return 药品明细列表
     */
    List<PrescriptionItemRecord> prescriptionSelectItems(@Param("prescriptionId") Long prescriptionId);

    /**
     * 查询当前有效的处方解读记录。
     *
     * @param prescriptionId 处方 ID
     * @return 解读投影，不存在时返回 null
     */
    PrescriptionInterpretationRecord prescriptionSelectInterpretation(@Param("prescriptionId") Long prescriptionId);
}
