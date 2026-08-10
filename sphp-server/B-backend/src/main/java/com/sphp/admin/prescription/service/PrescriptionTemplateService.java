package com.sphp.admin.prescription.service;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.pharmacy.dto.DrugListVO;
import com.sphp.admin.prescription.dto.PrescriptionSubmitVO;
import com.sphp.admin.prescription.dto.SaveTemplateRequest;
import com.sphp.admin.prescription.dto.TemplateListVO;

/**
 * 处方模板服务接口。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
public interface PrescriptionTemplateService {

    /**
     * 分页查询处方模板列表。
     *
     * <p>仅返回当前医院启用状态模板；按名称模糊、科室过滤。
     *
     * @param name   模板名称模糊搜索（可选）
     * @param deptId 科室 ID 过滤（空返回全部可用模板，含全院通用）
     * @param page   页码（从 1 开始）
     * @param size   每页大小
     * @return 模板分页结果
     */
    PageResult<TemplateListVO> page(String name, Long deptId, int page, int size);

    /**
     * 保存处方模板。
     *
     * <p>需医生身份；同医院防同名；模板默认启用状态。
     *
     * @param request 模板保存请求（名称、科室、药品明细）
     * @return 创建后的模板信息
     * @throws com.sphp.shared.exception.BusinessException 无医生身份/参数非法/药品不可用
     */
    TemplateListVO save(SaveTemplateRequest request);

    /**
     * 更新处方模板。
     *
     * <p>可更新字段：科室（deptId）、药品明细（items）。
     * 模板名称不可修改，防止引用断裂。
     *
     * @param id      模板 ID
     * @param request 更新请求（仅读取 deptId、items）
     * @return 更新后的模板信息
     * @throws com.sphp.shared.exception.BusinessException 无权限/不存在/参数非法
     */
    TemplateListVO update(Long id, SaveTemplateRequest request);

    /**
     * 删除处方模板（软删除）。
     *
     * @param id 模板 ID
     * @throws com.sphp.shared.exception.BusinessException 不存在或越权
     */
    void delete(Long id);

    /**
     * 应用模板开方（应用即开方，无草稿）。
     *
     * <p>校验模板归属当前医院且启用，组装明细后复用共享开方方法
     * （问诊校验 + 药品校验 + 风险拦截 + 建单）。
     *
     * @param templateId 模板 ID
     * @param consultId  问诊记录 ID
     * @return 处方提交结果（含风险警告与状态）
     * @throws com.sphp.shared.exception.BusinessException 模板无效/越权
     */
    PrescriptionSubmitVO apply(Long templateId, Long consultId);

    /**
     * 按 ID 查询当前医院药品（新建模板自动带出药品名称/规格）。
     *
     * @param id 药品 ID
     * @return 药品信息；不存在或不属于当前医院抛 A0402
     * @throws com.sphp.shared.exception.BusinessException 不存在或越权
     */
    DrugListVO getDrug(Long id);
}