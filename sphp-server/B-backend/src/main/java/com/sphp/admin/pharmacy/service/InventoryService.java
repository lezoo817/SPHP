package com.sphp.admin.pharmacy.service;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.pharmacy.dto.InventoryAlertVO;
import com.sphp.admin.pharmacy.dto.InventoryListVO;
import com.sphp.admin.pharmacy.dto.InventoryUnlockRequest;
import com.sphp.admin.pharmacy.dto.InventoryUpdateRequest;

import java.util.List;

/** 药品库存管理服务（系分 §5.7.3~5.7.6）。 */
public interface InventoryService {
    PageResult<InventoryListVO> page(Long drugId, Long pharmacyId, int page, int size);

    void update(Long id, InventoryUpdateRequest request);

    List<InventoryAlertVO> alerts(Long pharmacyId);

    void unlock(Long id, InventoryUnlockRequest request);
}
