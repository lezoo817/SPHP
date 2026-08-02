package com.sphp.patient.health.service.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.common.constant.ProposalConstant;
import com.sphp.patient.common.enums.ProposalFollowUpStatusEnum;
import com.sphp.patient.common.enums.ProposalMedicationActionEnum;
import com.sphp.patient.common.enums.ProposalMedicationStatusEnum;
import com.sphp.patient.health.dto.ProposalFollowUpConfirmRequest;
import com.sphp.patient.health.dto.ProposalMedicationUpdateRequest;
import com.sphp.patient.health.dto.ProposalReportCreateRequest;
import com.sphp.patient.health.entity.ProposalPatientReport;
import com.sphp.patient.health.entity.ProposalReportIndicator;
import com.sphp.patient.health.mapper.FollowUpRecord;
import com.sphp.patient.health.mapper.HealthPatientMapper;
import com.sphp.patient.health.mapper.IndicatorRecord;
import com.sphp.patient.health.mapper.MedicationRecord;
import com.sphp.patient.health.mapper.ProposalDataMapper;
import com.sphp.patient.health.mapper.ProposalReportIndicatorMapper;
import com.sphp.patient.health.mapper.ProposalReportMapper;
import com.sphp.patient.health.mapper.ReportRecord;
import com.sphp.patient.health.service.ProposalService;
import com.sphp.patient.health.vo.ProposalFollowUpVO;
import com.sphp.patient.health.vo.ProposalMedicationPlanVO;
import com.sphp.patient.health.vo.ProposalReportCreateVO;
import com.sphp.patient.health.vo.ProposalReportDetailVO;
import com.sphp.patient.health.vo.ProposalReportInterpretationVO;
import com.sphp.patient.health.vo.ProposalReportPageVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

/**
 * 健康报告、用药计划与随访计划服务实现。
 */
@Service
@RequiredArgsConstructor
public class ProposalServiceImpl implements ProposalService {

    private final HealthPatientMapper patientMapper;
    private final ProposalReportMapper reportMapper;
    private final ProposalReportIndicatorMapper indicatorMapper;
    private final ProposalDataMapper dataMapper;
    private final ObjectMapper objectMapper;

    /**
     * 为当前账号可访问的就诊人录入检查报告及其指标。
     *
     * @param request 报告录入请求
     * @return 已录入报告的 ID 与状态
     * @throws CAuthException 就诊人无权访问或持久化失败时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProposalReportCreateVO proposalCreateReport(ProposalReportCreateRequest request) {
        Long patientId = proposalResolvePatientId(request.getPatientId());
        ProposalPatientReport report = new ProposalPatientReport();
        report.setPatientId(patientId);
        report.setReportName(request.getReportName());
        report.setReportDate(request.getReportDate());
        report.setInterpretationStatus("PENDING");

        // 先创建报告主记录，确保全部指标只关联到同一份有效报告。
        if (reportMapper.insert(report) != 1) {
            throw proposalSystemError("报告录入失败");
        }
        for (ProposalReportCreateRequest.Indicator item : request.getIndicators()) {
            ProposalReportIndicator indicator = new ProposalReportIndicator();
            indicator.setReportId(report.getId());
            indicator.setName(item.getName());
            indicator.setValue(item.getValue());
            indicator.setUnit(item.getUnit());
            indicator.setReferenceRange(item.getReferenceRange());
            if (indicatorMapper.insert(indicator) != 1) {
                throw proposalSystemError("报告指标录入失败");
            }
        }
        return ProposalReportCreateVO.builder().reportId(report.getId()).status("RECORDED").build();
    }

    /**
     * 分页查询当前账号可访问就诊人的检查报告。
     *
     * @param patientId 可选就诊人 ID，未传时使用本人
     * @param pageNo 可选页码
     * @param pageSize 可选每页数量
     * @return 报告分页结果
     * @throws CAuthException 就诊人无权访问或分页参数越界时抛出
     */
    @Override
    public ProposalReportPageVO proposalListReports(Long patientId, Integer pageNo, Integer pageSize) {
        Long resolvedPatientId = proposalResolvePatientId(patientId);
        int resolvedPageNo = pageNo == null ? ProposalConstant.DEFAULT_PAGE_NO : pageNo;
        int resolvedPageSize = pageSize == null ? ProposalConstant.DEFAULT_PAGE_SIZE : pageSize;
        if (resolvedPageSize > ProposalConstant.MAX_PAGE_SIZE) {
            throw proposalBadRequest("pageSize 不能超过100");
        }

        long offset = (long) (resolvedPageNo - 1) * resolvedPageSize;
        List<ProposalReportPageVO.Item> records = dataMapper.proposalSelectReports(
                        resolvedPatientId, resolvedPageSize, offset)
                .stream()
                .map(item -> ProposalReportPageVO.Item.builder()
                        .id(item.id())
                        .reportName(item.reportName())
                        .reportDate(item.reportDate())
                        .indicatorCount(item.indicatorCount())
                        .build())
                .toList();
        return ProposalReportPageVO.builder()
                .pageNo(resolvedPageNo)
                .pageSize(resolvedPageSize)
                .total(dataMapper.proposalCountReports(resolvedPatientId))
                .records(records)
                .build();
    }

