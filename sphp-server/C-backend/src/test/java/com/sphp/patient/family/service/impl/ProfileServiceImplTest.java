package com.sphp.patient.family.service.impl;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.family.mapper.ProfileMapper;
import com.sphp.patient.family.mapper.ProfileRecord;
import com.sphp.patient.family.vo.ProfileVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
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
        record.setEmergencyContact("李四 13900139000");
        return record;
    }
}
