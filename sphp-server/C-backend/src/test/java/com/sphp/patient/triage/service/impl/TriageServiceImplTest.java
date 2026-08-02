package com.sphp.patient.triage.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.triage.dto.TriageAssessmentCreateRequest;
import com.sphp.patient.triage.entity.TriageAssessment;
import com.sphp.patient.triage.mapper.TriageDataMapper;
import com.sphp.patient.triage.mapper.TriageDepartmentRuleRecord;
import com.sphp.patient.triage.vo.TriageAssessmentVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 导诊服务业务规则测试。
 */
class TriageServiceImplTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * 每个测试结束后清理当前用户上下文。
     */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
    }

    /**
     * 验证规则按紧急程度排序、同科室去重并写入请求快照。
     *
     * @throws Exception JSON 断言失败时抛出
     */
    @Test
    void triageCreateAssessmentDeduplicatesRulesAndPersistsSnapshots() throws Exception {
        TriageDataMapper mapper = authorizedMapper();
        when(mapper.triageSelectMatchedRules(101L, "咳嗽发热三天")).thenReturn(List.of(
                new TriageDepartmentRuleRecord(301L, "呼吸内科", "高优先级原因", "HIGH"),
                new TriageDepartmentRuleRecord(301L, "呼吸内科", "低优先级原因", "MEDIUM"),
                new TriageDepartmentRuleRecord(302L, "感染科", "发热相关", "MEDIUM")));
        doAnswer(invocation -> {
            TriageAssessment assessment = invocation.getArgument(0);
            assessment.setId(601L);
            return 1;
        }).when(mapper).triageInsertAssessment(any(TriageAssessment.class));
        TriageServiceImpl service = new TriageServiceImpl(mapper, objectMapper);
        setUserContext();

        TriageAssessmentVO result = service.triageCreateAssessment(request());

        assertEquals(601L, result.getAssessmentId());
        assertEquals("HIGH", result.getUrgency());
        assertEquals(2, result.getRecommendedDepartments().size());
        assertEquals("高优先级原因", result.getRecommendedDepartments().getFirst().getReason());
        ArgumentCaptor<TriageAssessment> captor = ArgumentCaptor.forClass(TriageAssessment.class);
        verify(mapper).triageInsertAssessment(captor.capture());
        JsonNode snapshot = objectMapper.readTree(captor.getValue().getSymptomInput());
        assertEquals("咳嗽发热三天", snapshot.get("symptom").asText());
        assertTrue(captor.getValue().getRecommendedDepartments().contains("呼吸内科"));
    }

    /**
     * 验证无规则命中时保存 LOW 评估并返回空推荐列表。
     */
    @Test
    void triageCreateAssessmentReturnsLowWithNoRules() {
        TriageDataMapper mapper = authorizedMapper();
        when(mapper.triageSelectMatchedRules(101L, "轻微不适")).thenReturn(List.of());
        doAnswer(invocation -> {
            TriageAssessment assessment = invocation.getArgument(0);
            assessment.setId(602L);
            return 1;
        }).when(mapper).triageInsertAssessment(any(TriageAssessment.class));
        TriageServiceImpl service = new TriageServiceImpl(mapper, objectMapper);
        setUserContext();
        TriageAssessmentCreateRequest request = request();
        request.setSymptom("轻微不适");

        TriageAssessmentVO result = service.triageCreateAssessment(request);

        assertEquals("LOW", result.getUrgency());
        assertTrue(result.getRecommendedDepartments().isEmpty());
    }

    /**
     * 验证使用其他账号患者时返回访问未授权。
     */
    @Test
    void triageCreateAssessmentRejectsForeignPatient() {
        TriageDataMapper mapper = mock(TriageDataMapper.class);
        when(mapper.triageExistsActivePatient(20002L)).thenReturn(true);
        when(mapper.triageHasActivePatientRelation(10001L, 20002L)).thenReturn(false);
        TriageServiceImpl service = new TriageServiceImpl(mapper, objectMapper);
        setUserContext();
        TriageAssessmentCreateRequest request = request();
        request.setPatientId(20002L);

        CAuthException exception = assertThrows(CAuthException.class, () -> service.triageCreateAssessment(request));

        assertEquals("A0301", exception.getCode());
    }

    /**
     * 创建当前用户拥有本人患者关系且医院可用的 Mapper 模拟对象。
     *
     * @return 已授权 Mapper 模拟对象
     */
    private TriageDataMapper authorizedMapper() {
        TriageDataMapper mapper = mock(TriageDataMapper.class);
        when(mapper.triageSelectSelfPatientId(10001L)).thenReturn(20001L);
        when(mapper.triageExistsActivePatient(20001L)).thenReturn(true);
        when(mapper.triageHasActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(mapper.triageExistsEnabledHospital(101L)).thenReturn(true);
        return mapper;
    }

    /**
     * 创建合法导诊请求。
     *
     * @return 导诊请求测试数据
     */
    private TriageAssessmentCreateRequest request() {
        TriageAssessmentCreateRequest request = new TriageAssessmentCreateRequest();
        request.setHospitalId(101L);
        request.setSymptom("咳嗽发热三天");
        request.setDuration("3天");
        return request;
    }

    /**
     * 设置当前 C端用户上下文。
     */
    private void setUserContext() {
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
    }
}
