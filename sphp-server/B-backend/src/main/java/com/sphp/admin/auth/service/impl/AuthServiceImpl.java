package com.sphp.admin.auth.service.impl;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.exception.NotLoginException;
import cn.dev33.satoken.stp.StpUtil;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sphp.admin.auth.dto.LoginRequest;
import com.sphp.admin.auth.dto.RefreshTokenRequest;
import com.sphp.admin.auth.entity.BRefreshToken;
import com.sphp.admin.auth.entity.BUser;
import com.sphp.admin.auth.entity.Doctor;
import com.sphp.admin.auth.mapper.BRefreshTokenMapper;
import com.sphp.admin.auth.mapper.BUserMapper;
import com.sphp.admin.auth.mapper.DoctorMapper;
import com.sphp.admin.auth.service.AuthService;
import com.sphp.admin.auth.vo.LoginVO;
import com.sphp.admin.auth.vo.LogoutVO;
import com.sphp.admin.auth.vo.RefreshTokenVO;
import com.sphp.admin.auth.vo.TokenParseVO;
import com.sphp.admin.auth.vo.UserInfoVO;
import com.sphp.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.List;

/**
 * B端认证服务实现。
 *
 * <p>accessToken 由 Sa-Token 签发（无状态 JWT，有效期 7200s）；refreshToken 为自产不透明令牌，
 * 仅 SHA-256 哈希入库 {@code b_refresh_token}，刷新时校验+轮换。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 刷新令牌有效期：30 天（秒） */
    private static final long REFRESH_TOKEN_TTL_SECONDS = 30L * 24 * 3600;

    private final BUserMapper bUserMapper;
    private final BRefreshTokenMapper bRefreshTokenMapper;
    private final DoctorMapper doctorMapper;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginRequest request) {
        // 1. 按账号查询有效用户（软删过滤）
        BUser user = bUserMapper.selectOne(Wrappers.<BUser>lambdaQuery()
                .eq(BUser::getAccount, request.getUsername())
                .isNull(BUser::getDeletedAt));

        // 2. 账号不存在或密码错误统一提示，避免账号枚举
        if (user == null || !BCrypt.checkpw(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException("A0301", "账号或密码错误");
        }
        // 3. 账号状态校验
        if (!"ENABLED".equals(user.getStatus())) {
            throw new BusinessException("A0301", "账号已停用，请联系管理员");
        }

        // 4. Sa-Token 登录，签发 accessToken（JWT）
        StpUtil.login(user.getId());
        String accessToken = StpUtil.getTokenValue();

        // 5. 生成刷新令牌并入库（仅存哈希）
        String refreshToken = generateRefreshToken();
        saveRefreshToken(user.getId(), refreshToken);

        // 6. 组装用户信息
        UserInfoVO userInfo = buildUserInfo(user);

        log.info("用户登录成功 userId={}, account={}", user.getId(), user.getAccount());
        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(SaManager.getConfig().getTimeout())
                .userInfo(userInfo)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefreshTokenVO refresh(RefreshTokenRequest request) {
        // 1. 按哈希查有效（未吊销且未过期）刷新令牌
        BRefreshToken refreshToken = bRefreshTokenMapper.selectOne(Wrappers.<BRefreshToken>lambdaQuery()
                .eq(BRefreshToken::getTokenHash, sha256Hex(request.getRefreshToken()))
                .isNull(BRefreshToken::getRevokedAt)
                .gt(BRefreshToken::getExpiredAt, OffsetDateTime.now()));
        if (refreshToken == null) {
            throw new BusinessException("A0301", "刷新令牌无效或已过期");
        }

        // 2. 校验用户仍存在且启用
        BUser user = bUserMapper.selectById(refreshToken.getUserId());
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException("A0301", "用户不存在");
        }
        if (!"ENABLED".equals(user.getStatus())) {
            throw new BusinessException("A0301", "账号已停用，请联系管理员");
        }

        // 3. 轮换：吊销旧刷新令牌，签发新 token 对
        refreshToken.setRevokedAt(OffsetDateTime.now());
        bRefreshTokenMapper.updateById(refreshToken);

        StpUtil.login(user.getId());
        String newAccessToken = StpUtil.getTokenValue();
        String newRefreshToken = generateRefreshToken();
        saveRefreshToken(user.getId(), newRefreshToken);

        log.info("刷新令牌成功 userId={}", user.getId());
        return RefreshTokenVO.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(SaManager.getConfig().getTimeout())
                .build();
    }

    @Override
    public TokenParseVO parseToken() {
        // 1. 解析当前请求 Token（token-prefix 已配置，自动剥离 Bearer）
        Long userId;
        try {
            userId = StpUtil.getLoginIdAsLong();
        } catch (NotLoginException e) {
            throw new BusinessException("A0301", "Token无效或已过期");
        }

        // 2. 加载用户上下文
        BUser user = bUserMapper.selectById(userId);
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException("A0301", "用户不存在或已停用");
        }

        // 3. 医生维度补全（name 已含于 userInfo，此处取 deptId）
        Long deptId = null;
        if (user.getDoctorId() != null) {
            Doctor doctor = doctorMapper.selectById(user.getDoctorId());
            deptId = doctor == null ? null : doctor.getDeptId();
        }

        // 4. 令牌剩余有效期
        long timeout = StpUtil.getTokenTimeout();
        OffsetDateTime tokenExpiresAt = timeout < 0 ? null : OffsetDateTime.now().plusSeconds(timeout);

        return TokenParseVO.builder()
                .userId(user.getId())
                .account(user.getAccount())
                .roles(List.of(user.getRole()))
                .deptId(deptId)
                .doctorId(user.getDoctorId())
                .hospitalId(user.getHospitalId())
                .tokenExpiresAt(tokenExpiresAt)
                .build();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public LogoutVO logout() {
        // 1. 解析当前登录用户（无效 token 抛 NotLoginException）
        Long userId;
        try {
            userId = StpUtil.getLoginIdAsLong();
        } catch (NotLoginException e) {
            throw new BusinessException("A0301", "Token无效或已过期");
        }

        // 2. 吊销该用户全部有效刷新令牌（阻断续期，logout 的实质安全动作）
        int revoked = revokeAllRefreshTokens(userId);
        // 3. 无状态 JWT（StpLogicJwtForStateless）不支持服务端注销会话：
        //    StpUtil.logout() 内部访问会话 DAO 属禁用 API，会抛 ApiDisabledException。
        //    故 logout 只吊销刷新令牌，accessToken 于 7200s 到期后自然失效；
        //    如需 accessToken 立即失效，需引入黑名单（如 Redis），不在本次范围。

        log.info("用户退出登录 userId={}, 吊销刷新令牌数={}", userId, revoked);
        return LogoutVO.builder().loggedOut(true).build();
    }

    /** 组装登录用户信息：姓名/科室经 doctor_id 联查，ADMIN 无医生维度时姓名取账号 */
    private UserInfoVO buildUserInfo(BUser user) {
        String name = user.getAccount();
        Long deptId = null;
        if (user.getDoctorId() != null) {
            Doctor doctor = doctorMapper.selectById(user.getDoctorId());
            if (doctor == null) {
                throw new BusinessException("A0400", "账号未关联有效的医生信息");
            }
            name = doctor.getName();
            deptId = doctor.getDeptId();
        }
        return UserInfoVO.builder()
                .id(user.getId())
                .name(name)
                .roles(List.of(user.getRole()))
                .deptId(deptId)
                .doctorId(user.getDoctorId())
                .hospitalId(user.getHospitalId())
                .build();
    }

    /** 生成不透明刷新令牌：rt_ + 32 字节 Base64Url（无填充） */
    private String generateRefreshToken() {
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return "rt_" + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** 刷新令牌入库（仅存 SHA-256 哈希，原文只返回客户端） */
    private void saveRefreshToken(Long userId, String rawToken) {
        BRefreshToken entity = new BRefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(sha256Hex(rawToken));
        entity.setExpiredAt(OffsetDateTime.now().plusSeconds(REFRESH_TOKEN_TTL_SECONDS));
        bRefreshTokenMapper.insert(entity);
    }

    /** 吊销用户全部有效刷新令牌，返回受影响行数 */
    private int revokeAllRefreshTokens(Long userId) {
        BRefreshToken update = new BRefreshToken();
        update.setRevokedAt(OffsetDateTime.now());
        return bRefreshTokenMapper.update(update, Wrappers.<BRefreshToken>lambdaUpdate()
                .eq(BRefreshToken::getUserId, userId)
                .isNull(BRefreshToken::getRevokedAt));
    }

    /** SHA-256 十六进制摘要（刷新令牌哈希） */
    private static String sha256Hex(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 算法不可用", e);
        }
    }
}
