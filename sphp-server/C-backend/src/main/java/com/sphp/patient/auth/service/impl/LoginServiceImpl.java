package com.sphp.patient.auth.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.sphp.patient.auth.config.CAuthProperties;
import com.sphp.patient.auth.dto.RegisterRequest;
import com.sphp.patient.auth.entity.CUser;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.mapper.CUserMapper;
import com.sphp.patient.auth.service.LoginService;
import com.sphp.patient.auth.support.CAuthTokenGenerator;
import com.sphp.patient.auth.vo.CaptchaVO;
import com.sphp.patient.auth.vo.RegisterVO;
import com.sphp.patient.common.constant.CAuthConstant;
import com.sphp.patient.common.enums.CUserStatusEnum;
import com.sphp.patient.common.enums.PatientRelationshipEnum;
import com.sphp.patient.family.entity.Patient;
import com.sphp.patient.family.entity.PatientUserRelation;
import com.sphp.patient.family.mapper.PatientMapper;
import com.sphp.patient.family.mapper.PatientUserRelationMapper;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.util.Locale;

/**
 * C端登录注册服务实现。
 */
@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {

    /** 验证码图片宽度 */
    private static final int CAPTCHA_WIDTH = 120;
    /** 验证码图片高度 */
    private static final int CAPTCHA_HEIGHT = 40;
    /** 验证码字符数量 */
    private static final int CAPTCHA_LENGTH = 4;
    /** 验证码干扰线数量 */
    private static final int CAPTCHA_LINE_COUNT = 30;

    private final CAuthProperties authProperties;
    private final StringRedisTemplate redisTemplate;
    private final CUserMapper cUserMapper;
    private final PatientMapper patientMapper;
    private final PatientUserRelationMapper relationMapper;

    /**
     * 生成一次性图形验证码并保存 BCrypt 摘要。
     *
     * @return 图形验证码信息
     */
    @Override
    public CaptchaVO createCaptcha() {
        LineCaptcha captcha = CaptchaUtil.createLineCaptcha(
                CAPTCHA_WIDTH, CAPTCHA_HEIGHT, CAPTCHA_LENGTH, CAPTCHA_LINE_COUNT);
        String challengeId = CAuthTokenGenerator.generateCaptchaChallengeId();
        String captchaHash = BCrypt.hashpw(
                captcha.getCode().toUpperCase(Locale.ROOT), BCrypt.gensalt());

        // Redis 仅保存短期摘要，避免验证码原文泄漏或被重复使用
        redisTemplate.opsForValue().set(
                CAuthConstant.CAPTCHA_KEY_PREFIX + challengeId,
                captchaHash,
                Duration.ofSeconds(authProperties.getCaptchaExpiration())
        );
        return CaptchaVO.builder()
                .challengeId(challengeId)
                .imageBase64(captcha.getImageBase64Data())
                .expireSeconds(authProperties.getCaptchaExpiration())
                .build();
    }

    /**
     * 注册 C端账号并创建本人默认就诊人。
     *
     * @param request 注册请求
     * @return 新建账号信息
     * @throws CAuthException 验证码无效或账号已存在时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisterVO register(RegisterRequest request) {
        String account = normalizeAccount(request.getAccount());
        validateAndConsumeCaptcha(request.getChallengeId(), request.getCaptchaCode());

        // 应用层预检查用于快速反馈，数据库唯一约束负责并发兜底
        CUser existingUser = cUserMapper.selectOne(Wrappers.<CUser>lambdaQuery()
                .eq(CUser::getAccount, account)
                .isNull(CUser::getDeletedAt));
        if (existingUser != null) {
            throw new CAuthException(ErrorCodeEnum.ACCOUNT_ALREADY_EXISTS,
                    HttpStatus.CONFLICT, "登录账号已存在");
        }

        try {
            CUser user = new CUser();
            user.setAccount(account);
            user.setPasswordHash(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
            user.setStatus(CUserStatusEnum.ENABLED.getValue());
            cUserMapper.insert(user);

            // 注册契约不包含姓名，暂以账号作为本人就诊人的初始姓名
            Patient patient = new Patient();
            patient.setName(account);
            patientMapper.insert(patient);

            PatientUserRelation relation = new PatientUserRelation();
            relation.setUserId(user.getId());
            relation.setPatientId(patient.getId());
            relation.setRelationship(PatientRelationshipEnum.SELF.getValue());
            relation.setIsDefault(true);
            relationMapper.insert(relation);

            return RegisterVO.builder().userId(user.getId()).account(account).build();
        } catch (DuplicateKeyException e) {
            throw new CAuthException(ErrorCodeEnum.ACCOUNT_ALREADY_EXISTS,
                    HttpStatus.CONFLICT, "登录账号已存在");
        }
    }

    /**
     * 原子消费并校验图形验证码。
     *
     * @param challengeId 验证码挑战标识
     * @param captchaCode 用户输入验证码
     * @throws CAuthException 验证码不存在、过期或不匹配时抛出
     */
    private void validateAndConsumeCaptcha(String challengeId, String captchaCode) {
        String captchaHash = redisTemplate.opsForValue().getAndDelete(
                CAuthConstant.CAPTCHA_KEY_PREFIX + challengeId);
        if (!StringUtils.hasText(captchaHash)
                || !BCrypt.checkpw(captchaCode.toUpperCase(Locale.ROOT), captchaHash)) {
            throw new CAuthException(ErrorCodeEnum.CAPTCHA_ERROR,
                    HttpStatus.BAD_REQUEST, "图形验证码无效或已过期");
        }
    }

    /**
     * 去除账号首尾空白并校验规范化后的长度。
     *
     * @param rawAccount 原始登录账号
     * @return 规范化账号
     * @throws CAuthException 规范化后长度不合法时抛出
     */
    private String normalizeAccount(String rawAccount) {
        String account = rawAccount.trim();
        if (account.length() < 4 || account.length() > 32) {
            throw new CAuthException(ErrorCodeEnum.INVALID_PARAMETER,
                    HttpStatus.BAD_REQUEST, "登录账号长度必须为4至32位");
        }
        return account;
    }
}
