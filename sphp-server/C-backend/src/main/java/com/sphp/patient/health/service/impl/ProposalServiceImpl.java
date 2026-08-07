package com.sphp.patient.health.service.impl;

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
import com.sphp.patient.health.mapper.ConsultationReportListRecord;
import com.sphp.patient.health.mapper.ConsultationMedicalRecordListRecord;
import com.sphp.patient.health.mapper.ConsultationMedicalRecordRecord;
import com.sphp.patient.health.mapper.ConsultationReportInterpretationRecord;
import com.sphp.patient.health.mapper.ConsultationReportRecord;
import com.sphp.patient.health.mapper.FollowUpRecord;
import com.sphp.patient.health.mapper.HealthPatientMapper;
import com.sphp.patient.health.mapper.MedicationRecord;
import com.sphp.patient.health.mapper.ProposalDataMapper;
import com.sphp.patient.health.mapper.ProposalReportIndicatorMapper;
import com.sphp.patient.health.mapper.ProposalReportMapper;
import com.sphp.patient.health.service.ProposalService;
import com.sphp.patient.health.support.ProposalMedicationReminderSupport;
import com.sphp.patient.health.vo.ProposalFollowUpVO;
import com.sphp.patient.health.vo.ProposalMedicationPlanVO;
import com.sphp.patient.health.vo.ProposalReportCreateVO;
import com.sphp.patient.health.vo.ProposalReportDetailVO;
import com.sphp.patient.health.vo.ProposalReportInterpretationVO;
import com.sphp.patient.health.vo.ProposalReportPageVO;
import com.sphp.patient.health.vo.ProposalMedicalRecordPageVO;
import com.sphp.patient.health.vo.ProposalMedicalRecordDetailVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;

import static com.sphp.patient.common.constant.ProposalConstant.*;
import static com.sphp.patient.common.enums.ProposalMedicationActionEnum.*;
import static com.sphp.patient.health.support.ProposalMedicationReminderSupport.proposalCalculateNextReminderAt;
import static com.sphp.patient.health.support.ProposalMedicationReminderSupport.proposalParseReminderTimes;
import static com.sphp.patient.health.support.ProposalMedicationReminderSupport.proposalResolveReminderTimes;
import static com.sphp.patient.health.support.ProposalMedicationReminderSupport.proposalSerializeReminderTimes;
import static com.sphp.shared.common.enums.ErrorCodeEnum.*;

/**
 * 健康报告、用药计划与随访计划服务实现。
 */
@Service
@RequiredArgsConstructor
public class ProposalServiceImpl implements ProposalService {
    // 就诊人数据
    private final HealthPatientMapper patientMapper;
    // 检查报告数据
    private final ProposalReportMapper reportMapper;
    // 检查报告指标数据
    private final ProposalReportIndicatorMapper indicatorMapper;
    // 健康模块跨表查询数据
    private final ProposalDataMapper dataMapper;

    /**
     * 分页查询当前账号可访问就诊人的医生病历。
     *
     * @param patientId 可选就诊人 ID，未传时使用本人
     * @param pageNo 可选页码
     * @param pageSize 可选每页数量
     * @param recentDays 可选最近天数，仅允许 1 至 30 天
     * @return 病历分页结果
     * @throws CAuthException 就诊人无权访问或分页参数越界时抛出
     */
    @Override
    public ProposalMedicalRecordPageVO proposalListMedicalRecords(Long patientId, Integer pageNo, Integer pageSize,
                                                                   Integer recentDays) {
        // 统一解析本人或当前账号已绑定的家庭成员，防止跨账号读取病历。
        Long resolvedPatientId = proposalResolvePatientId(patientId);
        int resolvedPageNo = pageNo == null ? DEFAULT_PAGE_NO : pageNo;
        int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (resolvedPageSize > MAX_PAGE_SIZE) {
            throw proposalBadRequest("pageSize 不能超过100");
        }

        long offset = (long) (resolvedPageNo - 1) * resolvedPageSize;
        // 最近病历按服务端完成时间筛选，统一以服务端时钟定义最近窗口。
        OffsetDateTime completedSince = recentDays == null ? null : OffsetDateTime.now().minusDays(recentDays);
        // 仅返回已完成且医生已保存正文的病历，不向患者暴露接诊过程中的草稿。
        List<ConsultationMedicalRecordListRecord> medicalRecords =
                dataMapper.proposalSelectConsultationMedicalRecords(resolvedPatientId, resolvedPageSize, offset, completedSince);
        List<ProposalMedicalRecordPageVO.Item> records = medicalRecords.stream()
                .map(item -> ProposalMedicalRecordPageVO.Item.builder()
                        .id(item.id())
                        .patientId(item.patientId())
                        .doctorName(item.doctorName())
                        .departmentName(item.departmentName())
                        .completedAt(item.completedAt())
                        .updatedAt(item.updatedAt())
                        .build())
                .toList();
        return ProposalMedicalRecordPageVO.builder()
                .pageNo(resolvedPageNo)
                .pageSize(resolvedPageSize)
                .total(dataMapper.proposalCountConsultationMedicalRecords(resolvedPatientId, completedSince))
                .records(records)
                .build();
    }

