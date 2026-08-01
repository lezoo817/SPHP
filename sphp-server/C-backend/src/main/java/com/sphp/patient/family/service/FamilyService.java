package com.sphp.patient.family.service;

import com.sphp.patient.family.vo.FamilyMemberListVO;
import com.sphp.patient.family.dto.FamilyMemberCreateRequest;
import com.sphp.patient.family.dto.FamilyMemberUpdateRequest;
import com.sphp.patient.family.vo.FamilyMemberCreateVO;
import com.sphp.patient.family.vo.FamilyMemberUpdateVO;
import com.sphp.patient.family.vo.FamilyMemberUnbindVO;

import java.util.List;

/**
 * C端家庭成员管理服务。
 */
public interface FamilyService {

    /**
     * 查询当前 C端账号的全部有效就诊人。
     *
     * @return 本人及家庭成员列表
     */
    List<FamilyMemberListVO> listFamilyMembers();

    /**
     * 新增当前账号下的非本人家庭成员。
     *
     * @param request 新增家庭成员请求
     * @return 新建家庭成员信息
     */
    FamilyMemberCreateVO createFamilyMember(FamilyMemberCreateRequest request);

    /**
     * 更新当前账号下的有效非本人家庭成员。
     *
     * @param patientId 就诊人 ID
     * @param request 更新家庭成员请求
     * @return 更新后的家庭成员信息
     */
    FamilyMemberUpdateVO updateFamilyMember(Long patientId, FamilyMemberUpdateRequest request);

    /**
     * 停用当前账号下的有效非本人家庭成员关系。
     *
     * @param patientId 就诊人 ID
     * @return 解绑结果
     */
    FamilyMemberUnbindVO unbindFamilyMember(Long patientId);
}
