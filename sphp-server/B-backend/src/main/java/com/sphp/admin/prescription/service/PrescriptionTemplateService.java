package com.sphp.admin.prescription.service;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.pharmacy.dto.DrugListVO;
import com.sphp.admin.prescription.dto.PrescriptionSubmitVO;
import com.sphp.admin.prescription.dto.SaveTemplateRequest;
import com.sphp.admin.prescription.dto.TemplateListVO;

/**
 * 处方模板服务接口。
 */
public interface PrescriptionTemplateService {

    /**
     * 分页查询处方模板列表（系分 §5.6.6）。
     */
    PageResult<TemplateListVO> page(String name, Long deptId, int page, int size);

    /**
     * 保存处方模板（系分 §5.6.7）。
     */
    TemplateListVO save(SaveTemplateRequest request);

    /**
     * 删除处方模板（软删除）。
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
     */
    PrescriptionSubmitVO apply(Long templateId, Long consultId);

    /**
     * 按 ID 查询当前医院药品（新建模板自动带出药品名称/规格）。
     *
     * @param id 药品 ID
     * @return 药品信息；不存在或不属于当前医院抛 A0402
     */
    DrugListVO getDrug(Long id);
}