package com.sphp.admin.pharmacy.service;

import com.sphp.admin.pharmacy.entity.Pharmacy;

import java.util.List;

/**
 * 药房管理服务（管理员视角）。
 *
 * <p>按当前登录管理员所属医院（{@code hospital_id}）做数据隔离，
 * 仅返回本院启用状态的药房，供下拉筛选使用。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
public interface PharmacyService {
    /**
     * 获取当前医院所有启用药房（按 ID 升序）。
     *
     * @return 当前医院启用药房列表
     */
    List<Pharmacy> listEnabled();
}