    /**
     * 查询单份检查报告及其全部指标。
     *
     * @param reportId 报告 ID
     * @return 报告详情
     * @throws CAuthException 报告不存在或当前账号无权访问时抛出
     */
    @Override
    public ProposalReportDetailVO proposalGetReport(Long reportId) {
        ReportRecord report = proposalRequireReport(reportId);
        List<ProposalReportDetailVO.Indicator> indicators = dataMapper.proposalSelectIndicators(reportId)
                .stream()
                .map(this::proposalToIndicatorVO)
                .toList();
        return ProposalReportDetailVO.builder()
                .id(report.id())
                .reportName(report.reportName())
                .reportDate(report.reportDate())
                .indicators(indicators)
                .build();
    }

    /**
     * 读取已生成的报告解读内容，不触发新的解读生成任务。
     *
     * @param reportId 报告 ID
     * @return 已准备好的报告解读
     * @throws CAuthException 报告无权访问、解读未准备完成或 JSON 无法解析时抛出
     */
    @Override
    public ProposalReportInterpretationVO proposalGetReportInterpretation(Long reportId) {
        ReportRecord report = proposalRequireReport(reportId);
        if (!"READY".equals(report.interpretationStatus()) || report.interpretation() == null) {
            throw new CAuthException(ErrorCodeEnum.BUSINESS_STATUS_CONFLICT, HttpStatus.CONFLICT, "报告解读尚未生成");
        }
        try {
            return objectMapper.readValue(report.interpretation(), ProposalReportInterpretationVO.class);
        } catch (Exception exception) {
            throw proposalSystemError("报告解读读取失败");
        }
    }

    /**
     * 查询当前账号可访问就诊人的用药计划。
     *
     * @param patientId 可选就诊人 ID，未传时使用本人
     * @param status 可选计划状态
     * @return 用药计划列表
     * @throws CAuthException 状态非法或就诊人无权访问时抛出
     */
    @Override
    public List<ProposalMedicationPlanVO> proposalListMedicationPlans(Long patientId, String status) {
        Long resolvedPatientId = proposalResolvePatientId(patientId);
        proposalValidateEnumValue(status, ProposalMedicationStatusEnum.values());
        return dataMapper.proposalSelectMedications(resolvedPatientId, status)
                .stream()
                .map(this::proposalToMedicationVO)
                .toList();
    }

