package com.sphp.admin.pharmacy.service;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.pharmacy.dto.DrugCreateRequest;
import com.sphp.admin.pharmacy.dto.DrugListVO;
import com.sphp.admin.pharmacy.dto.DrugUpdateRequest;

/**
 * 药品目录服务（管理员视角）。
 *
 * <p>按当前登录管理员所属医院（{@code hospital_id}）做数据隔离；
 * 药品状态字段使用 {@link com.sphp.admin.common.enums.BUserStatusEnum}，
 * 批准文号唯一性按医院维度校验。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
public interface DrugService {
    /**
     * 分页查询药品目录。
     *
     * @param name   药品名称模糊检索（{@code null} 或空白表示不过滤）
     * @param status 状态过滤（{@code null} 或空白表示不过滤）
     * @param page   页码（从 1 开始）
     * @param size   每页大小
     * @return 分页结果
     */
    PageResult<DrugListVO> page(String name, String status, int page, int size);

    /**
     * 新增药品。医院归属由后端根据当前管理员所属医院自动填充。
     *
     * @param request 新增药品请求
     * @throws com.sphp.shared.exception.BusinessException 当批准文号已存在或状态非法时抛出
     */
    void create(DrugCreateRequest request);

    /**
     * 编辑药品基本信息与状态。
     *
     * @param id      药品 ID
     * @param request 编辑药品请求（仅非空字段生效）
     * @throws com.sphp.shared.exception.BusinessException 当药品不存在/越权、批准文号重复或状态非法时抛出
     */
    void update(Long id, DrugUpdateRequest request);

    /**
     * 软删除药品。存在库存记录时拒绝删除，避免库存出现孤儿药品。
     *
     * @param id 药品 ID
     * @throws com.sphp.shared.exception.BusinessException 当药品不存在/越权或存在库存记录时抛出
     */
    void delete(Long id);
}
