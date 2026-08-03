package com.sphp.admin.prescription.controller;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.prescription.dto.SaveTemplateRequest;
import com.sphp.admin.prescription.dto.TemplateListVO;
import com.sphp.admin.prescription.service.PrescriptionTemplateService;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处方模板控制器（系分 §5.6.6 ~ §5.6.7）。
 */
@RestController
@RequestMapping("/b")
@Tag(name = "5-处方模板", description = "处方模板列表/创建/删除")
@RequiredArgsConstructor
public class PrescriptionTemplateController {

    private static final int MAX_PAGE_SIZE = 100;

    private static int clampSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private final PrescriptionTemplateService templateService;

    @GetMapping("/prescription-templates")
    @Operation(summary = "处方模板列表", description = "分页查询处方模板（按名称模糊搜索、科室过滤）")
    public Result<PageResult<TemplateListVO>> page(
            @Parameter(description = "模板名称模糊搜索") @RequestParam(required = false) String name,
            @Parameter(description = "科室 ID（空返回全部可用模板）") @RequestParam(required = false) Long deptId,
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") int size) {
        return Result.success("查询成功",
                templateService.page(name, deptId, page, clampSize(size)));
    }

    @PostMapping("/prescription-templates")
    @Operation(summary = "保存处方模板", description = "创建新处方模板（需医生身份）")
    public Result<TemplateListVO> save(@RequestBody SaveTemplateRequest request) {
        return Result.success("创建成功", templateService.save(request));
    }

    @DeleteMapping("/prescription-templates/{id}")
    @Operation(summary = "删除处方模板", description = "软删除处方模板")
    public Result<String> delete(@PathVariable Long id) {
        templateService.delete(id);
        return Result.success("删除成功");
    }
}