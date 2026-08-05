package com.sphp.admin.pharmacy.service;

import com.sphp.admin.pharmacy.entity.Pharmacy;

import java.util.List;

/** 药房管理服务。 */
public interface PharmacyService {
    /** 获取当前医院所有启用药房。 */
    List<Pharmacy> listEnabled();
}