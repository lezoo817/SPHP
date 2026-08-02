package com.sphp.patient.registration.service.impl;

import com.sphp.patient.registration.mapper.HospitalRecord;
import com.sphp.patient.registration.mapper.RegistrationResourceMapper;
import com.sphp.patient.registration.service.RegistrationService;
import com.sphp.patient.registration.vo.HospitalListVO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * C端挂号资源查询服务实现。
 */
@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private final RegistrationResourceMapper resourceMapper;

    /**
     * 查询全部可供 C端选择的医院。
     *
     * @return 启用医院列表
     */
    @Override
    public List<HospitalListVO> listHospitals() {
        return resourceMapper.selectAvailableHospitals().stream()
                .map(this::toHospitalListVO)
                .toList();
    }

    /**
     * 转换医院查询记录，避免向 C端泄漏后台管理字段。
     *
     * @param record 医院查询记录
     * @return 可选医院响应对象
     */
    private HospitalListVO toHospitalListVO(HospitalRecord record) {
        return HospitalListVO.builder()
                .hospitalId(record.hospitalId())
                .name(record.name())
                .level(record.level())
                .address(record.address())
                .contact(record.contact())
                .build();
    }
}
