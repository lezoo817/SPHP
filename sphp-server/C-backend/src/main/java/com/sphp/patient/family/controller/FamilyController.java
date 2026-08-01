package com.sphp.patient.family.controller;

import com.sphp.patient.family.service.FamilyService;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.family.dto.FamilyMemberCreateRequest;
import com.sphp.patient.family.dto.FamilyMemberUpdateRequest;
import com.sphp.patient.family.vo.FamilyMemberCreateVO;
import com.sphp.patient.family.vo.FamilyMemberListVO;
import com.sphp.patient.family.vo.FamilyMemberUpdateVO;
import com.sphp.patient.family.vo.FamilyMemberUnbindVO;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import com.sphp.shared.common.constant.HeaderConstant;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import org.springframework.validation.annotation.Validated;

import java.util.List;

/**
 * C端家庭成员管理接口。
 */
@RestController
@Validated
@RequestMapping("/c/v1/family-members")
@Tag(name = "C端家庭成员", description = "查询、新增、更新和停用解绑家庭成员")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;
    private final CIdempotencyService idempotencyService;

    /**
     * 查询当前账号的本人及有效家庭成员。
     *
     * @return 家庭成员列表
     */
    @GetMapping
    @Operation(summary = "查询家庭成员")
    public Result<List<FamilyMemberListVO>> listFamilyMembers() {
        return Result.success("查询成功", familyService.listFamilyMembers());
    }

    /**
     * 新增当前账号下的家庭成员。
     *
     * @param idempotencyKey 客户端幂等键
     * @param request 新增家庭成员请求
     * @return 新建家庭成员信息
     */
    @PostMapping
    @Operation(summary = "新增家庭成员")
    public Result<FamilyMemberCreateVO> createFamilyMember(
            @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey,
            @Valid @RequestBody FamilyMemberCreateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<FamilyMemberCreateVO> payload = idempotencyService.execute(
                userId,
                "/c/v1/family-members",
                idempotencyKey,
                request,
                FamilyMemberCreateVO.class,
                () -> new IdempotencyPayload<>("家庭成员已添加", familyService.createFamilyMember(request))
        );
        return Result.success(payload.message(), payload.data());
    }

    /**
     * 更新当前账号下的非本人家庭成员。
     *
     * @param patientId 就诊人 ID
     * @param idempotencyKey 客户端幂等键
     * @param request 更新家庭成员请求
     * @return 更新后的家庭成员信息
     */
    @PutMapping("/{patientId}")
    @Operation(summary = "更新家庭成员")
    public Result<FamilyMemberUpdateVO> updateFamilyMember(
            @PathVariable @Positive(message = "就诊人ID必须为正整数") Long patientId,
            @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey,
            @Valid @RequestBody FamilyMemberUpdateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<FamilyMemberUpdateVO> payload = idempotencyService.execute(
                userId,
                "/c/v1/family-members/" + patientId,
                idempotencyKey,
                request,
                FamilyMemberUpdateVO.class,
                () -> new IdempotencyPayload<>("家庭成员资料已更新", familyService.updateFamilyMember(patientId, request))
        );
        return Result.success(payload.message(), payload.data());
    }

    /**
     * 停用解绑当前账号下的非本人家庭成员。
     *
     * @param patientId 就诊人 ID
     * @param idempotencyKey 客户端幂等键
     * @return 解绑结果
     */
    @DeleteMapping("/{patientId}")
    @Operation(summary = "停用解绑家庭成员")
    public Result<FamilyMemberUnbindVO> unbindFamilyMember(
            @PathVariable @Positive(message = "就诊人ID必须为正整数") Long patientId,
            @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<FamilyMemberUnbindVO> payload = idempotencyService.execute(
                userId,
                "/c/v1/family-members/" + patientId,
                idempotencyKey,
                patientId,
                FamilyMemberUnbindVO.class,
                () -> new IdempotencyPayload<>("家庭成员已解绑", familyService.unbindFamilyMember(patientId))
        );
        return Result.success(payload.message(), payload.data());
    }
}
