package com.sphp.patient.family.service;

import com.sphp.patient.family.vo.FamilyMemberListVO;
import com.sphp.patient.family.dto.FamilyMemberCreateRequest;
import com.sphp.patient.family.vo.FamilyMemberCreateVO;

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
}
