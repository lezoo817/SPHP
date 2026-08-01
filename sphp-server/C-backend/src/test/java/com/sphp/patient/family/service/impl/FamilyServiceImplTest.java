package com.sphp.patient.family.service.impl;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.family.mapper.FamilyMemberMapper;
import com.sphp.patient.family.mapper.FamilyMemberRecord;
import com.sphp.patient.family.mapper.PatientMapper;
import com.sphp.patient.family.mapper.PatientUserRelationMapper;
import com.sphp.patient.family.dto.FamilyMemberCreateRequest;
import com.sphp.patient.family.dto.FamilyMemberUpdateRequest;
import com.sphp.patient.family.entity.Patient;
import com.sphp.patient.family.entity.PatientUserRelation;
import com.sphp.patient.family.vo.FamilyMemberListVO;
import com.sphp.patient.family.vo.FamilyMemberCreateVO;
import com.sphp.patient.family.vo.FamilyMemberUpdateVO;
import com.sphp.patient.family.vo.FamilyMemberUnbindVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 家庭成员服务单元测试。
 */
class FamilyServiceImplTest {

    /**
     * 每个测试结束后清理当前用户上下文。
     */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
    }

    /**
     * 验证查询仅映射有效成员资料，并对手机号进行脱敏。
     */
    @Test
    void listFamilyMembersReturnsMaskedPhoneAndRelationName() {
        FamilyMemberMapper familyMemberMapper = mock(FamilyMemberMapper.class);
        PatientMapper patientMapper = mock(PatientMapper.class);
        PatientUserRelationMapper relationMapper = mock(PatientUserRelationMapper.class);
        FamilyServiceImpl familyService = new FamilyServiceImpl(familyMemberMapper, patientMapper, relationMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        when(familyMemberMapper.selectActiveMembers(10001L)).thenReturn(List.of(
                record(20001L, "张三", "SELF", true, "13800138000", LocalDate.of(1990, 5, 20)),
                record(20002L, "张小明", "CHILD", false, "13800138001", LocalDate.of(2018, 6, 1))
        ));

        List<FamilyMemberListVO> result = familyService.listFamilyMembers();

        assertEquals(2, result.size());
        assertEquals("本人", result.getFirst().getRelationName());
        assertEquals("138****8000", result.getFirst().getPhone());
        assertEquals("子女", result.get(1).getRelationName());
        assertFalse(result.get(1).getIsDefault());
    }

    /**
     * 验证新增成员锁定用户后创建患者和非默认关系。
     */
    @Test
    void createFamilyMemberCreatesPatientAndNonDefaultRelation() {
        FamilyMemberMapper familyMemberMapper = mock(FamilyMemberMapper.class);
        PatientMapper patientMapper = mock(PatientMapper.class);
        PatientUserRelationMapper relationMapper = mock(PatientUserRelationMapper.class);
        FamilyServiceImpl familyService = new FamilyServiceImpl(familyMemberMapper, patientMapper, relationMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        FamilyMemberCreateRequest request = new FamilyMemberCreateRequest();
        request.setName("张小明");
        request.setRelation("CHILD");
        request.setGender("MALE");
        request.setBirthday(LocalDate.of(2018, 6, 1));
        request.setPhone("13800138001");
        request.setIdCardNo("11010519491231002X");
        when(familyMemberMapper.lockUserForFamilyMutation(10001L)).thenReturn(10001L);
        when(familyMemberMapper.countActiveNonSelfMembers(10001L)).thenReturn(2);
        when(familyMemberMapper.existsActiveIdCard(10001L, "11010519491231002X", null)).thenReturn(false);
        doAnswer(invocation -> {
            Patient patient = invocation.getArgument(0);
            patient.setId(20002L);
            return 1;
        }).when(patientMapper).insert(any(Patient.class));

        FamilyMemberCreateVO result = familyService.createFamilyMember(request);

        assertEquals(20002L, result.getPatientId());
        assertEquals("张小明", result.getName());
        assertEquals("CHILD", result.getRelation());
        assertFalse(result.getIsDefault());
        verify(relationMapper).insert(org.mockito.ArgumentMatchers.<PatientUserRelation>argThat(
                relation -> relation.getUserId().equals(10001L)
                        && relation.getPatientId().equals(20002L)
                        && "CHILD".equals(relation.getRelationship())
                        && Boolean.FALSE.equals(relation.getIsDefault())));
    }

    /**
     * 验证更新成员时可选字段未传保留原值并更新关系。
     */
    @Test
    void updateFamilyMemberPreservesMissingOptionalFields() {
        FamilyMemberMapper familyMemberMapper = mock(FamilyMemberMapper.class);
        PatientMapper patientMapper = mock(PatientMapper.class);
        PatientUserRelationMapper relationMapper = mock(PatientUserRelationMapper.class);
        FamilyServiceImpl familyService = new FamilyServiceImpl(familyMemberMapper, patientMapper, relationMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        FamilyMemberUpdateRequest request = new FamilyMemberUpdateRequest();
        request.setName("张小明");
        request.setRelation("CHILD");
        request.setPhone("13800138002");
        FamilyMemberRecord existing = record(20002L, "张小明旧名", "CHILD", false,
                "13800138001", LocalDate.of(2018, 6, 1));
        existing.setRelationId(50001L);
        existing.setGender("MALE");
        existing.setIdCardNo("11010519491231002X");
        existing.setEmergencyContact("张三 13800138000");
        when(familyMemberMapper.lockUserForFamilyMutation(10001L)).thenReturn(10001L);
        when(familyMemberMapper.selectActiveMember(10001L, 20002L)).thenReturn(existing);
        when(patientMapper.updateById(any(Patient.class))).thenReturn(1);
        when(relationMapper.updateById(any(PatientUserRelation.class))).thenReturn(1);

        FamilyMemberUpdateVO result = familyService.updateFamilyMember(20002L, request);

        assertEquals(20002L, result.getPatientId());
        assertEquals("138****8002", result.getPhone());
        verify(patientMapper).updateById(org.mockito.ArgumentMatchers.<Patient>argThat(patient ->
                "MALE".equals(patient.getGender())
                        && LocalDate.of(2018, 6, 1).equals(patient.getDateOfBirth())
                        && "11010519491231002X".equals(patient.getIdCardCiphertext())
                        && "13800138002".equals(patient.getPhoneCiphertext())));
    }

    /**
     * 验证账号本人不能通过家庭成员接口更新。
     */
    @Test
    void updateFamilyMemberRejectsSelfMember() {
        FamilyMemberMapper familyMemberMapper = mock(FamilyMemberMapper.class);
        PatientMapper patientMapper = mock(PatientMapper.class);
        PatientUserRelationMapper relationMapper = mock(PatientUserRelationMapper.class);
        FamilyServiceImpl familyService = new FamilyServiceImpl(familyMemberMapper, patientMapper, relationMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        FamilyMemberUpdateRequest request = updateRequest();
        FamilyMemberRecord selfRecord = record(20001L, "张三", "SELF", true,
                "13800138000", LocalDate.of(1990, 5, 20));
        when(familyMemberMapper.lockUserForFamilyMutation(10001L)).thenReturn(10001L);
        when(familyMemberMapper.selectActiveMember(10001L, 20001L)).thenReturn(selfRecord);

        CAuthException exception = assertThrows(CAuthException.class,
                () -> familyService.updateFamilyMember(20001L, request));

        assertEquals("A0443", exception.getCode());
    }

    /**
     * 验证更新为当前账号下已绑定身份证号时会被拒绝。
     */
    @Test
    void updateFamilyMemberRejectsDuplicateIdCard() {
        FamilyMemberMapper familyMemberMapper = mock(FamilyMemberMapper.class);
        PatientMapper patientMapper = mock(PatientMapper.class);
        PatientUserRelationMapper relationMapper = mock(PatientUserRelationMapper.class);
        FamilyServiceImpl familyService = new FamilyServiceImpl(familyMemberMapper, patientMapper, relationMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        FamilyMemberUpdateRequest request = updateRequest();
        request.setIdCardNo("11010519491231002x");
        FamilyMemberRecord existing = record(20002L, "张小明", "CHILD", false,
                "13800138001", LocalDate.of(2018, 6, 1));
        when(familyMemberMapper.lockUserForFamilyMutation(10001L)).thenReturn(10001L);
        when(familyMemberMapper.selectActiveMember(10001L, 20002L)).thenReturn(existing);
        when(familyMemberMapper.existsActiveIdCard(10001L, "11010519491231002X", 20002L)).thenReturn(true);

        CAuthException exception = assertThrows(CAuthException.class,
                () -> familyService.updateFamilyMember(20002L, request));

        assertEquals("A0506", exception.getCode());
    }

    /**
     * 验证解绑仅软删除当前用户与非本人成员之间的关系记录。
     */
    @Test
    void unbindFamilyMemberSoftDeletesOnlyRelation() {
        FamilyMemberMapper familyMemberMapper = mock(FamilyMemberMapper.class);
        PatientMapper patientMapper = mock(PatientMapper.class);
        PatientUserRelationMapper relationMapper = mock(PatientUserRelationMapper.class);
        FamilyServiceImpl familyService = new FamilyServiceImpl(familyMemberMapper, patientMapper, relationMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        FamilyMemberRecord existing = record(20002L, "张小明", "CHILD", false,
                "13800138001", LocalDate.of(2018, 6, 1));
        existing.setRelationId(50001L);
        when(familyMemberMapper.lockUserForFamilyMutation(10001L)).thenReturn(10001L);
        when(familyMemberMapper.selectActiveMember(10001L, 20002L)).thenReturn(existing);
        when(familyMemberMapper.softDeleteActiveRelation(org.mockito.ArgumentMatchers.eq(50001L),
                org.mockito.ArgumentMatchers.eq(10001L), any(OffsetDateTime.class))).thenReturn(1);

        FamilyMemberUnbindVO result = familyService.unbindFamilyMember(20002L);

        assertEquals(20002L, result.getPatientId());
        assertTrue(result.getUnbound());
        verify(familyMemberMapper).softDeleteActiveRelation(org.mockito.ArgumentMatchers.eq(50001L),
                org.mockito.ArgumentMatchers.eq(10001L), any(OffsetDateTime.class));
    }

    /**
     * 验证账号本人不能通过家庭成员接口解绑。
     */
    @Test
    void unbindFamilyMemberRejectsSelfMember() {
        FamilyMemberMapper familyMemberMapper = mock(FamilyMemberMapper.class);
        PatientMapper patientMapper = mock(PatientMapper.class);
        PatientUserRelationMapper relationMapper = mock(PatientUserRelationMapper.class);
        FamilyServiceImpl familyService = new FamilyServiceImpl(familyMemberMapper, patientMapper, relationMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        FamilyMemberRecord selfRecord = record(20001L, "张三", "SELF", true,
                "13800138000", LocalDate.of(1990, 5, 20));
        when(familyMemberMapper.lockUserForFamilyMutation(10001L)).thenReturn(10001L);
        when(familyMemberMapper.selectActiveMember(10001L, 20001L)).thenReturn(selfRecord);

        CAuthException exception = assertThrows(CAuthException.class,
                () -> familyService.unbindFamilyMember(20001L));

        assertEquals("A0443", exception.getCode());
        verifyNoInteractions(patientMapper, relationMapper);
    }

    /**
     * 创建满足更新必填字段要求的请求。
     *
     * @return 更新家庭成员请求
     */
    private FamilyMemberUpdateRequest updateRequest() {
        FamilyMemberUpdateRequest request = new FamilyMemberUpdateRequest();
        request.setName("张小明");
        request.setRelation("CHILD");
        return request;
    }

    /**
     * 创建家庭成员联表查询记录。
     *
     * @param patientId 就诊人 ID
     * @param name 姓名
     * @param relation 关系编码
     * @param isDefault 是否默认
     * @param phone 手机号
     * @param birthday 出生日期
     * @return 查询记录
     */
    private FamilyMemberRecord record(Long patientId, String name, String relation, boolean isDefault,
                                      String phone, LocalDate birthday) {
        FamilyMemberRecord record = new FamilyMemberRecord();
        record.setPatientId(patientId);
        record.setName(name);
        record.setRelationship(relation);
        record.setIsDefault(isDefault);
        record.setGender("MALE");
        record.setPhone(phone);
        record.setBirthday(birthday);
        return record;
    }
}
