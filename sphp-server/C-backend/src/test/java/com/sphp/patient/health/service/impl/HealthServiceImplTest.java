package com.sphp.patient.health.service.impl;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.health.dto.AllergyCreateRequest;
import com.sphp.patient.health.dto.AllergyUpdateRequest;
import com.sphp.patient.health.dto.MedicalHistoryCreateRequest;
import com.sphp.patient.health.entity.PatientAllergy;
import com.sphp.patient.health.entity.PatientMedicalHistory;
import com.sphp.patient.health.mapper.HealthPatientMapper;
import com.sphp.patient.health.mapper.HealthPatientProfileRecord;
import com.sphp.patient.health.mapper.PatientAllergyMapper;
import com.sphp.patient.health.mapper.PatientMedicalHistoryMapper;
import com.sphp.patient.health.vo.HealthRecordVO;
import com.sphp.patient.health.vo.AllergyCreateVO;
import com.sphp.patient.health.vo.AllergyUpdateVO;
import com.sphp.patient.health.vo.MedicalHistoryCreateVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 健康档案服务单元测试。
 */
class HealthServiceImplTest {

    /**
     * 每个测试结束后清理当前 C端用户上下文。
     */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
    }

    /**
     * 验证未传就诊人时查询本人档案，并且返回资料不包含敏感联系方式。
     */
    @Test
    void getHealthRecordReturnsDefaultPatientProfileHistoriesAndSummary() {
        HealthPatientMapper healthPatientMapper = mock(HealthPatientMapper.class);
        PatientAllergyMapper allergyMapper = mock(PatientAllergyMapper.class);
        PatientMedicalHistoryMapper historyMapper = mock(PatientMedicalHistoryMapper.class);
        HealthServiceImpl healthService = new HealthServiceImpl(healthPatientMapper, allergyMapper, historyMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        when(healthPatientMapper.selectSelfPatientId(10001L)).thenReturn(20001L);
        when(healthPatientMapper.selectActiveProfile(20001L))
                .thenReturn(new HealthPatientProfileRecord(20001L, "张三", "MALE"));
        when(allergyMapper.selectList(any())).thenReturn(List.of(allergy(16001L, "青霉素", "皮疹")));
        when(historyMapper.selectList(any())).thenReturn(List.of(history(17001L, "高血压病史5年", LocalDate.of(2021, 1, 1))));

        HealthRecordVO result = healthService.getHealthRecord(null);

        assertEquals(20001L, result.getProfile().getId());
        assertEquals("张三", result.getProfile().getName());
        assertEquals("青霉素", result.getAllergies().getFirst().getAllergen());
        assertEquals(LocalDate.of(2021, 1, 1), result.getMedicalHistories().getFirst().getOccurredAt());
        assertEquals("已记录1项过敏史和1项既往史", result.getSummary());
        assertTrue(Arrays.stream(result.getProfile().getClass().getDeclaredFields())
                .noneMatch(field -> List.of("phone", "idCardNo", "emergencyContact").contains(field.getName())));
    }

    /**
     * 验证新增过敏史时将未传患者解析为本人并返回新记录。
     */
    @Test
    void createAllergyUsesSelfPatientWhenPatientIdIsMissing() {
        HealthPatientMapper healthPatientMapper = mock(HealthPatientMapper.class);
        PatientAllergyMapper allergyMapper = mock(PatientAllergyMapper.class);
        PatientMedicalHistoryMapper historyMapper = mock(PatientMedicalHistoryMapper.class);
        HealthServiceImpl healthService = new HealthServiceImpl(healthPatientMapper, allergyMapper, historyMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        AllergyCreateRequest request = new AllergyCreateRequest();
        request.setAllergen("青霉素");
        request.setReaction("皮疹");
        when(healthPatientMapper.selectSelfPatientId(10001L)).thenReturn(20001L);
        doAnswer(invocation -> {
            PatientAllergy allergy = invocation.getArgument(0);
            allergy.setId(16001L);
            return 1;
        }).when(allergyMapper).insert(any(PatientAllergy.class));

        AllergyCreateVO result = healthService.createAllergy(request);

        assertEquals(16001L, result.getId());
        assertEquals("青霉素", result.getAllergen());
        verify(allergyMapper).insert(org.mockito.ArgumentMatchers.<PatientAllergy>argThat(allergy ->
                allergy.getPatientId().equals(20001L)
                        && "青霉素".equals(allergy.getAllergen())
                        && "皮疹".equals(allergy.getReaction())));
    }

    /**
     * 验证更新过敏史时保留未传的过敏反应，并使用患者归属条件更新。
     */
    @Test
    void updateAllergyPreservesMissingReactionAndChecksPatientScope() {
        HealthPatientMapper healthPatientMapper = mock(HealthPatientMapper.class);
        PatientAllergyMapper allergyMapper = mock(PatientAllergyMapper.class);
        PatientMedicalHistoryMapper historyMapper = mock(PatientMedicalHistoryMapper.class);
        HealthServiceImpl healthService = new HealthServiceImpl(healthPatientMapper, allergyMapper, historyMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        AllergyUpdateRequest request = new AllergyUpdateRequest();
        request.setAllergen("阿莫西林");
        PatientAllergy existing = allergy(16001L, "青霉素", "皮疹");
        existing.setPatientId(20001L);
        when(healthPatientMapper.existsActivePatient(20001L)).thenReturn(true);
        when(healthPatientMapper.hasActivePatientRelation(10001L, 20001L)).thenReturn(true);
        when(allergyMapper.selectOne(any())).thenReturn(existing);
        when(allergyMapper.update(any(PatientAllergy.class), any())).thenReturn(1);

        AllergyUpdateVO result = healthService.updateAllergy(16001L, request);

        assertEquals(16001L, result.getId());
        assertEquals("阿莫西林", result.getAllergen());
        assertEquals("皮疹", result.getReaction());
        verify(allergyMapper).update(org.mockito.ArgumentMatchers.<PatientAllergy>argThat(allergy ->
                allergy.getPatientId().equals(20001L)
                        && "阿莫西林".equals(allergy.getAllergen())
                        && "皮疹".equals(allergy.getReaction())), any());
    }

    /**
     * 验证新增既往史时将未传患者解析为本人并保存发生日期。
     */
    @Test
    void createMedicalHistoryUsesSelfPatientAndPreservesOccurredAt() {
        HealthPatientMapper healthPatientMapper = mock(HealthPatientMapper.class);
        PatientAllergyMapper allergyMapper = mock(PatientAllergyMapper.class);
        PatientMedicalHistoryMapper historyMapper = mock(PatientMedicalHistoryMapper.class);
        HealthServiceImpl healthService = new HealthServiceImpl(healthPatientMapper, allergyMapper, historyMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        MedicalHistoryCreateRequest request = new MedicalHistoryCreateRequest();
        request.setContent("高血压病史5年");
        request.setOccurredAt(LocalDate.of(2021, 1, 1));
        when(healthPatientMapper.selectSelfPatientId(10001L)).thenReturn(20001L);
        doAnswer(invocation -> {
            PatientMedicalHistory history = invocation.getArgument(0);
            history.setId(17001L);
            return 1;
        }).when(historyMapper).insert(any(PatientMedicalHistory.class));

        MedicalHistoryCreateVO result = healthService.createMedicalHistory(request);

        assertEquals(17001L, result.getId());
        assertEquals("高血压病史5年", result.getContent());
        assertEquals(LocalDate.of(2021, 1, 1), result.getOccurredAt());
        verify(historyMapper).insert(org.mockito.ArgumentMatchers.<PatientMedicalHistory>argThat(history ->
                history.getPatientId().equals(20001L)
                        && "高血压病史5年".equals(history.getContent())
                        && LocalDate.of(2021, 1, 1).equals(history.getOccurredAt())));
    }

    /**
     * 创建过敏史实体测试数据。
     *
     * @param id 过敏史 ID
     * @param allergen 过敏原名称
     * @param reaction 过敏反应
     * @return 过敏史实体
     */
    private PatientAllergy allergy(Long id, String allergen, String reaction) {
        PatientAllergy allergy = new PatientAllergy();
        allergy.setId(id);
        allergy.setAllergen(allergen);
        allergy.setReaction(reaction);
        return allergy;
    }

    /**
     * 创建既往史实体测试数据。
     *
     * @param id 既往史 ID
     * @param content 既往史内容
     * @param occurredAt 发生日期
     * @return 既往史实体
     */
    private PatientMedicalHistory history(Long id, String content, LocalDate occurredAt) {
        PatientMedicalHistory history = new PatientMedicalHistory();
        history.setId(id);
        history.setContent(content);
        history.setOccurredAt(occurredAt);
        return history;
    }
}
