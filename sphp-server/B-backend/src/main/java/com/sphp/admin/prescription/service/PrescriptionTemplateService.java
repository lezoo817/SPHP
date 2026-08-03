package com.sphp.admin.prescription.service;

import com.sphp.admin.common.vo.PageResult;
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
}