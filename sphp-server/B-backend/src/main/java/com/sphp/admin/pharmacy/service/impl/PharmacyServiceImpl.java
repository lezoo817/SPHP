package com.sphp.admin.pharmacy.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.pharmacy.entity.Pharmacy;
import com.sphp.admin.pharmacy.mapper.PharmacyMapper;
import com.sphp.admin.pharmacy.service.PharmacyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/** 药房管理服务实现。 */
@Service
@RequiredArgsConstructor
public class PharmacyServiceImpl implements PharmacyService {

    private final PharmacyMapper pharmacyMapper;
    private final CurrentUserService currentUserService;

    @Override
    public List<Pharmacy> listEnabled() {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        return pharmacyMapper.selectList(
                Wrappers.<Pharmacy>lambdaQuery()
                        .eq(Pharmacy::getHospitalId, hospitalId)
                        .eq(Pharmacy::getStatus, "ENABLED")
                        .isNull(Pharmacy::getDeletedAt)
                        .orderByAsc(Pharmacy::getId));
    }
}