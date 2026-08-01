package com.sphp.admin.hospital.service;

import com.sphp.admin.hospital.dto.HospitalUpdateRequest;
import com.sphp.admin.hospital.vo.HospitalVO;

/**
 * 医院信息服务。
 */
public interface HospitalService {

    /**
     * 查询当前管理员所属医院信息。
     */
    HospitalVO get();

    /**
     * 编辑医院信息（仅允许编辑本院）。
     *
     * @param id      医院 ID
     * @param request 编辑请求
     */
    void update(Long id, HospitalUpdateRequest request);
}
