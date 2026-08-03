package com.sphp.patient.auth.service.impl;

import cn.hutool.captcha.CaptchaUtil;
import cn.hutool.captcha.LineCaptcha;
import com.sphp.patient.auth.config.CAuthProperties;
import com.sphp.patient.auth.config.CJwtProperties;
import com.sphp.patient.auth.dto.LoginRequest;
import com.sphp.patient.auth.dto.RegisterRequest;
import com.sphp.patient.auth.dto.RefreshTokenRequest;
import com.sphp.patient.auth.dto.LogoutRequest;
import com.sphp.patient.auth.dto.ChangePasswordRequest;
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
import com.sphp.patient.auth.vo.LogoutVO;
import com.sphp.patient.auth.vo.ChangePasswordVO;
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
import java.util.List;

import static com.sphp.patient.common.constant.CAuthConstant.*;
import static com.sphp.patient.common.enums.CUserStatusEnum.ENABLED;
import static com.sphp.patient.common.enums.PatientRelationshipEnum.SELF;
import static com.sphp.shared.common.enums.ErrorCodeEnum.*;
import static com.sphp.shared.common.enums.ErrorCodeEnum.UNAUTHORIZED;
import static org.springframework.http.HttpStatus.*;

/**
 * C端登录注册服务实现。
 */
