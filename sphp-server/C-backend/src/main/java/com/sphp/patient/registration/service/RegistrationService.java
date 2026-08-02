package com.sphp.patient.registration.service;

import com.sphp.patient.registration.vo.HospitalListVO;

import java.util.List;

/**
 * C端挂号资源查询服务。
 */
public interface RegistrationService {

    /**
     * 查询全部可供 C端选择的医院。
     *
     * @return 启用医院列表
     */
    List<HospitalListVO> listHospitals();
}
