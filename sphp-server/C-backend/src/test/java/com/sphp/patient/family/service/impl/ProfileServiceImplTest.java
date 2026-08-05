package com.sphp.patient.family.service.impl;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.family.dto.ProfileUpdateRequest;
import com.sphp.patient.family.mapper.ProfileMapper;
import com.sphp.patient.family.mapper.ProfileRecord;
import com.sphp.patient.family.vo.ProfileUpdateVO;
import com.sphp.patient.family.vo.ProfileVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 个人资料服务查询单元测试。
 */
class ProfileServiceImplTest {

    /**
     * 每个测试结束后清理当前用户上下文。
     */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
    }

    /**
     * 验证仅映射当前账号 SELF 资料并脱敏敏感联系方式。
     */
    @Test
    void getProfileReturnsMaskedSelfProfile() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        ProfileServiceImpl profileService = new ProfileServiceImpl(profileMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        when(profileMapper.selectSelfProfile(10001L)).thenReturn(profileRecord());

        ProfileVO result = profileService.getProfile();

        assertEquals(20001L, result.getId());
        assertEquals("张三", result.getName());
        assertEquals("138****8000", result.getPhone());
        assertEquals("110***********1234", result.getIdCardNo());
        assertEquals("李四 139****9000", result.getEmergencyContact());
    }

    /**
     * 验证本人关系或患者资料不存在时返回资源不存在错误。
     */
    @Test
    void getProfileRejectsMissingSelfProfile() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        ProfileServiceImpl profileService = new ProfileServiceImpl(profileMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        when(profileMapper.selectSelfProfile(10001L)).thenReturn(null);

        CAuthException exception = assertThrows(CAuthException.class, profileService::getProfile);

        assertEquals("A0402", exception.getCode());
        assertEquals(404, exception.getHttpStatus().value());
    }

    /**
     * 验证更新仅覆盖请求给出的字段，并在 SELF 边界内执行条件更新。
     */
    @Test
    void updateProfilePreservesOptionalFieldsAndUsesSelfCondition() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        ProfileServiceImpl profileService = new ProfileServiceImpl(profileMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setName("张三新名");
        request.setPhone("13800138001");
        request.setIdCardNo("11010519491231123x");
        when(profileMapper.lockUserForProfileMutation(10001L)).thenReturn(10001L);
        when(profileMapper.selectSelfProfile(10001L)).thenReturn(profileRecord());
        when(profileMapper.updateSelfProfile(eq(10001L), eq(20001L), eq("张三新名"), eq("MALE"),
                eq(LocalDate.of(1990, 5, 20)), eq("13800138001"), eq("11010519491231123X"),
                eq("李四 13900139000"), any()))
                .thenReturn(1);

        ProfileUpdateVO result = profileService.updateProfile(request);

        assertEquals(20001L, result.getId());
        assertEquals("张三新名", result.getName());
        assertEquals("138****8001", result.getPhone());
        assertEquals("110***********123X", result.getIdCardNo());
        verify(profileMapper).updateSelfProfile(eq(10001L), eq(20001L), eq("张三新名"), eq("MALE"),
                eq(LocalDate.of(1990, 5, 20)), eq("13800138001"), eq("11010519491231123X"),
                eq("李四 13900139000"), any());
    }

    /**
     * 验证条件更新未命中时拒绝并发或资料状态变更。
     */
    @Test
    void updateProfileRejectsConditionalUpdateMiss() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        ProfileServiceImpl profileService = new ProfileServiceImpl(profileMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setName("张三新名");
        when(profileMapper.lockUserForProfileMutation(10001L)).thenReturn(10001L);
        when(profileMapper.selectSelfProfile(10001L)).thenReturn(profileRecord());
        when(profileMapper.updateSelfProfile(any(), any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(0);

        CAuthException exception = assertThrows(CAuthException.class, () -> profileService.updateProfile(request));

        assertEquals("B0202", exception.getCode());
        assertEquals(409, exception.getHttpStatus().value());
    }

    /**
     * 验证非法性别和未来生日被业务校验拒绝。
     */
    @Test
    void updateProfileRejectsInvalidGenderAndFutureBirthday() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        ProfileServiceImpl profileService = new ProfileServiceImpl(profileMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        ProfileUpdateRequest invalidGender = new ProfileUpdateRequest();
        invalidGender.setName("张三");
        invalidGender.setGender("OTHER");
        ProfileUpdateRequest futureBirthday = new ProfileUpdateRequest();
        futureBirthday.setName("张三");
        futureBirthday.setBirthday(LocalDate.now().plusDays(1));

        assertEquals("A0400", assertThrows(CAuthException.class,
                () -> profileService.updateProfile(invalidGender)).getCode());
        assertEquals("A0400", assertThrows(CAuthException.class,
                () -> profileService.updateProfile(futureBirthday)).getCode());
    }

    /**
     * 验证当前账号下与有效家庭成员重复的身份证号会被拒绝。
     */
    @Test
    void updateProfileRejectsDuplicateIdCardNo() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        ProfileServiceImpl profileService = new ProfileServiceImpl(profileMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setName("张三");
        request.setIdCardNo("11010519491231123x");
        when(profileMapper.lockUserForProfileMutation(10001L)).thenReturn(10001L);
        when(profileMapper.selectSelfProfile(10001L)).thenReturn(profileRecord());
        when(profileMapper.existsActiveIdCard(10001L, "11010519491231123X", 20001L)).thenReturn(true);

        CAuthException exception = assertThrows(CAuthException.class,
                () -> profileService.updateProfile(request));

        assertEquals("A0506", exception.getCode());
        assertEquals(409, exception.getHttpStatus().value());
        verify(profileMapper, never()).updateSelfProfile(any(), any(), any(), any(), any(), any(), any(), any(), any());
    }

    /**
     * 验证服务层仍会拒绝不符合基础格式的身份证号。
     */
    @Test
    void updateProfileRejectsInvalidIdCardNo() {
        ProfileMapper profileMapper = mock(ProfileMapper.class);
        ProfileServiceImpl profileService = new ProfileServiceImpl(profileMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        ProfileUpdateRequest request = new ProfileUpdateRequest();
        request.setName("张三");
        request.setIdCardNo("11010519491231");

        CAuthException exception = assertThrows(CAuthException.class,
                () -> profileService.updateProfile(request));

        assertEquals("A0400", exception.getCode());
        verify(profileMapper, never()).lockUserForProfileMutation(any());
    }

    /**
     * 创建有效本人资料查询记录。
     *
     * @return 本人资料查询记录
     */
    private ProfileRecord profileRecord() {
        ProfileRecord record = new ProfileRecord();
        record.setPatientId(20001L);
        record.setName("张三");
        record.setGender("MALE");
        record.setBirthday(LocalDate.of(1990, 5, 20));
        record.setPhone("13800138000");
        record.setIdCardNo("110105194912311234");
        record.setEmergencyContact("李四 13900139000");
        return record;
    }
}
