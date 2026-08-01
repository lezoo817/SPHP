package com.sphp.patient.family.service;

import com.sphp.patient.family.vo.FamilyMemberListVO;

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
}
