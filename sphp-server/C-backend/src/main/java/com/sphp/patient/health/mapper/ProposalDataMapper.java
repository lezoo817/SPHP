package com.sphp.patient.health.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * 健康报告、用药计划和随访计划的跨表数据访问接口。
 */
@Mapper
public interface ProposalDataMapper {

    /**
     * 分页查询已完成且保存病历正文的问诊记录。
     *
     * @param patientId 就诊人 ID
     * @param limit 分页大小
     * @param offset 分页偏移量
     * @return 医生病历列表投影
     */
    List<ConsultationMedicalRecordListRecord> proposalSelectConsultationMedicalRecords(
            @Param("patientId") Long patientId, @Param("limit") int limit, @Param("offset") long offset);

    /**
     * 统计已完成且保存病历正文的问诊记录数量。
     *
     * @param patientId 就诊人 ID
     * @return 可见病历总数
     */
    long proposalCountConsultationMedicalRecords(@Param("patientId") Long patientId);

    /**
     * 分页查询已完成且已保存病历的问诊记录。
     *
     * @param patientId 患者 ID
     * @param limit 分页大小
     * @param offset 分页偏移量
     * @return 医生病历报告列表投影
     */
    List<ConsultationReportListRecord> proposalSelectConsultationReports(@Param("patientId") Long patientId,
                                                                          @Param("limit") int limit,
                                                                          @Param("offset") long offset);

    /**
     * 统计已完成且已保存病历的问诊记录数量。
     *
     * @param patientId 患者 ID
     * @return 医生病历报告总数
     */
    long proposalCountConsultationReports(@Param("patientId") Long patientId);

    /**
     * 按报告 ID 查询可向患者展示的医生病历。
     *
     * @param reportId 问诊记录 ID，即 C 端报告 ID
     * @return 医生病历报告投影，不存在或不可展示时返回 null
     */
    ConsultationReportRecord proposalSelectConsultationReport(@Param("reportId") Long reportId);

    /**
     * 按问诊记录读取已准备好的医生病历解读。
     *
     * @param reportId 问诊记录 ID，即 C 端报告 ID
     * @return 已准备好的解读投影，不存在或未准备完成时返回 null
     */
    ConsultationReportInterpretationRecord proposalSelectReadyConsultationReportInterpretation(
            @Param("reportId") Long reportId);

    /**
     * 按患者和可选状态查询用药计划。
     *
     * @param patientId 患者 ID
     * @param status 可选计划状态
     * @return 用药计划投影列表
     */
    List<MedicationRecord> proposalSelectMedications(@Param("patientId") Long patientId,
                                                       @Param("status") String status);

    /**
     * 按 ID 查询未删除的用药计划资源。
     *
     * @param planId 用药计划 ID
     * @return 用药计划投影，不存在时返回 null
     */
    MedicationRecord proposalSelectMedication(@Param("planId") Long planId);

    /**
     * 按患者与原状态条件更新用药计划，防止并发状态覆盖。
     *
     * @param planId 用药计划 ID
     * @param patientId 资源所属患者 ID
     * @param status 更新后的状态
     * @param expectedStatus 读取时的原状态
     * @param nextRemindAt 更新后的下次提醒时间
     * @param endAt 更新后的结束时间
     * @param now 当前更新时间
     * @return 受影响行数
     */
    int proposalUpdateMedication(@Param("planId") Long planId,
                                 @Param("patientId") Long patientId,
                                 @Param("status") String status,
                                 @Param("expectedStatus") String expectedStatus,
                                 @Param("nextRemindAt") OffsetDateTime nextRemindAt,
                                 @Param("endAt") OffsetDateTime endAt,
                                 @Param("now") OffsetDateTime now);

    /**
     * 按患者和可选状态查询随访计划。
     *
     * @param patientId 患者 ID
     * @param status 可选随访状态
     * @return 随访计划投影列表
     */
    List<FollowUpRecord> proposalSelectFollowUps(@Param("patientId") Long patientId,
                                                  @Param("status") String status);

    /**
     * 按 ID 查询未删除的随访计划资源。
     *
     * @param followUpId 随访计划 ID
     * @return 随访计划投影，不存在时返回 null
     */
    FollowUpRecord proposalSelectFollowUp(@Param("followUpId") Long followUpId);

    /**
     * 将待确认随访计划原子更新为已确认状态。
     *
     * @param followUpId 随访计划 ID
     * @param patientId 资源所属患者 ID
     * @param remindAt 确认后的提醒时间
     * @param now 当前更新时间
     * @return 受影响行数
     */
    int proposalConfirmFollowUp(@Param("followUpId") Long followUpId,
                                @Param("patientId") Long patientId,
                                @Param("remindAt") OffsetDateTime remindAt,
                                @Param("now") OffsetDateTime now);
}
