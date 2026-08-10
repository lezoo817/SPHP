package com.sphp.admin.pharmacy.service.impl;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.common.enums.BUserStatusEnum;
import com.sphp.admin.pharmacy.entity.Pharmacy;
import com.sphp.admin.pharmacy.mapper.PharmacyMapper;
import com.sphp.admin.pharmacy.service.PharmacyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 药房管理服务实现（管理员视角）。
 *
 * <p>按当前登录管理员所属医院（{@code hospital_id}）做数据隔离过滤；
 * 药房状态字段与 {@link BUserStatusEnum} 复用（仅 ENABLED / DISABLED 两态）。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
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
                        .eq(Pharmacy::getStatus, BUserStatusEnum.ENABLED.getCode())
                        .isNull(Pharmacy::getDeletedAt)
                        .orderByAsc(Pharmacy::getId));
    }
}