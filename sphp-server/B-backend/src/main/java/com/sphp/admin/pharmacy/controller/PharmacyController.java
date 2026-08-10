package com.sphp.admin.pharmacy.controller;

import com.sphp.admin.pharmacy.entity.Pharmacy;
import com.sphp.admin.pharmacy.service.PharmacyService;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 药房管理接口（管理员视角）。
 *
 * <p>外部完整 URL 前缀为 {@code /api/b/admin/pharmacies}。所有接口按当前登录管理员
 * 所属医院（{@code hospital_id}）做数据隔离，仅返回本院启用状态的药房。
 */
@RestController
@RequestMapping("/b/admin/pharmacies")
@Tag(name = "药房管理", description = "药房列表查询（管理员）")
@RequiredArgsConstructor
public class PharmacyController {

    private final PharmacyService pharmacyService;

    @GetMapping
    @Operation(summary = "查询药房列表", description = "返回当前医院所有启用药房，供下拉筛选使用")
    public Result<List<Pharmacy>> list() {
        return Result.success("查询成功", pharmacyService.listEnabled());
    }
}