    /**
     * 查询单份当前账号可访问的医生病历。
     *
     * @param consultId 问诊记录 ID，即病历 ID
     * @return 病历详情
     * @throws CAuthException 病历不存在、暂不可展示或当前账号无权访问时抛出
     */
    @Override
    public ProposalMedicalRecordDetailVO proposalGetMedicalRecord(Long consultId) {
        // 先以病历 ID 查询受可见性条件约束的问诊记录，避免返回未完成或空正文病历。
        ConsultationMedicalRecordRecord medicalRecord =
                dataMapper.proposalSelectConsultationMedicalRecord(consultId);
        if (medicalRecord == null) {
            throw proposalNotFound("病历不存在或暂不可查看");
        }
        // 由病历反查就诊人归属，禁止通过病历 ID 跨账号读取医疗记录。
        proposalRequireAccessiblePatient(medicalRecord.patientId());
        return ProposalMedicalRecordDetailVO.builder()
                .id(medicalRecord.id())
                .patientId(medicalRecord.patientId())
                .doctorId(medicalRecord.doctorId())
                .doctorName(medicalRecord.doctorName())
                .departmentName(medicalRecord.departmentName())
                .doctorNote(medicalRecord.doctorNote())
                .startedAt(medicalRecord.startedAt())
                .completedAt(medicalRecord.completedAt())
                .updatedAt(medicalRecord.updatedAt())
                .build();
    }

    /**
     * 为当前账号可访问的就诊人录入检查报告及其指标。
     *
     * <p>历史兼容能力，新报告不再由 C 端自主录入。</p>
     *
     * @param request 报告录入请求
     * @return 已录入报告的 ID 与状态
     * @throws CAuthException 就诊人无权访问或持久化失败时抛出
     */
    @Override
    @Deprecated(since = "2026-08", forRemoval = false)
    @Transactional(rollbackFor = Exception.class)
    public ProposalReportCreateVO proposalCreateReport(ProposalReportCreateRequest request) {
        // 解析并检查就诊人 ID
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
        return ProposalReportCreateVO.builder()
                .reportId(report.getId())
                .status("RECORDED")
                .build();
    }

    /**
     * 分页查询当前账号可访问就诊人的已完成医生病历报告。
     *
     * @param patientId 可选就诊人 ID，未传时使用本人
     * @param pageNo 可选页码
     * @param pageSize 可选每页数量
     * @return 报告分页结果
     * @throws CAuthException 就诊人无权访问或分页参数越界时抛出
     */
    @Override
    @Deprecated(since = "2026-08", forRemoval = false)
    public ProposalReportPageVO proposalListReports(Long patientId, Integer pageNo, Integer pageSize) {
        // 解析并检查就诊人 ID
        Long resolvedPatientId = proposalResolvePatientId(patientId);
        int resolvedPageNo = pageNo == null ? DEFAULT_PAGE_NO : pageNo;
        int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (resolvedPageSize > MAX_PAGE_SIZE) {
            throw proposalBadRequest("pageSize 不能超过100");
        }

        long offset = (long) (resolvedPageNo - 1) * resolvedPageSize;
        // 仅查询已完成且医生已保存正文的问诊记录，避免向患者展示接诊中的草稿病历。
        List<ConsultationReportListRecord> reportRecords = dataMapper.proposalSelectConsultationReports(
                resolvedPatientId, resolvedPageSize, offset);
        List<ProposalReportPageVO.Item> records = reportRecords.stream()
                .map(item -> ProposalReportPageVO.Item.builder()
                        .id(item.id())
                        .patientId(item.patientId())
                        .doctorName(item.doctorName())
                        .departmentName(item.departmentName())
                        .completedAt(item.completedAt())
                        .updatedAt(item.updatedAt())
                        .build())
                .toList();
        return ProposalReportPageVO.builder()
                .pageNo(resolvedPageNo)
                .pageSize(resolvedPageSize)
                .total(dataMapper.proposalCountConsultationReports(resolvedPatientId))
                .records(records)
                .build();
    }

