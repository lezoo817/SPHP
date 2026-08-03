package com.sphp.admin.hospital.controller;

import com.sphp.admin.hospital.dto.HospitalUpdateRequest;
import com.sphp.admin.hospital.service.HospitalService;
import com.sphp.admin.hospital.vo.HospitalVO;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 医院信息管理接口（管理员，系分 §5.3.1~5.3.2）。
 *
 * <p>注意：application.yml 已配置 {@code server.servlet.context-path=/api}，
 * 因此控制器映射路径为相对路径 {@code /b/admin/hospitals}，外部完整 URL 为 {@code /api/b/admin/hospitals}。
 */
@RestController
@RequestMapping("/b/admin/hospitals")
@Tag(name = "2-医院管理", description = "查询/编辑医院信息（管理员）")
@RequiredArgsConstructor
public class HospitalController {

    private final HospitalService hospitalService;

    @GetMapping
    @Operation(summary = "查询医院信息", description = "返回当前管理员所属医院信息")
    public Result<HospitalVO> get() {
        return Result.success("查询成功", hospitalService.get());
    }

    @PutMapping("/{id}")
    @Operation(summary = "编辑医院信息", description = "编辑当前管理员所属医院信息（仅更新传入的非空字段）")
    public Result<Void> update(@PathVariable Long id, @Valid @RequestBody HospitalUpdateRequest request) {
        hospitalService.update(id, request);
        return Result.success("编辑成功", null);
    }
}
