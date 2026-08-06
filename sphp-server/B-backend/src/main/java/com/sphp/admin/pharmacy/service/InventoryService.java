package com.sphp.admin.pharmacy.service;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.pharmacy.dto.InventoryAlertVO;
import com.sphp.admin.pharmacy.dto.InventoryListVO;
import com.sphp.admin.pharmacy.dto.InventoryUnlockRequest;
import com.sphp.admin.pharmacy.dto.InventoryUpdateRequest;

import java.util.List;

/**
 * 药品库存管理服务（管理员视角）。
 *
 * <p>按当前登录管理员所属医院（{@code hospital_id}）做数据隔离；
 * 库存按药房归属（{@code pharmacy.hospital_id}）间接过滤。
 *
 * <p>库存状态由 {@code available_count} 与 {@code safety_stock} 比值派生：
 * <ul>
 *   <li>NORMAL：{@code available >= safety * 2}</li>
 *   <li>LOW：{@code safety <= available < safety * 2}</li>
 *   <li>ALERT：{@code available < safety}</li>
 * </ul>
 */
public interface InventoryService {
    /**
     * 分页查询库存列表。
     *
     * @param drugId     药品 ID 过滤（{@code null} 表示不过滤）
     * @param pharmacyId 药房 ID 过滤（{@code null} 时按药品汇总全部药房库存）
     * @param page       页码（从 1 开始）
     * @param size       每页大小
     * @return 分页结果
     */
    PageResult<InventoryListVO> page(Long drugId, Long pharmacyId, int page, int size);

    /**
     * 更新可售/锁定/安全库存与单价（仅非空字段生效，负数拒绝）。
     *
     * @param id      库存 ID
     * @param request 更新库存请求
     * @throws com.sphp.shared.exception.BusinessException 当库存不存在/越权或数值为负时抛出
     */
    void update(Long id, InventoryUpdateRequest request);

    /**
     * 低库存预警查询，返回 {@code available < safety * 2} 的 ALERT/LOW 状态库存。
     *
     * @param pharmacyId 药房 ID 过滤（{@code null} 表示不过滤）
     * @return 预警库存列表（按 {@code available_count} 升序）
     */
    List<InventoryAlertVO> alerts(Long pharmacyId);

    /**
     * 手动释放锁定库存。前置校验订单确实锁定该库存后释放。
     *
     * @param id      库存 ID
     * @param request 释放请求（含购药订单 ID 与原因）
     * @throws com.sphp.shared.exception.BusinessException 当库存/订单不存在、订单与库存药房不一致或订单未锁定该药品时抛出
     */
    void unlock(Long id, InventoryUnlockRequest request);
}