    /**
     * 按指定动作更新用药计划状态。
     *
     * @param planId 用药计划 ID
     * @param request 状态变更请求
     * @return 更新后的计划摘要
     * @throws CAuthException 计划不存在、无权访问或状态转换非法时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProposalMedicationPlanVO proposalUpdateMedicationPlan(Long planId,
                                                                   ProposalMedicationUpdateRequest request) {
        MedicationRecord medication = dataMapper.proposalSelectMedication(planId);
        if (medication == null) {
            throw proposalNotFound("用药计划不存在");
        }
        Long patientId = proposalRequireAccessiblePatient(medication.patientId());
        OffsetDateTime now = OffsetDateTime.now();
        String targetStatus;
        OffsetDateTime nextReminderAt;
        OffsetDateTime endAt;

        if (request.getAction() == ProposalMedicationActionEnum.PAUSE && "ACTIVE".equals(medication.status())) {
            targetStatus = "PAUSED";
            nextReminderAt = null;
            endAt = null;
        } else if (request.getAction() == ProposalMedicationActionEnum.RESUME && "PAUSED".equals(medication.status())) {
            targetStatus = "ACTIVE";
            nextReminderAt = now;
            endAt = null;
        } else if (request.getAction() == ProposalMedicationActionEnum.COMPLETE
                && ("ACTIVE".equals(medication.status()) || "PAUSED".equals(medication.status()))) {
            targetStatus = "COMPLETED";
            nextReminderAt = null;
            endAt = now;
        } else {
            throw proposalConflict("当前用药计划状态不允许该操作");
        }

        // 将读取时状态带入条件更新，避免并发请求覆盖既有状态转换。
        if (dataMapper.proposalUpdateMedication(planId, patientId, targetStatus, medication.status(),
                nextReminderAt, endAt, now) != 1) {
            throw proposalConflict("当前用药计划状态已变化");
        }
        return ProposalMedicationPlanVO.builder()
                .id(planId)
                .drugName(medication.drugName())
                .dosage(medication.dosage())
                .frequency(medication.frequency())
                .nextReminderAt(nextReminderAt)
                .status(targetStatus)
                .build();
    }

    /**
     * 查询当前账号可访问就诊人的随访计划。
     *
     * @param patientId 可选就诊人 ID，未传时使用本人
     * @param status 可选随访状态
     * @return 随访计划列表
     * @throws CAuthException 状态非法或就诊人无权访问时抛出
     */
    @Override
    public List<ProposalFollowUpVO> proposalListFollowUps(Long patientId, String status) {
        Long resolvedPatientId = proposalResolvePatientId(patientId);
        proposalValidateEnumValue(status, ProposalFollowUpStatusEnum.values());
        return dataMapper.proposalSelectFollowUps(resolvedPatientId, status)
                .stream()
                .map(this::proposalToFollowUpVO)
                .toList();
    }

    /**
     * 确认待确认状态的随访计划，并设置提醒时间。
     *
     * @param followUpId 随访计划 ID
     * @param request 确认请求，未传提醒时间时使用 dueAt
     * @return 已确认的随访计划
     * @throws CAuthException 随访不存在、无权访问或状态已变化时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ProposalFollowUpVO proposalConfirmFollowUp(Long followUpId, ProposalFollowUpConfirmRequest request) {
        FollowUpRecord followUp = dataMapper.proposalSelectFollowUp(followUpId);
        if (followUp == null) {
            throw proposalNotFound("随访计划不存在");
        }
        Long patientId = proposalRequireAccessiblePatient(followUp.patientId());
        if (!"PENDING_CONFIRM".equals(followUp.status())) {
            throw proposalConflict("当前随访计划不可确认");
        }
        OffsetDateTime remindAt = request.getRemindAt() == null ? followUp.dueAt() : request.getRemindAt();
        if (dataMapper.proposalConfirmFollowUp(followUpId, patientId, remindAt, OffsetDateTime.now()) != 1) {
            throw proposalConflict("当前随访计划状态已变化");
        }
        return ProposalFollowUpVO.builder()
                .id(followUp.id())
                .type(followUp.type())
                .dueAt(followUp.dueAt())
                .content(followUp.content())
                .status("CONFIRMED")
                .remindAt(remindAt)
                .build();
    }

    /**
     * 读取报告并校验其患者归属。
     *
     * @param reportId 报告 ID
     * @return 已授权访问的报告记录
     * @throws CAuthException 报告不存在或无权访问时抛出
     */
    private ReportRecord proposalRequireReport(Long reportId) {
        ReportRecord report = dataMapper.proposalSelectReport(reportId);
        if (report == null) {
            throw proposalNotFound("检查报告不存在");
        }
        proposalRequireAccessiblePatient(report.patientId());
        return report;
    }

