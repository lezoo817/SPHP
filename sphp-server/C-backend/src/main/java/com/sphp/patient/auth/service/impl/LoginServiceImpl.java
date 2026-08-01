package com.sphp.patient.auth.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.sphp.patient.auth.config.CAuthProperties;
import com.sphp.patient.auth.config.CJwtProperties;
import com.sphp.patient.auth.dto.LoginRequest;
import com.sphp.patient.auth.dto.RegisterRequest;
import com.sphp.patient.auth.dto.RefreshTokenRequest;
import com.sphp.patient.auth.entity.CRefreshToken;
import com.sphp.patient.auth.entity.CUser;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.mapper.CUserMapper;
import com.sphp.patient.auth.mapper.CRefreshTokenMapper;
import com.sphp.patient.auth.service.LoginService;
import com.sphp.patient.auth.support.CAuthTokenGenerator;
import com.sphp.patient.auth.support.CAuthDigestUtil;
import com.sphp.patient.auth.support.jwt.CJwtService;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.auth.vo.CaptchaVO;
import com.sphp.patient.auth.vo.LoginUserVO;
import com.sphp.patient.auth.vo.LoginVO;
import com.sphp.patient.auth.vo.TokenParseVO;
import com.sphp.patient.auth.vo.RefreshTokenVO;
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
import java.time.OffsetDateTime;
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
    private final CJwtProperties jwtProperties;
    private final StringRedisTemplate redisTemplate;
    private final CUserMapper cUserMapper;
    private final PatientMapper patientMapper;
    private final PatientUserRelationMapper relationMapper;
    private final CRefreshTokenMapper refreshTokenMapper;
    private final CJwtService jwtService;

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
     * 使用 C端账号密码登录并签发 Token 对。
     *
     * @param request 登录请求
     * @return Token 对和用户摘要
     * @throws CAuthException 账号密码错误、账号停用或失败次数超限时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginRequest request) {
        String account = normalizeAccount(request.getAccount());
        String failureKey = loginFailureKey(account);
        checkLoginLock(failureKey);

        CUser user = cUserMapper.selectOne(Wrappers.<CUser>lambdaQuery()
                .eq(CUser::getAccount, account)
                .isNull(CUser::getDeletedAt));
        // 账号不存在和密码错误使用同一响应，避免泄漏账号是否存在
        if (user == null || !BCrypt.checkpw(request.getPassword(), user.getPasswordHash())) {
            recordLoginFailure(failureKey);
        }
        if (!CUserStatusEnum.ENABLED.getValue().equals(user.getStatus())) {
            throw new CAuthException(ErrorCodeEnum.ACCOUNT_DISABLED,
                    HttpStatus.FORBIDDEN, "账号已被停用");
        }

        redisTemplate.delete(failureKey);
        IssuedRefreshToken refreshToken = issueRefreshToken(user.getId());
        String accessToken = jwtService.issueAccessToken(
                user.getId(), user.getAccount(), refreshToken.tokenHash());
        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken.rawToken())
                .expiresIn(jwtProperties.getExpiration())
                .user(LoginUserVO.builder().id(user.getId()).account(user.getAccount()).build())
                .build();
    }

    /**
     * 读取拦截器建立的当前 C端用户最小令牌上下文。
     *
     * @return Token 最小身份信息
     * @throws CAuthException 当前请求未建立有效身份时抛出
     */
    @Override
    public TokenParseVO parseToken() {
        CUserPrincipal principal = CUserContext.getRequired();
        return TokenParseVO.builder()
                .userId(principal.userId())
                .account(principal.account())
                .tokenExpiresAt(principal.tokenExpiresAt())
                .build();
    }

    /**
     * 校验并轮换 C端刷新令牌。
     *
     * @param request 刷新令牌请求
     * @return 新 Token 对
     * @throws CAuthException 刷新令牌过期、撤销、伪造或重复消费时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefreshTokenVO refresh(RefreshTokenRequest request) {
        String oldTokenHash = CAuthDigestUtil.sha256Hex(request.getRefreshToken());
        CRefreshToken oldToken = refreshTokenMapper.selectOne(Wrappers.<CRefreshToken>lambdaQuery()
                .eq(CRefreshToken::getTokenHash, oldTokenHash));
        if (oldToken == null || oldToken.getRevokedAt() != null) {
            throw invalidRefreshToken();
        }
        OffsetDateTime now = OffsetDateTime.now();
        if (!oldToken.getExpiredAt().isAfter(now)) {
            throw new CAuthException(ErrorCodeEnum.LOGIN_EXPIRED,
                    HttpStatus.UNAUTHORIZED, "刷新令牌已过期");
        }

        String oldSessionKey = CAuthConstant.REFRESH_SESSION_KEY_PREFIX + oldTokenHash;
        String sessionValue = redisTemplate.opsForValue().get(oldSessionKey);
        if (!StringUtils.hasText(sessionValue)
                || !sessionValue.equals(oldToken.getUserId() + ":" + oldToken.getId())) {
            throw invalidRefreshToken();
        }

        CUser user = cUserMapper.selectById(oldToken.getUserId());
        if (user == null || user.getDeletedAt() != null) {
            throw invalidRefreshToken();
        }
        if (!CUserStatusEnum.ENABLED.getValue().equals(user.getStatus())) {
            throw new CAuthException(ErrorCodeEnum.ACCOUNT_DISABLED,
                    HttpStatus.FORBIDDEN, "账号已被停用");
        }

        // 状态条件更新确保并发刷新时仅一个请求取得旧令牌消费权
        CRefreshToken revokedToken = new CRefreshToken();
        revokedToken.setRevokedAt(now);
        int updated = refreshTokenMapper.update(revokedToken, Wrappers.<CRefreshToken>lambdaUpdate()
                .eq(CRefreshToken::getId, oldToken.getId())
                .isNull(CRefreshToken::getRevokedAt)
                .gt(CRefreshToken::getExpiredAt, now));
        if (updated != 1) {
            throw invalidRefreshToken();
        }

        redisTemplate.delete(oldSessionKey);
        IssuedRefreshToken newRefreshToken = issueRefreshToken(user.getId());
        String newAccessToken = jwtService.issueAccessToken(
                user.getId(), user.getAccount(), newRefreshToken.tokenHash());
        return RefreshTokenVO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken.rawToken())
                .expiresIn(jwtProperties.getExpiration())
                .build();
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

    /**
     * 检查当前账号是否处于登录锁定窗口。
     *
     * @param failureKey 登录失败计数键
     * @throws CAuthException 失败次数达到阈值时抛出
     */
    private void checkLoginLock(String failureKey) {
        String failureCount = redisTemplate.opsForValue().get(failureKey);
        if (StringUtils.hasText(failureCount)
                && Long.parseLong(failureCount) >= authProperties.getLoginMaxFailures()) {
            throw new CAuthException(ErrorCodeEnum.PASSWORD_RETRY_LIMIT_EXCEEDED,
                    HttpStatus.TOO_MANY_REQUESTS, "登录失败次数过多，请稍后重试");
        }
    }

    /**
     * 原子增加登录失败次数并设置锁定窗口。
     *
     * @param failureKey 登录失败计数键
     * @throws CAuthException 始终以登录失败或锁定异常结束当前请求
     */
    private void recordLoginFailure(String failureKey) {
        Long failureCount = redisTemplate.opsForValue().increment(failureKey);
        if (failureCount != null && failureCount == 1L) {
            // 首次失败设置固定窗口，避免无 TTL 的失败计数长期残留
            redisTemplate.expire(failureKey, Duration.ofSeconds(authProperties.getLoginLockSeconds()));
        }
        if (failureCount != null && failureCount >= authProperties.getLoginMaxFailures()) {
            throw new CAuthException(ErrorCodeEnum.PASSWORD_RETRY_LIMIT_EXCEEDED,
                    HttpStatus.TOO_MANY_REQUESTS, "登录失败次数过多，请稍后重试");
        }
        throw new CAuthException(ErrorCodeEnum.LOGIN_FAILED,
                HttpStatus.UNAUTHORIZED, "账号或密码错误");
    }

    /**
     * 创建刷新令牌数据库记录和 Redis 会话。
     *
     * @param userId C端用户 ID
     * @return 刷新令牌原文与摘要
     */
    private IssuedRefreshToken issueRefreshToken(Long userId) {
        String rawToken = CAuthTokenGenerator.generateRefreshToken();
        String tokenHash = CAuthDigestUtil.sha256Hex(rawToken);
        CRefreshToken entity = new CRefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(tokenHash);
        entity.setExpiredAt(OffsetDateTime.now().plusSeconds(authProperties.getRefreshTokenExpiration()));
        refreshTokenMapper.insert(entity);

        // Redis 只保存用户和数据库令牌 ID，不保存刷新令牌原文
        redisTemplate.opsForValue().set(
                CAuthConstant.REFRESH_SESSION_KEY_PREFIX + tokenHash,
                userId + ":" + entity.getId(),
                Duration.ofSeconds(authProperties.getRefreshTokenExpiration())
        );
        return new IssuedRefreshToken(rawToken, tokenHash);
    }

    /**
     * 使用账号摘要构造不含账号原文的登录失败键。
     *
     * @param account 规范化登录账号
     * @return Redis 登录失败计数键
     */
    private String loginFailureKey(String account) {
        return CAuthConstant.LOGIN_FAILURE_KEY_PREFIX + CAuthDigestUtil.sha256Hex(account);
    }

    /**
     * 已签发刷新令牌的内部结果。
     *
     * @param rawToken 仅返回客户端的令牌原文
     * @param tokenHash 持久化和会话校验使用的摘要
     */
    private record IssuedRefreshToken(String rawToken, String tokenHash) {
    }

    /**
     * 创建刷新令牌无效异常。
     *
     * @return HTTP 401 刷新令牌无效异常
     */
    private CAuthException invalidRefreshToken() {
        return new CAuthException(ErrorCodeEnum.UNAUTHORIZED,
                HttpStatus.UNAUTHORIZED, "刷新令牌无效或已撤销");
    }
}
