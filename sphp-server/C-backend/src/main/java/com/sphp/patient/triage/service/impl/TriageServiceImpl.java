package com.sphp.patient.triage.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.common.constant.TriageConstant;
import com.sphp.patient.common.enums.TriageUrgencyEnum;
import com.sphp.patient.triage.dto.TriageAssessmentCreateRequest;
import com.sphp.patient.triage.entity.TriageAssessment;
import com.sphp.patient.triage.mapper.TriageDataMapper;
import com.sphp.patient.triage.mapper.TriageDepartmentRuleRecord;
import com.sphp.patient.triage.service.TriageService;
import com.sphp.patient.triage.vo.TriageAssessmentVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sphp.patient.common.constant.TriageConstant.DISCLAIMER;
import static com.sphp.patient.common.enums.TriageUrgencyEnum.LOW;
import static com.sphp.shared.common.enums.ErrorCodeEnum.*;

/**
 * C端症状导诊服务实现。
 */
@Service
@RequiredArgsConstructor
public class TriageServiceImpl implements TriageService {
    // 数据访问接口
    private final TriageDataMapper triageDataMapper;
    // JSON 序列化接口
    private final ObjectMapper objectMapper;

    /**
     * 按医院启用规则匹配症状、写入评估快照并返回非诊断性建议。
     *
     * @param request 导诊评估请求
     * @return 已保存的导诊评估结果
     * @throws CAuthException 患者、医院不可访问或评估持久化失败时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public TriageAssessmentVO triageCreateAssessment(TriageAssessmentCreateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        // 解析本人或显式就诊人并校验当前用户有效归属
        Long patientId = triageResolveAccessiblePatient(userId, request.getPatientId());
        // 判断医院是否启用且未被软删除
        if (!triageDataMapper.triageExistsEnabledHospital(request.getHospitalId())) {
            throw triageNotFound("医院不存在或已停用");
        }

        // SQL 已按规则紧急程度和优先级排序，LinkedHashMap 保留每个科室的首条最高优先规则。
        List<TriageDepartmentRuleRecord> matchedRules = triageDataMapper
                .triageSelectMatchedRules(request.getHospitalId(), request.getSymptom());
        Map<Long, TriageAssessmentVO.RecommendedDepartment> departmentMap = new LinkedHashMap<>();
        for (TriageDepartmentRuleRecord rule : matchedRules) {
            departmentMap.putIfAbsent(rule.departmentId(), TriageAssessmentVO.RecommendedDepartment.builder()
                    .id(rule.departmentId())
                    .name(rule.departmentName())
                    .reason(rule.reason())
                    .build());
        }
        // 获取最高紧急程度
        String urgency = matchedRules.isEmpty() ? LOW.name() : matchedRules.getFirst().urgency();
        List<TriageAssessmentVO.RecommendedDepartment> recommendedDepartments = List.copyOf(departmentMap.values());

        TriageAssessment assessment = new TriageAssessment();
        assessment.setPatientId(patientId);
        assessment.setHospitalId(request.getHospitalId());
        assessment.setSymptomInput(triageSerialize(request, "症状快照保存失败"));
        assessment.setUrgency(urgency);
        assessment.setRecommendedDepartments(triageSerialize(recommendedDepartments, "推荐科室快照保存失败"));
        if (triageDataMapper.triageInsertAssessment(assessment) != 1) {
            throw triageSystemError("导诊评估保存失败");
        }
        return TriageAssessmentVO.builder()
                .assessmentId(assessment.getId())
                .urgency(urgency) // 紧急程度
                .recommendedDepartments(recommendedDepartments) // 推荐科室
                .disclaimer(DISCLAIMER) // 声明
                .build();
    }

    /**
     * 解析本人或显式就诊人并校验当前用户有效归属。
     *
     * @param userId C端用户 ID
     * @param requestedPatientId 可选就诊人 ID
     * @return 当前用户可访问的就诊人 ID
     * @throws CAuthException 就诊人不存在或无权访问时抛出
     */
    private Long triageResolveAccessiblePatient(Long userId, Long requestedPatientId) {
        Long patientId = requestedPatientId == null
                ? triageDataMapper.triageSelectSelfPatientId(userId)
                : requestedPatientId;
        if (patientId == null || !triageDataMapper.triageExistsActivePatient(patientId)) {
            throw triageNotFound("就诊人不存在或已停用");
        }
        if (!triageDataMapper.triageHasActivePatientRelation(userId, patientId)) {
            throw triageForbidden("无权访问该就诊人");
        }
        return patientId;
    }

    /**
     * 序列化导诊请求或推荐结果，用于保留不可变评估快照。
     *
     * @param source 待序列化对象
     * @param errorMessage 序列化失败时的客户端提示
     * @return JSON 文本
     * @throws CAuthException JSON 序列化失败时抛出
     */
    private String triageSerialize(Object source, String errorMessage) {
        try {
            return objectMapper.writeValueAsString(source); // 将对象序列化为 JSON
        } catch (JsonProcessingException exception) {
            throw triageSystemError(errorMessage);
        }
    }

    /**
     * 创建资源不存在异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 404 业务异常
     */
    private CAuthException triageNotFound(String message) {
        return new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, message);
    }

    /**
     * 创建患者归属越权异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 403 业务异常
     */
    private CAuthException triageForbidden(String message) {
        return new CAuthException(UNAUTHORIZED, HttpStatus.FORBIDDEN, message);
    }

    /**
     * 创建系统异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 500 业务异常
     */
    private CAuthException triageSystemError(String message) {
        return new CAuthException(SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