    /**
     * 解析请求目标患者并校验当前账号拥有有效关系。
     *
     * @param patientId 可选就诊人 ID
     * @return 当前账号可访问的有效就诊人 ID
     * @throws CAuthException 患者不存在或无权访问时抛出
     */
    private Long proposalResolvePatientId(Long patientId) {
        Long userId = CUserContext.getRequired().userId();
        Long resolvedPatientId = patientId == null ? patientMapper.selectSelfPatientId(userId) : patientId;
        if (resolvedPatientId == null || !patientMapper.existsActivePatient(resolvedPatientId)) {
            throw proposalNotFound("就诊人不存在或已停用");
        }
        if (!patientMapper.hasActivePatientRelation(userId, resolvedPatientId)) {
            throw proposalForbidden("无权访问该就诊人");
        }
        return resolvedPatientId;
    }

    /**
     * 校验资源反查得到的患者可被当前账号访问。
     *
     * @param patientId 资源所属患者 ID
     * @return 已授权访问的患者 ID
     * @throws CAuthException 患者不存在或无权访问时抛出
     */
    private Long proposalRequireAccessiblePatient(Long patientId) {
        return proposalResolvePatientId(patientId);
    }

    /**
     * 将指标投影转换为对外详情对象。
     *
     * @param indicator 指标投影
     * @return 指标详情
     */
    private ProposalReportDetailVO.Indicator proposalToIndicatorVO(IndicatorRecord indicator) {
        return ProposalReportDetailVO.Indicator.builder()
                .name(indicator.name())
                .value(indicator.value())
                .unit(indicator.unit())
                .referenceRange(indicator.referenceRange())
                .build();
    }

    /**
     * 将用药计划投影转换为对外对象。
     *
     * @param medication 用药计划投影
     * @return 用药计划对象
     */
    private ProposalMedicationPlanVO proposalToMedicationVO(MedicationRecord medication) {
        return ProposalMedicationPlanVO.builder()
                .id(medication.id())
                .drugName(medication.drugName())
                .dosage(medication.dosage())
                .frequency(medication.frequency())
                .nextReminderAt(medication.nextReminderAt())
                .status(medication.status())
                .build();
    }

    /**
     * 将随访计划投影转换为对外对象。
     *
     * @param followUp 随访计划投影
     * @return 随访计划对象
     */
    private ProposalFollowUpVO proposalToFollowUpVO(FollowUpRecord followUp) {
        return ProposalFollowUpVO.builder()
                .id(followUp.id())
                .type(followUp.type())
                .dueAt(followUp.dueAt())
                .content(followUp.content())
                .status(followUp.status())
                .remindAt(followUp.remindAt())
                .build();
    }

    /**
     * 校验传入的状态值是否属于指定枚举。
     *
     * @param value 待校验状态值
     * @param values 合法枚举集合
     * @param <T> 枚举类型
     * @throws CAuthException 状态值不合法时抛出
     */
    private <T extends Enum<T>> void proposalValidateEnumValue(String value, T[] values) {
        if (value != null && !value.isBlank()
                && Arrays.stream(values).noneMatch(item -> item.name().equals(value))) {
            throw proposalBadRequest("状态不在允许范围内");
        }
    }

    /**
     * 创建资源不存在异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 404 业务异常
     */
    private CAuthException proposalNotFound(String message) {
        return new CAuthException(ErrorCodeEnum.INVALID_USER_INPUT, HttpStatus.NOT_FOUND, message);
    }

    /**
     * 创建资源越权异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 403 业务异常
     */
    private CAuthException proposalForbidden(String message) {
        return new CAuthException(ErrorCodeEnum.UNAUTHORIZED, HttpStatus.FORBIDDEN, message);
    }

    /**
     * 创建状态冲突异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 409 业务异常
     */
    private CAuthException proposalConflict(String message) {
        return new CAuthException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID, HttpStatus.CONFLICT, message);
    }

    /**
     * 创建参数范围异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 400 业务异常
     */
    private CAuthException proposalBadRequest(String message) {
        return new CAuthException(ErrorCodeEnum.PARAMETER_OUT_OF_RANGE, HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 创建系统异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 500 业务异常
     */
    private CAuthException proposalSystemError(String message) {
        return new CAuthException(ErrorCodeEnum.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
