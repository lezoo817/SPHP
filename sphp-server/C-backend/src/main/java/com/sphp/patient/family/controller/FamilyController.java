package com.sphp.patient.family.controller;

import com.sphp.patient.family.service.FamilyService;
import com.sphp.patient.family.vo.FamilyMemberListVO;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C端家庭成员管理接口。
 */
@RestController
@RequestMapping("/c/v1/family-members")
@Tag(name = "C端家庭成员", description = "查询、新增、更新和停用解绑家庭成员")
@RequiredArgsConstructor
public class FamilyController {

    private final FamilyService familyService;

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
}
