package com.sphp.admin.prescription.controller;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.pharmacy.dto.DrugListVO;
import com.sphp.admin.prescription.dto.ApplyTemplateRequest;
import com.sphp.admin.prescription.dto.PrescriptionSubmitVO;
import com.sphp.admin.prescription.dto.SaveTemplateRequest;
import com.sphp.admin.prescription.dto.TemplateListVO;
import com.sphp.admin.prescription.service.PrescriptionTemplateService;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 处方模板控制器（管理员视角）。
 *
 * <p>外部完整 URL 前缀为 {@code /api/b/...}。
 * 按当前登录用户所属医院做数据隔离过滤；
 * 创建/更新需医生身份，查询/删除不限角色。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@RestController
@RequestMapping("/b")
@Tag(name = "处方模板", description = "处方模板列表/创建/删除")
@RequiredArgsConstructor
public class PrescriptionTemplateController {

    private static final int MAX_PAGE_SIZE = 100;

    private static int clampSize(int size) {
        return Math.clamp(size, 1, MAX_PAGE_SIZE);
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
    @Operation(summary = "保存处方模板", description = "创建新处方模板（需医生身份）；校验药品存在且数量充足（天数×频次×用量 ≤ 数量×规格）")
    public Result<TemplateListVO> save(@RequestBody SaveTemplateRequest request) {
        return Result.success("创建成功", templateService.save(request));
    }

    @PutMapping("/prescription-templates/{id}")
    @Operation(summary = "更新处方模板", description = "更新处方模板的科室与药品明细；模板名称不可修改；需医生身份")
    public Result<TemplateListVO> update(@PathVariable Long id, @RequestBody SaveTemplateRequest request) {
        return Result.success("更新成功", templateService.update(id, request));
    }

    @GetMapping("/prescription-templates/drugs/{id}")
    @Operation(summary = "查询药品（模板选药）", description = "按 ID 查询当前医院药品，供新建模板自动带出药品名称与规格；不存在或越权返回 A0402")
    public Result<DrugListVO> getDrug(@PathVariable Long id) {
        return Result.success("查询成功", templateService.getDrug(id));
    }

    @DeleteMapping("/prescription-templates/{id}")
    @Operation(summary = "删除处方模板", description = "软删除处方模板")
    public Result<String> delete(@PathVariable Long id) {
        templateService.delete(id);
        return Result.success("删除成功");
    }

    @PostMapping("/prescription-templates/{id}/apply")
    @Operation(summary = "应用处方模板开方", description = "应用即开方：执行风险拦截（过敏/禁忌 ERROR 拒绝，高危 AUDIT 待审，其余直接 APPROVED）；返回与提交处方一致的结果结构")
    public Result<PrescriptionSubmitVO> apply(@PathVariable Long id,
                                              @Valid @RequestBody ApplyTemplateRequest request) {
        return Result.success("开方成功", templateService.apply(id, request.getConsultId()));
    }
}