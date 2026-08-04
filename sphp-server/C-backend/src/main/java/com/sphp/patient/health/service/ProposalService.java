package com.sphp.patient.health.service;

import com.sphp.patient.health.dto.ProposalFollowUpConfirmRequest;
import com.sphp.patient.health.dto.ProposalMedicationUpdateRequest;
import com.sphp.patient.health.dto.ProposalReportCreateRequest;
import com.sphp.patient.health.vo.ProposalFollowUpVO;
import com.sphp.patient.health.vo.ProposalMedicationPlanVO;
import com.sphp.patient.health.vo.ProposalReportCreateVO;
import com.sphp.patient.health.vo.ProposalReportDetailVO;
import com.sphp.patient.health.vo.ProposalReportInterpretationVO;
import com.sphp.patient.health.vo.ProposalReportPageVO;
import com.sphp.patient.health.vo.ProposalMedicalRecordPageVO;

import java.util.List;

/**
 * 健康报告、用药和随访服务。
 */
public interface ProposalService {

    /**
     * 分页查询当前账号可访问就诊人的医生病历。
     *
     * @param patientId 可选就诊人 ID，未传时使用本人
     * @param pageNo 可选页码
     * @param pageSize 可选每页数量
     * @return 病历分页结果
     */
    ProposalMedicalRecordPageVO proposalListMedicalRecords(Long patientId, Integer pageNo, Integer pageSize);

    /**
     * 录入检查报告。
     *
     * <p>历史兼容能力，新 C 端报告应读取 B 端医生保存的病历。</p>
     *
     * @param request 报告录入请求
     * @return 已录入报告结果
     */
    @Deprecated(since = "2026-08", forRemoval = false)
    ProposalReportCreateVO proposalCreateReport(ProposalReportCreateRequest request);

    /**
     * 分页查询检查报告。
     *
     * @param patientId 可选就诊人 ID
     * @param pageNo 可选页码
     * @param pageSize 可选每页数量
     * @return 报告分页结果
     */
    ProposalReportPageVO proposalListReports(Long patientId, Integer pageNo, Integer pageSize);

    /**
     * 查询检查报告详情。
     *
     * @param reportId 报告 ID
     * @return 报告详情
     */
    ProposalReportDetailVO proposalGetReport(Long reportId);

    /**
     * 查询已生成的报告解读。
     *
     * @param reportId 报告 ID
     * @return 报告解读
     */
    ProposalReportInterpretationVO proposalGetReportInterpretation(Long reportId);

    /**
     * 查询用药计划。
     *
     * @param patientId 可选就诊人 ID
     * @param status 可选计划状态
     * @return 用药计划列表
     */
    List<ProposalMedicationPlanVO> proposalListMedicationPlans(Long patientId, String status);

    /**
     * 更新用药计划状态。
     *
     * @param planId 用药计划 ID
     * @param request 状态变更请求
     * @return 更新后的用药计划
     */
    ProposalMedicationPlanVO proposalUpdateMedicationPlan(Long planId, ProposalMedicationUpdateRequest request);

    /**
     * 查询随访计划。
     *
     * @param patientId 可选就诊人 ID
     * @param status 可选随访状态
     * @return 随访计划列表
     */
    List<ProposalFollowUpVO> proposalListFollowUps(Long patientId, String status);

    /**
     * 确认随访计划。
     *
     * @param followUpId 随访计划 ID
     * @param request 随访确认请求
     * @return 已确认随访计划
     */
    ProposalFollowUpVO proposalConfirmFollowUp(Long followUpId, ProposalFollowUpConfirmRequest request);
}
