package com.sphp.patient.registration.service.impl;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.registration.mapper.DepartmentRecord;
import com.sphp.patient.registration.mapper.HospitalRecord;
import com.sphp.patient.registration.mapper.RegistrationResourceMapper;
import com.sphp.patient.registration.service.RegistrationService;
import com.sphp.patient.registration.vo.DepartmentListVO;
import com.sphp.patient.registration.vo.HospitalListVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
     * 查询指定可用医院下的启用科室。
     *
     * @param hospitalId 医院 ID
     * @param keyword 可选科室名称关键字
     * @return 可选科室列表
     * @throws CAuthException 医院不存在或已停用时抛出
     */
    @Override
    public List<DepartmentListVO> listDepartments(Long hospitalId, String keyword) {
        // 先确认医院可用，避免向客户端暴露停用医院下的科室数据。
        if (resourceMapper.selectAvailableHospital(hospitalId) == null) {
            throw new CAuthException(ErrorCodeEnum.INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "医院不存在或已停用");
        }
        // 空白关键字不参与筛选，保证与未传关键字的查询语义一致。
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return resourceMapper.selectAvailableDepartments(hospitalId, normalizedKeyword).stream()
                .map(this::toDepartmentListVO)
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

    /**
     * 转换科室查询记录，限制返回字段为 C 端选择挂号资源所需信息。
     *
     * @param record 科室查询记录
     * @return 可选科室响应对象
     */
    private DepartmentListVO toDepartmentListVO(DepartmentRecord record) {
        return DepartmentListVO.builder()
                .id(record.id())
                .name(record.name())
                .description(record.description())
                .build();
    }
}
