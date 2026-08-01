package com.sphp.patient.auth.service.impl;

import com.sphp.patient.auth.config.CAuthProperties;
import com.sphp.patient.auth.dto.RegisterRequest;
import com.sphp.patient.auth.entity.CUser;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.mapper.CUserMapper;
import com.sphp.patient.auth.vo.RegisterVO;
import com.sphp.patient.auth.vo.CaptchaVO;
import com.sphp.patient.family.entity.Patient;
import com.sphp.patient.family.entity.PatientUserRelation;
import com.sphp.patient.family.mapper.PatientMapper;
import com.sphp.patient.family.mapper.PatientUserRelationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.lenient;

/**
 * C端登录服务单元测试。
 */
@ExtendWith(MockitoExtension.class)
class LoginServiceImplTest {

    @Mock
    private StringRedisTemplate redisTemplate;
    @Mock
    private ValueOperations<String, String> valueOperations;
    @Mock
    private CUserMapper cUserMapper;
    @Mock
    private PatientMapper patientMapper;
    @Mock
    private PatientUserRelationMapper relationMapper;

    private LoginServiceImpl loginService;

    /**
     * 初始化验证码测试所需依赖。
     */
    @BeforeEach
    void setUp() {
        CAuthProperties properties = new CAuthProperties();
        properties.setCaptchaExpiration(120);
        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        loginService = new LoginServiceImpl(properties, redisTemplate, cUserMapper, patientMapper, relationMapper);
    }

    /**
     * 验证图形验证码只以 BCrypt 摘要写入 Redis 并设置 120 秒 TTL。
     */
    @Test
    void createCaptchaStoresHashWithTtl() {
        CaptchaVO captcha = loginService.createCaptcha();

        assertTrue(captcha.getChallengeId().startsWith("cap_"));
        assertTrue(captcha.getImageBase64().startsWith("data:image/png;base64,"));
        assertEquals(120, captcha.getExpireSeconds());
        verify(valueOperations).set(
                eq("cend:captcha:" + captcha.getChallengeId()),
                argThat(value -> value != null && value.startsWith("$2")),
                eq(Duration.ofSeconds(120))
        );
    }

    /**
     * 验证注册事务创建账号、本人就诊人和默认关系。
     */
    @Test
    void registerCreatesUserPatientAndDefaultRelation() {
        RegisterRequest request = new RegisterRequest();
        request.setAccount(" patient_zhangsan ");
        request.setPassword("P@ssw0rd123");
        request.setChallengeId("cap_test");
        request.setCaptchaCode("A7K9");
        when(valueOperations.getAndDelete("cend:captcha:cap_test"))
                .thenReturn(org.mindrot.jbcrypt.BCrypt.hashpw("A7K9", org.mindrot.jbcrypt.BCrypt.gensalt()));
        when(cUserMapper.selectOne(any())).thenReturn(null);
        doAnswer(invocation -> {
            CUser user = invocation.getArgument(0);
            user.setId(10001L);
            return 1;
        }).when(cUserMapper).insert(any(CUser.class));
        doAnswer(invocation -> {
            Patient patient = invocation.getArgument(0);
            patient.setId(20001L);
            return 1;
        }).when(patientMapper).insert(any(Patient.class));

        RegisterVO result = loginService.register(request);

        assertEquals(10001L, result.getUserId());
        assertEquals("patient_zhangsan", result.getAccount());
        verify(cUserMapper).insert(org.mockito.ArgumentMatchers.<CUser>argThat(user -> "patient_zhangsan".equals(user.getAccount())
                && user.getPasswordHash().startsWith("$2") && "ENABLED".equals(user.getStatus())));
        verify(patientMapper).insert(org.mockito.ArgumentMatchers.<Patient>argThat(
                patient -> "patient_zhangsan".equals(patient.getName())));
        verify(relationMapper).insert(org.mockito.ArgumentMatchers.<PatientUserRelation>argThat(
                relation -> relation.getUserId().equals(10001L)
                && relation.getPatientId().equals(20001L)
                && "SELF".equals(relation.getRelationship()) && Boolean.TRUE.equals(relation.getIsDefault())));
    }

    /**
     * 验证账号去除首尾空白后仍需满足长度要求。
     */
    @Test
    void registerRejectsTrimmedAccountThatIsTooShort() {
        RegisterRequest request = new RegisterRequest();
        request.setAccount(" a ");
        request.setPassword("12345678");
        request.setChallengeId("cap_test");
        request.setCaptchaCode("A7K9");

        CAuthException exception = assertThrows(CAuthException.class, () -> loginService.register(request));

        assertEquals("A0400", exception.getCode());
    }
}
