package com.sphp.admin.auth.service.impl;

import cn.dev33.satoken.SaManager;
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
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.enums.BUserStatusEnum;
import com.sphp.shared.common.enums.ErrorCodeEnum;
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
 * B 端认证服务实现。
 *
 * <p>令牌签发策略：
 * <ul>
 *   <li>accessToken — 由 Sa-Token 签发的无状态 JWT，有效期与 Sa-Token 配置一致（默认 7200s）。</li>
 *   <li>refreshToken — 自产不透明令牌（{@code rt_} + 32 字节 Base64Url），仅 SHA-256 哈希入库
 *       {@code b_refresh_token}；原文仅返回客户端，刷新时校验 + 轮换。</li>
 * </ul>
 *
 * <p>退出登录说明：accessToken 为无状态 JWT，服务端无法主动失效；当前实现仅吊销该用户全部有效
 * refreshToken，accessToken 等待其剩余有效期自然过期。如需 accessToken 立即失效，
 * 需引入黑名单（建议 Redis），不在本模块范围内。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuthServiceImpl implements AuthService {

    /** 刷新令牌有效期：30 天（秒）。 */
    private static final long REFRESH_TOKEN_TTL_SECONDS = 30L * 24 * 3600;

    /** 刷新令牌前缀标识。 */
    private static final String REFRESH_TOKEN_PREFIX = "rt_";

    /** 刷新令牌随机字节长度（256 bit，熵充足）。 */
    private static final int REFRESH_TOKEN_RANDOM_BYTES = 32;

    private final BUserMapper bUserMapper;
    private final BRefreshTokenMapper bRefreshTokenMapper;
    private final DoctorMapper doctorMapper;
    private final CurrentUserService currentUserService;

    /**
     * 账号密码登录。
     *
     * <p>校验流程：账号存在性 → 密码（BCrypt） → 账号状态 → 签发 token 对。
     * 为避免账号枚举，账号不存在与密码错误统一提示。
     *
     * @param request 登录请求（账号 / 密码）
     * @return 登录响应（accessToken + refreshToken + 用户信息）
     * @throws BusinessException 账号或密码错误 / 账号已停用
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LoginVO login(LoginRequest request) {
        // 软删过滤：仅查询未删除账号
        BUser user = bUserMapper.selectOne(Wrappers.<BUser>lambdaQuery()
                .eq(BUser::getAccount, request.getUsername())
                .isNull(BUser::getDeletedAt));

        // 账号不存在与密码错误统一提示，避免账号枚举
        if (user == null || !BCrypt.checkpw(request.getPassword(), user.getPasswordHash())) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED, "账号或密码错误");
        }
        if (!BUserStatusEnum.isEnabled(user.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED, "账号已停用，请联系管理员");
        }

        StpUtil.login(user.getId());
        String accessToken = StpUtil.getTokenValue();

        String refreshToken = generateRefreshToken();
        saveRefreshToken(user.getId(), refreshToken);

        UserInfoVO userInfo = buildUserInfo(user);

        log.info("用户登录成功 userId={}, account={}", user.getId(), user.getAccount());
        return LoginVO.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(SaManager.getConfig().getTimeout())
                .userInfo(userInfo)
                .build();
    }

    /**
     * 刷新令牌。
     *
     * <p>校验旧 refreshToken（哈希 + 未吊销 + 未过期）→ 校验用户有效 → 轮换签发新 token 对。
     * 旧 refreshToken 在签发新令牌后立即吊销，保证一次性使用。
     *
     * @param request 刷新请求（旧 refreshToken）
     * @return 新 token 对
     * @throws BusinessException refreshToken 无效 / 过期 / 用户失效 / 账号已停用
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RefreshTokenVO refresh(RefreshTokenRequest request) {
        BRefreshToken refreshToken = bRefreshTokenMapper.selectOne(Wrappers.<BRefreshToken>lambdaQuery()
                .eq(BRefreshToken::getTokenHash, sha256Hex(request.getRefreshToken()))
                .isNull(BRefreshToken::getRevokedAt)
                .gt(BRefreshToken::getExpiredAt, OffsetDateTime.now()));
        if (refreshToken == null) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED, "刷新令牌无效或已过期");
        }

        BUser user = bUserMapper.selectById(refreshToken.getUserId());
        if (user == null || user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED, "用户不存在");
        }
        if (!BUserStatusEnum.isEnabled(user.getStatus())) {
            throw new BusinessException(ErrorCodeEnum.UNAUTHORIZED, "账号已停用，请联系管理员");
        }

        // 轮换：吊销旧 refreshToken，签发新 token 对
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

    /**
     * 解析当前请求 Token，返回用户上下文。
     *
     * <p>供 Agent 通道调用：依赖 Sa-Token 自动剥离 Bearer 前缀，取出当前登录用户并按需补全
     * 医生维度（deptId）。令牌剩余有效期由 Sa-Token 推算。
     *
     * @return Token 解析响应（用户身份 + 角色 + 医生/科室维度 + 过期时间）
     */
    @Override
    public TokenParseVO parseToken() {
        BUser user = currentUserService.getCurrentUser();

        // 医生维度补全：b_user 不含 dept_id，经 doctor_id 联查 doctor 表
        Long deptId = null;
        if (user.getDoctorId() != null) {
            Doctor doctor = doctorMapper.selectById(user.getDoctorId());
            deptId = (doctor == null) ? null : doctor.getDeptId();
        }

        // Sa-Token：getTokenTimeout 返回秒；负数表示无超时或已过期
        long timeout = StpUtil.getTokenTimeout();
        OffsetDateTime tokenExpiresAt = (timeout < 0) ? null : OffsetDateTime.now().plusSeconds(timeout);

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

    /**
     * 退出登录。
     *
     * <p>安全语义：吊销该用户全部有效 refreshToken，阻断后续续期。
     * accessToken 为无状态 JWT，服务端无会话可注销，等待自然过期（见类注释）。
     *
     * @return 退出结果（始终为 loggedOut=true，除非 token 已失效）
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public LogoutVO logout() {
        // 复用 CurrentUserService 统一异常处理（无效 token 抛 A0301）
        Long userId = currentUserService.getCurrentUser().getId();
        int revoked = revokeAllRefreshTokens(userId);

        log.info("用户退出登录 userId={}, 吊销刷新令牌数={}", userId, revoked);
        return LogoutVO.builder().loggedOut(true).build();
    }

    /**
     * 组装登录用户信息 VO。
     *
     * <p>b_user 表无姓名 / 科室列，姓名与科室需经 doctor_id 联查 doctor 表补全；
     * 管理员（{@code doctorId == null}）姓名取登录账号，deptId 为 null。
     *
     * @param user 登录用户实体
     * @return 登录用户信息 VO
     * @throws BusinessException 关联了 doctorId 但未查到医生记录（数据异常）
     */
    private UserInfoVO buildUserInfo(BUser user) {
        String name = user.getAccount();
        Long deptId = null;
        if (user.getDoctorId() != null) {
            Doctor doctor = doctorMapper.selectById(user.getDoctorId());
            if (doctor == null) {
                throw new BusinessException(ErrorCodeEnum.INVALID_PARAMETER, "账号未关联有效的医生信息");
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

    /**
     * 生成不透明刷新令牌。
     *
     * <p>格式：{@code rt_<Base64Url 编码的 32 字节随机数>}。使用 {@link SecureRandom} 保证熵，
     * 原文仅在签发瞬间返回给客户端一次。
     *
     * @return 新 refreshToken 原文
     */
    private String generateRefreshToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_RANDOM_BYTES];
        new SecureRandom().nextBytes(bytes);
        return REFRESH_TOKEN_PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /**
     * 刷新令牌入库。
     *
     * <p>仅存储 SHA-256 哈希（{@code tokenHash}），原文绝不落库；过期时间取当前时间 +
     * {@link #REFRESH_TOKEN_TTL_SECONDS}。
     *
     * @param userId  关联 b_user.id
     * @param rawToken 原始 refreshToken 原文
     */
    private void saveRefreshToken(Long userId, String rawToken) {
        BRefreshToken entity = new BRefreshToken();
        entity.setUserId(userId);
        entity.setTokenHash(sha256Hex(rawToken));
        entity.setExpiredAt(OffsetDateTime.now().plusSeconds(REFRESH_TOKEN_TTL_SECONDS));
        bRefreshTokenMapper.insert(entity);
    }

    /**
     * 吊销指定用户全部有效刷新令牌。
     *
     * @param userId b_user.id
     * @return 受影响行数
     */
    private int revokeAllRefreshTokens(Long userId) {
        BRefreshToken update = new BRefreshToken();
        update.setRevokedAt(OffsetDateTime.now());
        return bRefreshTokenMapper.update(update, Wrappers.<BRefreshToken>lambdaUpdate()
                .eq(BRefreshToken::getUserId, userId)
                .isNull(BRefreshToken::getRevokedAt));
    }

    /**
     * SHA-256 十六进制摘要（用于刷新令牌哈希）。
     *
     * <p>SHA-256 在 JDK 中为强制实现，{@link NoSuchAlgorithmException} 仅在极端环境
     * （如安全策略文件禁用）下抛出，故包装为 {@link IllegalStateException}。
     *
     * @param input 原始字符串
     * @return 小写十六进制摘要
     */
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
