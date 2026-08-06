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

    /** 资源不存在 / 越权访问（A0402：通用资源未找到） */
    private static final String ERR_RESOURCE_NOT_FOUND = "A0402";
    /** 无操作权限（A0443：通用权限不足） */
    private static final String ERR_FORBIDDEN = "A0443";

    private final HospitalMapper hospitalMapper;
    private final CurrentUserService currentUserService;

    @Override
    public HospitalVO get() {
        Long hospitalId = currentUserService.getCurrentHospitalId();
        return toVO(loadActiveHospital(hospitalId));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void update(Long id, HospitalUpdateRequest request) {
        Long currentHospitalId = currentUserService.getCurrentHospitalId();
        Hospital hospital = loadActiveHospital(id);
        // 数据隔离：仅允许编辑当前管理员所属医院
        if (!hospital.getId().equals(currentHospitalId)) {
            throw new BusinessException(ERR_FORBIDDEN, "无操作权限，仅可编辑本院信息");
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

    /**
     * 按 ID 加载有效医院（未软删），不存在返回 {@value #ERR_RESOURCE_NOT_FOUND}。
     *
     * @param id 医院 ID
     * @return 医院实体
     */
    private Hospital loadActiveHospital(Long id) {
        Hospital hospital = hospitalMapper.selectById(id);
        if (hospital == null || hospital.getDeletedAt() != null) {
            throw new BusinessException(ERR_RESOURCE_NOT_FOUND, "医院不存在");
        }
        return hospital;
    }

    /**
     * 实体转 VO。
     *
     * @param hospital 医院实体
     * @return 视图对象
     */
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