@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {

    // C端认证配置
    private final CAuthProperties authProperties;
    // JWT 配置
    private final CJwtProperties jwtProperties;

    private final StringRedisTemplate redisTemplate;
    // C端用户Mapper
    private final CUserMapper cUserMapper;

    private final PatientMapper patientMapper;
    // 就诊人与用户关系Mapper
    private final PatientUserRelationMapper relationMapper;
    // Refresh TokenMapper
    private final CRefreshTokenMapper refreshTokenMapper;

    private final CJwtService jwtService;

    /**
     * 生成一次性图形验证码并保存 BCrypt 摘要。
     *
     * @return 图形验证码信息
     */
    @Override
    public CaptchaVO createCaptcha() {
        // 调用Hutool的工具类生成验证码
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
        // 验证码检查
        validateAndConsumeCaptcha(request.getChallengeId(), request.getCaptchaCode());

        // 应用层预检查用于快速反馈，数据库唯一约束负责并发兜底
        CUser existingUser = cUserMapper.selectOne(Wrappers.<CUser>lambdaQuery()
                .eq(CUser::getAccount, account)
                .isNull(CUser::getDeletedAt));
        if (existingUser != null) {
            throw new CAuthException(ACCOUNT_ALREADY_EXISTS,
                    CONFLICT, "登录账号已存在");
        }

        try {
            CUser user = new CUser();
            user.setAccount(account);
            user.setPasswordHash(BCrypt.hashpw(request.getPassword(), BCrypt.gensalt()));
            user.setStatus(ENABLED.getValue());
            cUserMapper.insert(user);

            // 注册契约不包含姓名，暂以账号作为本人就诊人的初始姓名
            Patient patient = new Patient();
            patient.setName(account);
            patientMapper.insert(patient);

            // 创建本人默认就诊人
            PatientUserRelation relation = new PatientUserRelation();
            relation.setUserId(user.getId());
            relation.setPatientId(patient.getId());
            relation.setRelationship(SELF.getValue());
            relation.setIsDefault(true);
            relationMapper.insert(relation);

            return RegisterVO.builder().userId(user.getId()).account(account).build();
        } catch (DuplicateKeyException e) {
            throw new CAuthException(ACCOUNT_ALREADY_EXISTS,
                    CONFLICT, "登录账号已存在");
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
        // 登录失败检查
        checkLoginLock(failureKey);

        // 应用层预检查用于快速反馈，数据库唯一约束负责并发兜底
        CUser user = cUserMapper.selectOne(Wrappers.<CUser>lambdaQuery()
                .eq(CUser::getAccount, account)
                .isNull(CUser::getDeletedAt));
        // 账号不存在和密码错误使用同一响应，避免泄漏账号是否存在
        if (user == null || !BCrypt.checkpw(request.getPassword(), user.getPasswordHash())) {
            recordLoginFailure(failureKey);
        }
        // 账号停用
        if (user != null && !ENABLED.getValue().equals(user.getStatus())) {
            throw new CAuthException(ACCOUNT_DISABLED,
                    FORBIDDEN, "账号已被停用");
        }

        // 登录失败重置
        redisTemplate.delete(failureKey);
        // 签发 Refresh Token
        IssuedRefreshToken refreshToken = null;
        if (user != null) {
            refreshToken = issueRefreshToken(user.getId());
        }
        // 签发 Access Token
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
        // 从拦截器中获取当前用户
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
        // 校验并获取旧 Refresh Token
        String oldTokenHash = CAuthDigestUtil.sha256Hex(request.getRefreshToken());
        CRefreshToken oldToken = refreshTokenMapper.selectOne(Wrappers.<CRefreshToken>lambdaQuery()
                .eq(CRefreshToken::getTokenHash, oldTokenHash));
        // 刷新令牌不存在、撤销、伪造或重复消费
        if (oldToken == null || oldToken.getRevokedAt() != null) {
            throw invalidRefreshToken();
        }
        OffsetDateTime now = OffsetDateTime.now();
        // 刷新令牌已过期
        if (!oldToken.getExpiredAt().isAfter(now)) {
            throw new CAuthException(LOGIN_EXPIRED,
                    HttpStatus.UNAUTHORIZED, "刷新令牌已过期");
        }

        // 检查旧 Refresh Token 的会话
        String oldSessionKey = REFRESH_SESSION_KEY_PREFIX + oldTokenHash;
        String sessionValue = redisTemplate.opsForValue().get(oldSessionKey);
        // 旧 Refresh Token 的会话不存在或已过期
        if (!StringUtils.hasText(sessionValue)
                || !sessionValue.equals(oldToken.getUserId() + ":" + oldToken.getId())) {
            throw invalidRefreshToken();
        }
        // 检查旧 Refresh Token 的用户
        CUser user = cUserMapper.selectById(oldToken.getUserId());
        if (user == null || user.getDeletedAt() != null) {
            throw invalidRefreshToken();
        }
        //若账号状态为停用
        if (!ENABLED.getValue().equals(user.getStatus())) {
            throw new CAuthException(ACCOUNT_DISABLED,
                    FORBIDDEN, "账号已被停用");
        }

        // 状态条件更新确保并发刷新时仅一个请求取得旧令牌消费权
        CRefreshToken revokedToken = new CRefreshToken();
        revokedToken.setRevokedAt(now);
        int updated = refreshTokenMapper.update(revokedToken, Wrappers.<CRefreshToken>lambdaUpdate()
                .eq(CRefreshToken::getId, oldToken.getId()) // id相同
                .isNull(CRefreshToken::getRevokedAt)  // 未撤销
                .gt(CRefreshToken::getExpiredAt, now)); // 未过期
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
     * 撤销当前 Access Token 绑定的刷新会话。
     *
     * @param request 退出登录请求
     * @return 退出结果
     * @throws CAuthException Access Token 与 Refresh Token 不属于同一会话时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LogoutVO logout(LogoutRequest request) {
        CUserPrincipal principal = CUserContext.getRequired();
        String tokenHash = CAuthDigestUtil.sha256Hex(request.getRefreshToken());
        if (!tokenHash.equals(principal.sessionHash())) {
            throw new CAuthException(UNAUTHORIZED,
                    HttpStatus.UNAUTHORIZED, "当前会话无效");
        }
        // 获取当前 Refresh Token
        CRefreshToken currentToken = refreshTokenMapper.selectOne(Wrappers.<CRefreshToken>lambdaQuery()
                .eq(CRefreshToken::getTokenHash, tokenHash)
                .eq(CRefreshToken::getUserId, principal.userId()));
        // Refresh Token 不存在、撤销、伪造或已过期
        if (currentToken == null || currentToken.getRevokedAt() != null
                || !currentToken.getExpiredAt().isAfter(OffsetDateTime.now())) {
            throw new CAuthException(UNAUTHORIZED,
                    HttpStatus.UNAUTHORIZED, "当前会话无效");
        }

        OffsetDateTime now = OffsetDateTime.now();
        CRefreshToken revokedToken = new CRefreshToken();
        revokedToken.setRevokedAt(now);
        int updated = refreshTokenMapper.update(revokedToken, Wrappers.<CRefreshToken>lambdaUpdate()
                .eq(CRefreshToken::getId, currentToken.getId())
                .eq(CRefreshToken::getUserId, principal.userId())
                .isNull(CRefreshToken::getRevokedAt)
                .gt(CRefreshToken::getExpiredAt, now));
        if (updated != 1) {
            throw new CAuthException(UNAUTHORIZED,
                    HttpStatus.UNAUTHORIZED, "当前会话无效");
        }

        // 删除 Redis 会话后，与其绑定的 Access Token 立即失效
        redisTemplate.delete(REFRESH_SESSION_KEY_PREFIX + tokenHash);
        return LogoutVO.builder().loggedOut(true).build();
    }

    /**
     * 修改当前 C端用户登录密码并撤销其他刷新会话。
     *
     * @param request 修改密码请求
     * @return 密码修改结果
     * @throws CAuthException 当前密码错误、账号失效或并发修改冲突时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ChangePasswordVO changePassword(ChangePasswordRequest request) {
        CUserPrincipal principal = CUserContext.getRequired();
        CUser user = cUserMapper.selectById(principal.userId());
        // 账号状态为停用
        if (user == null || user.getDeletedAt() != null
                || !ENABLED.getValue().equals(user.getStatus())) {
            throw new CAuthException(UNAUTHORIZED,
                    HttpStatus.UNAUTHORIZED, "当前登录状态无效");
        }
        // 检查旧密码
        if (!BCrypt.checkpw(request.getOldPassword(), user.getPasswordHash())) {
            throw new CAuthException(PASSWORD_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST, "当前密码不正确");
        }
        // 新密码不能与旧密码相同
        if (BCrypt.checkpw(request.getNewPassword(), user.getPasswordHash())) {
            throw new CAuthException(PASSWORD_VALIDATION_FAILED,
                    HttpStatus.BAD_REQUEST, "新密码不能与当前密码相同");
        }

        String oldPasswordHash = user.getPasswordHash();
        CUser passwordUpdate = new CUser();
        passwordUpdate.setPasswordHash(BCrypt.hashpw(request.getNewPassword(), BCrypt.gensalt()));
        int userUpdated = cUserMapper.update(passwordUpdate, Wrappers.<CUser>lambdaUpdate()
                .eq(CUser::getId, principal.userId())
                .eq(CUser::getPasswordHash, oldPasswordHash)
                .isNull(CUser::getDeletedAt));
        if (userUpdated != 1) {
            throw new CAuthException(ErrorCodeEnum.BUSINESS_STATUS_CONFLICT,
                    CONFLICT, "密码状态已变化，请重新登录后重试");
        }

        OffsetDateTime now = OffsetDateTime.now();
        // 获取其他刷新会话
        List<CRefreshToken> otherSessions = refreshTokenMapper.selectList(
                Wrappers.<CRefreshToken>lambdaQuery()
                        .eq(CRefreshToken::getUserId, principal.userId()) // 当前用户
                        .ne(CRefreshToken::getTokenHash, principal.sessionHash()) // 当前会话
                        .isNull(CRefreshToken::getRevokedAt) // 未撤销
                        .gt(CRefreshToken::getExpiredAt, now)); // 未过期
        // 如果有其他刷新会话，则撤销
        if (!otherSessions.isEmpty()) {
            CRefreshToken revokedToken = new CRefreshToken();
            revokedToken.setRevokedAt(now);
            refreshTokenMapper.update(revokedToken, Wrappers.<CRefreshToken>lambdaUpdate()
                    .eq(CRefreshToken::getUserId, principal.userId())
                    .ne(CRefreshToken::getTokenHash, principal.sessionHash())
                    .isNull(CRefreshToken::getRevokedAt)
                    .gt(CRefreshToken::getExpiredAt, now));

            // 密码修改后立即使其他设备的 Access Token 和刷新令牌失效
            List<String> redisKeys = otherSessions.stream()
                    .map(CRefreshToken::getTokenHash) // 获取 Redis 键
                    .map(hash -> REFRESH_SESSION_KEY_PREFIX + hash) // 转换成 Redis 键
                    .toList(); // 转为列表
            redisTemplate.delete(redisKeys);
        }
        return ChangePasswordVO.builder().passwordChanged(true).build();
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
                    BAD_REQUEST, "图形验证码无效或已过期");
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
            throw new CAuthException(INVALID_PARAMETER,
                    BAD_REQUEST, "登录账号长度必须为4至32位");
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
        // 登录失败次数达到阈值
        if (StringUtils.hasText(failureCount)
                && Long.parseLong(failureCount) >= authProperties.getLoginMaxFailures()) {
            throw new CAuthException(PASSWORD_RETRY_LIMIT_EXCEEDED,
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
            //Duration 在这里是把"秒数"包装成"时间段"类型
        }
        if (failureCount != null && failureCount >= authProperties.getLoginMaxFailures()) {
            throw new CAuthException(PASSWORD_RETRY_LIMIT_EXCEEDED,
                    HttpStatus.TOO_MANY_REQUESTS, "登录失败次数过多，请稍后重试");
        }
        throw new CAuthException(LOGIN_FAILED,
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
                REFRESH_SESSION_KEY_PREFIX + tokenHash,
                userId + ":" + entity.getId(),
                Duration.ofSeconds(authProperties.getRefreshTokenExpiration())//Duration 在这里是把"秒数"包装成"时间段"类型
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
        return LOGIN_FAILURE_KEY_PREFIX + CAuthDigestUtil.sha256Hex(account);
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
        return new CAuthException(UNAUTHORIZED,
                HttpStatus.UNAUTHORIZED, "刷新令牌无效或已撤销");
    }
}
