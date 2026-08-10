package com.sphp.patient.consultation.mapper;

import com.sphp.patient.consultation.entity.ConsultationRecord;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * C端问诊与处方跨表数据访问接口。
 */
@Mapper
public interface ConsultationDataMapper {

    /**
     * 查询当前账号的本人就诊人 ID。
     *
     * @param userId C端用户 ID
     * @return 有效本人就诊人 ID，不存在时返回 null
     */
    Long selectConsultationSelfPatientId(@Param("userId") Long userId);

    /**
     * 判断就诊人是否存在且未被软删除。
     *
     * @param patientId 就诊人 ID
     * @return 存在时返回 true
     */
    boolean existsConsultationActivePatient(@Param("patientId") Long patientId);

    /**
     * 判断当前账号是否拥有有效就诊人关系。
     *
     * @param userId C端用户 ID
     * @param patientId 就诊人 ID
     * @return 存在有效关系时返回 true
     */
    boolean hasConsultationActivePatientRelation(@Param("userId") Long userId, @Param("patientId") Long patientId);

    /**
     * 查询拥有有效就诊人关系的 C 端账号，用于向其推送医生消息。
     *
     * @param patientId 就诊人 ID
     * @return C端账号 ID 列表
     */
    List<Long> selectConsultationPatientUserIds(@Param("patientId") Long patientId);

    /**
     * 锁定当前 C 端账号，串行化同一账号的预问诊提交。
     *
     * @param userId C 端用户 ID
     * @return 已锁定用户 ID，不存在时返回 null
     */
    Long lockConsultationUser(@Param("userId") Long userId);

    /**
     * 判断医生是否启用且未被软删除。
     *
     * @param doctorId 医生 ID
     * @return 医生可接诊时返回 true
     */
    boolean existsConsultationAvailableDoctor(@Param("doctorId") Long doctorId);

    /**
     * 查询同一患者和医生是否存在活动在线问诊。
     *
     * @param patientId 本人患者 ID
     * @param doctorId 接诊医生 ID
     * @return 存在无挂号关联的待接诊或进行中问诊时返回 true
     */
    boolean existsOnlineConsultationActiveRecord(@Param("patientId") Long patientId, @Param("doctorId") Long doctorId);

    /**
     * 查询本人有效过敏史，用于预问诊健康档案快照。
     *
     * @param patientId 本人患者 ID
     * @return 过敏史快照列表
     */
    List<ConsultationAllergySnapshotRecord> selectConsultationAllergySnapshots(@Param("patientId") Long patientId);

    /**
     * 查询本人有效既往史，用于预问诊健康档案快照。
     *
     * @param patientId 本人患者 ID
     * @return 既往史快照列表
     */
    List<ConsultationMedicalHistorySnapshotRecord> selectConsultationMedicalHistorySnapshots(@Param("patientId") Long patientId);

    /**
     * 插入带 JSONB 附件的问诊记录。
     *
     * @param consultationRecord 待插入问诊记录
     * @return 受影响行数
     */
    int insertConsultationRecord(@Param("record") ConsultationRecord consultationRecord);

    /**
     * 分页查询指定就诊人的问诊记录。
     *
     * @param patientId 就诊人 ID
     * @param status 可选问诊状态
     * @param limit 页大小
     * @param offset 分页偏移量
     * @return 问诊列表投影
     */
    List<ConsultationListRecord> selectConsultationList(@Param("patientId") Long patientId,
                                                        @Param("status") String status,
                                                        @Param("limit") int limit,
                                                        @Param("offset") long offset);

    /**
     * 统计指定就诊人的问诊记录数。
     *
     * @param patientId 就诊人 ID
     * @param status 可选问诊状态
     * @return 记录总数
     */
    long countConsultationList(@Param("patientId") Long patientId, @Param("status") String status);

    /**
     * 查询问诊详情及医生展示信息。
     *
     * @param consultationId 问诊记录 ID
     * @return 详情投影，不存在时返回 null
     */
    ConsultationDetailRecord selectConsultationDetail(@Param("consultationId") Long consultationId);

    /**
     * 锁定问诊详情，用于发送患者文字消息前的状态校验。
     *
     * @param consultationId 问诊记录 ID
     * @return 已锁定问诊详情，不存在时返回 null
     */
    ConsultationDetailRecord lockConsultationDetail(@Param("consultationId") Long consultationId);

    /**
     * 查询问诊的未删除文字消息。
     *
     * @param consultationId 问诊记录 ID
     * @return 按创建时间升序排列的消息列表
     */
    List<ConsultationMessageRecord> selectConsultationMessages(@Param("consultationId") Long consultationId);

    /**
     * 查询问诊关联的已批准处方 ID。
     *
     * @param consultationId 问诊记录 ID
     * @return 已批准处方 ID 列表
     */
    List<Long> selectConsultationApprovedPrescriptionIds(@Param("consultationId") Long consultationId);

    /**
     * 分页查询指定就诊人的已批准处方。
     *
     * @param patientId 就诊人 ID
     * @param limit 页大小
     * @param offset 分页偏移量
     * @return 处方列表投影
     */
    List<ConsultationPrescriptionRecord> selectApprovedPrescriptionList(@Param("patientId") Long patientId,
                                                                        @Param("limit") int limit,
                                                                        @Param("offset") long offset);

    /**
     * 统计指定就诊人的已批准处方数量。
     *
     * @param patientId 就诊人 ID
     * @return 已批准处方数量
     */
    long countApprovedPrescriptionList(@Param("patientId") Long patientId);

    /**
     * 查询处方资源基础归属和状态，不向客户端直接暴露。
     *
     * @param prescriptionId 处方 ID
     * @return 处方资源记录，不存在时返回 null
     */
    ConsultationPrescriptionResourceRecord selectConsultationPrescriptionResource(@Param("prescriptionId") Long prescriptionId);

    /**
     * 查询已批准处方详情及开方医生信息。
     *
     * @param prescriptionId 处方 ID
     * @return 已批准处方详情，不可展示时返回 null
     */
    ConsultationPrescriptionDetailRecord selectApprovedPrescriptionDetail(@Param("prescriptionId") Long prescriptionId);

    /**
     * 查询处方药品明细。
     *
     * @param prescriptionId 处方 ID
     * @return 处方药品明细
     */
    List<ConsultationPrescriptionItemRecord> selectConsultationPrescriptionItems(@Param("prescriptionId") Long prescriptionId);
}