    /**
     * 查询单份已完成的医生病历报告。
     *
     * @param reportId 报告 ID
     * @return 报告详情
     * @throws CAuthException 报告不存在或当前账号无权访问时抛出
     */
    @Override
    @Deprecated(since = "2026-08", forRemoval = false)
    public ProposalReportDetailVO proposalGetReport(Long reportId) {
        // 资源反查并校验就诊人归属，禁止通过报告 ID 跨账号读取病历。
        ConsultationReportRecord report = proposalRequireConsultationReport(reportId);
        return ProposalReportDetailVO.builder()
                .id(report.id())
                .patientId(report.patientId())
                .doctorId(report.doctorId())
                .doctorName(report.doctorName())
                .departmentName(report.departmentName())
                .doctorNote(report.doctorNote())
                .startedAt(report.startedAt())
                .completedAt(report.completedAt())
                .updatedAt(report.updatedAt())
                .build();
    }

    /**
     * 读取已生成的医生病历解读内容，不触发新的解读生成任务。
     *
     * @param reportId 报告 ID
     * @return 已准备好的报告解读
     * @throws CAuthException 报告无权访问或解读未准备完成时抛出
     */
    @Override
    @Deprecated(since = "2026-08", forRemoval = false)
    public ProposalReportInterpretationVO proposalGetReportInterpretation(Long reportId) {
        // 先校验病历可展示及患者归属，解读记录不能单独绕过报告访问控制。
        proposalRequireConsultationReport(reportId);
        ConsultationReportInterpretationRecord interpretation =
                dataMapper.proposalSelectReadyConsultationReportInterpretation(reportId);
        if (interpretation == null) {
            throw new CAuthException(BUSINESS_STATUS_CONFLICT, HttpStatus.CONFLICT, "报告解读尚未生成");
        }
        return ProposalReportInterpretationVO.builder()
                .reportId(reportId)
                .content(interpretation.content())
                .disclaimer(interpretation.disclaimer())
                .generatedAt(interpretation.generatedAt())
                .build();
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
        // 解析并检查就诊人 ID
        Long resolvedPatientId = proposalResolvePatientId(patientId);
        // 检查计划状态
        proposalValidateEnumValue(status, ProposalMedicationStatusEnum.values());
        return dataMapper.proposalSelectMedications(resolvedPatientId, status)
                .stream()
                .map(this::proposalToMedicationVO) // 转换
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
        // 校验资源反查得到的患者可被当前账号访问
        Long patientId = proposalRequireAccessiblePatient(medication.patientId());
        OffsetDateTime now = OffsetDateTime.now();
        String targetStatus;
        OffsetDateTime nextReminderAt;
        boolean reminderEnabled = medication.reminderEnabled();
        String reminderTimesJson = medication.reminderTimesJson();
        OffsetDateTime endAt;

        // 用户开启时按处方频次生成固定日间时刻，并从当前时间计算首个提醒。
        if (request.getAction() == ENABLE_REMINDER && "ACTIVE".equals(medication.status()) && !reminderEnabled) {
            List<LocalTime> reminderTimes = proposalResolveReminderTimes(medication.frequency());
            targetStatus = "ACTIVE";
            reminderEnabled = true;
            reminderTimesJson = proposalSerializeReminderTimes(reminderTimes);
            nextReminderAt = proposalCalculateNextReminderAt(reminderTimes, now);
            endAt = null;
        }
        // 关闭提醒不影响当前用药计划的执行状态。
        else if (request.getAction() == DISABLE_REMINDER
                && ("ACTIVE".equals(medication.status()) || "PAUSED".equals(medication.status()))
                && reminderEnabled) {
            targetStatus = medication.status();
            reminderEnabled = false;
            nextReminderAt = null;
            endAt = null;
        }
        // 暂停计划时停止扫描，但保留用户已开启的提醒偏好和时刻快照。
        else if (request.getAction() == PAUSE && "ACTIVE".equals(medication.status())) {
            targetStatus = "PAUSED";
            nextReminderAt = null;
            endAt = null;
        }
        // 恢复计划后仅在用户此前已开启提醒时恢复下一个日间提醒。
        else if (request.getAction() == RESUME && "PAUSED".equals(medication.status())) {
            targetStatus = "ACTIVE";
            nextReminderAt = reminderEnabled
                    ? proposalCalculateNextReminderAt(proposalParseReminderTimes(reminderTimesJson), now)
                    : null;
            endAt = null;
        }
        // 完成计划后停止后续提醒，不删除历史提醒时刻快照。
        else if (request.getAction() == COMPLETE
                && ("ACTIVE".equals(medication.status()) || "PAUSED".equals(medication.status()))) {
            targetStatus = "COMPLETED";
            reminderEnabled = false;
            nextReminderAt = null;
            endAt = now;
        } else {
            throw proposalConflict("当前用药计划状态不允许该操作");
        }

        // 将读取时状态带入条件更新，避免并发请求覆盖既有状态转换。
        if (dataMapper.proposalUpdateMedication(planId, patientId, targetStatus, medication.status(),
                nextReminderAt, reminderEnabled, reminderTimesJson, endAt, now) != 1) {
            throw proposalConflict("当前用药计划状态已变化");
        }
        return proposalToMedicationVO(medication, targetStatus, nextReminderAt, reminderEnabled, reminderTimesJson);
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
        // 解析并检查就诊人 ID
        Long resolvedPatientId = proposalResolvePatientId(patientId);
        // 检查随访状态
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
        // 校验资源反查得到的患者可被当前账号访问
        Long patientId = proposalRequireAccessiblePatient(followUp.patientId());
        //如果当前状态不是待确认状态，则不允许确认
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
     * 读取可向患者展示的医生病历，并校验其患者归属。
     *
     * @param reportId 问诊记录 ID，即 C 端报告 ID
     * @return 已授权访问的医生病历报告
     * @throws CAuthException 报告不存在、未完成、无正文或无权访问时抛出
     */
    private ConsultationReportRecord proposalRequireConsultationReport(Long reportId) {
        ConsultationReportRecord report = dataMapper.proposalSelectConsultationReport(reportId);
        if (report == null) {
            throw proposalNotFound("医生病历报告不存在");
        }
        // 仅在资源可展示后校验归属，避免暴露其他患者的病历状态。
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
        // 检查就诊人存在
        if (resolvedPatientId == null || !patientMapper.existsActivePatient(resolvedPatientId)) {
            throw proposalNotFound("就诊人不存在或已停用");
        }
        // 校验当前账号可访问
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
        // 检查患者存在
        return proposalResolvePatientId(patientId);
    }

    /**
     * 将用药计划投影转换为对外对象。
     *
     * @param medication 用药计划投影
     * @return 用药计划对象
     */
    private ProposalMedicationPlanVO proposalToMedicationVO(MedicationRecord medication) {
        return proposalToMedicationVO(medication, medication.status(), medication.nextReminderAt(),
                medication.reminderEnabled(), medication.reminderTimesJson());
    }

    /**
     * 将用药计划及其最新提醒状态转换为对外对象。
     *
     * @param medication 原始用药计划投影
     * @param status 最新计划状态
     * @param nextReminderAt 最新下次提醒时间
     * @param reminderEnabled 最新提醒开关
     * @param reminderTimesJson 每日提醒时刻 JSON 数组
     * @return 用药计划对象
     */
    private ProposalMedicationPlanVO proposalToMedicationVO(MedicationRecord medication, String status,
                                                             OffsetDateTime nextReminderAt, boolean reminderEnabled,
                                                             String reminderTimesJson) {
        return ProposalMedicationPlanVO.builder()
                .id(medication.id())
                .drugName(medication.drugName())
                .dosage(medication.dosage())
                .frequency(medication.frequency())
                .nextReminderAt(nextReminderAt)
                .reminderEnabled(reminderEnabled)
                .reminderTimes(reminderTimesJson == null || reminderTimesJson.isBlank() ? List.of()
                        : proposalParseReminderTimes(reminderTimesJson).stream().map(LocalTime::toString).toList())
                .status(status)
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
                && Arrays.stream(values)
                .noneMatch(item -> item.name().equals(value))//只有流中没有任何一个元素满足条件时才返回 true,只要找到一个匹配项就立刻返回 false(同样有短路特性)。
        ) {
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
        return new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, message);
    }

    /**
     * 创建资源越权异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 403 业务异常
     */
    private CAuthException proposalForbidden(String message) {
        return new CAuthException(UNAUTHORIZED, HttpStatus.FORBIDDEN, message);
    }

    /**
     * 创建状态冲突异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 409 业务异常
     */
    private CAuthException proposalConflict(String message) {
        return new CAuthException(ORDER_CLOSED_OR_STATUS_INVALID, HttpStatus.CONFLICT, message);
    }

    /**
     * 创建参数范围异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 400 业务异常
     */
    private CAuthException proposalBadRequest(String message) {
        return new CAuthException(PARAMETER_OUT_OF_RANGE, HttpStatus.BAD_REQUEST, message);
    }

    /**
     * 创建系统异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 500 业务异常
     */
    private CAuthException proposalSystemError(String message) {
        return new CAuthException(SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
