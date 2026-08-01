package com.sphp.patient.family.controller;

import com.sphp.patient.family.service.FamilyService;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.family.dto.FamilyMemberCreateRequest;
import com.sphp.patient.family.vo.FamilyMemberCreateVO;
import com.sphp.patient.family.vo.FamilyMemberListVO;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import com.sphp.shared.common.constant.HeaderConstant;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
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
}
