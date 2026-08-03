package com.sphp.patient.family.controller;

import com.sphp.patient.family.service.ProfileService;
import com.sphp.patient.family.vo.ProfileVO;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
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
}
