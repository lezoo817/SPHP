package com.sphp.admin.pharmacy.service;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.pharmacy.dto.DrugCreateRequest;
import com.sphp.admin.pharmacy.dto.DrugListVO;
import com.sphp.admin.pharmacy.dto.DrugUpdateRequest;

/** 药品目录服务（系分 §5.7.1~5.7.2）。 */
public interface DrugService {
    PageResult<DrugListVO> page(String name, String status, int page, int size);

    void create(DrugCreateRequest request);

    void update(Long id, DrugUpdateRequest request);

    void delete(Long id);
}
