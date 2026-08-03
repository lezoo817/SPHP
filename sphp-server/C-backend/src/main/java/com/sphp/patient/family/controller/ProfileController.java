package com.sphp.patient.family.controller;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.family.dto.ProfileUpdateRequest;
import com.sphp.patient.family.service.ProfileService;
import com.sphp.patient.family.vo.ProfileUpdateVO;
import com.sphp.patient.family.vo.ProfileVO;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import com.sphp.shared.common.constant.HeaderConstant;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * C端当前账号本人资料接口。
 */
@RestController
@Validated
@RequestMapping("/c/v1/profile")
@Tag(name = "C端个人资料", description = "查询和维护当前账号本人资料")
@RequiredArgsConstructor
public class ProfileController {

    private final ProfileService profileService;
    private final CIdempotencyService idempotencyService;

    /**
     * 查询当前登录账号本人资料。
     *
     * @return 脱敏后的本人资料
     */
    @GetMapping
    @Operation(summary = "查询个人资料")
    public Result<ProfileVO> getProfile() {
        return Result.success("查询成功", profileService.getProfile());
    }

    /**
     * 更新当前登录账号本人资料。
     *
     * @param idempotencyKey 客户端幂等键
     * @param request 本人资料更新请求
     * @return 更新后的最小资料摘要
     */
    @PutMapping
    @Operation(summary = "更新个人资料")
    public Result<ProfileUpdateVO> updateProfile(
            @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey,
            @Valid @RequestBody ProfileUpdateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<ProfileUpdateVO> payload = idempotencyService.execute(
                userId,
                "/c/v1/profile",
                idempotencyKey,
                request,
                ProfileUpdateVO.class,
                () -> new IdempotencyPayload<>("个人资料已更新", profileService.updateProfile(request))
        );
        return Result.success(payload.message(), payload.data());
    }
}
