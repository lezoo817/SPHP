package com.sphp.admin.prescription.service;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.pharmacy.dto.DrugListVO;
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
     * 按 ID 查询当前医院药品（新建模板自动带出药品名称/规格）。
     *
     * @param id 药品 ID
     * @return 药品信息；不存在或不属于当前医院抛 A0402
     */
    DrugListVO getDrug(Long id);
}