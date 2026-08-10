package com.sphp.admin.hospital.service;

import com.sphp.admin.hospital.dto.HospitalUpdateRequest;
import com.sphp.admin.hospital.vo.HospitalVO;

/**
 * 医院信息服务。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
public interface HospitalService {

    /**
     * 查询当前管理员所属医院信息。
     *
     * @return 医院信息
     * @throws com.sphp.shared.exception.BusinessException 医院不存在或已软删（A0402）
     */
    HospitalVO get();

    /**
     * 编辑医院信息（仅允许编辑本院，{@code id} 必须等于当前管理员所属医院）。
     *
     * @param id      医院 ID
     * @param request 编辑请求（仅更新非空字段）
     * @throws com.sphp.shared.exception.BusinessException 医院不存在（A0402）/ 越权编辑（A0443）
     */
    void update(Long id, HospitalUpdateRequest request);
}
