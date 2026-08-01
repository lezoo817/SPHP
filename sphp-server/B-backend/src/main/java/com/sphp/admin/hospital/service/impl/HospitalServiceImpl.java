package com.sphp.admin.hospital.service.impl;

import com.sphp.admin.common.CurrentUserService;
import com.sphp.admin.hospital.dto.HospitalUpdateRequest;
import com.sphp.admin.hospital.entity.Hospital;
import com.sphp.admin.hospital.mapper.HospitalMapper;
import com.sphp.admin.hospital.service.HospitalService;
import com.sphp.admin.hospital.vo.HospitalVO;
import com.sphp.shared.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

/**
 * 医院信息服务实现。
 */
@Service
@RequiredArgsConstructor
public class HospitalServiceImpl implements HospitalService {

    private final HospitalMapper hospitalMapper;
    private final CurrentUserService currentUserService;

    @Override
    public HospitalVO get() {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Hospital hospital = hospitalMapper.selectById(hospitalId);
        if (hospital == null || hospital.getDeletedAt() != null) {
            throw new BusinessException("A0402", "医院不存在");
        }
        return toVO(hospital);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, HospitalUpdateRequest request) {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        Hospital hospital = hospitalMapper.selectById(id);
        if (hospital == null || hospital.getDeletedAt() != null) {
            throw new BusinessException("A0402", "医院不存在");
        }
        // 数据隔离：仅允许编辑当前管理员所属医院
        if (!hospital.getId().equals(hospitalId)) {
            throw new BusinessException("A0443", "无操作权限，仅可编辑本院信息");
        }
        // 仅更新传入的非空值
        if (request.getName() != null) {
            hospital.setName(request.getName());
        }
        if (request.getLevel() != null) {
            hospital.setLevel(request.getLevel());
        }
        if (request.getDescription() != null) {
            hospital.setDescription(request.getDescription());
        }
        if (request.getAddress() != null) {
            hospital.setAddress(request.getAddress());
        }
        if (request.getContact() != null) {
            hospital.setContact(request.getContact());
        }
        hospital.setUpdatedAt(OffsetDateTime.now());
        hospitalMapper.updateById(hospital);
    }

    private HospitalVO toVO(Hospital hospital) {
        return HospitalVO.builder()
                .id(hospital.getId())
                .name(hospital.getName())
                .level(hospital.getLevel())
                .description(hospital.getDescription())
                .address(hospital.getAddress())
                .contact(hospital.getContact())
                .status(hospital.getStatus())
                .build();
    }
}
