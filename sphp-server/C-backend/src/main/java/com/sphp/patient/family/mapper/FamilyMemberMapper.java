package com.sphp.patient.family.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 家庭成员跨表查询与并发控制数据访问接口。
 */
@Mapper
public interface FamilyMemberMapper {

    /**
     * 查询当前账号的全部有效就诊人。
     *
     * @param userId C端用户 ID
     * @return 本人及有效家庭成员列表
     */
    List<FamilyMemberRecord> selectActiveMembers(@Param("userId") Long userId);

    /**
     * 反查当前账号下的有效就诊人详情。
     *
     * @param userId C端用户 ID
     * @param patientId 就诊人 ID
     * @return 有效关系详情，不存在时返回 null
     */
    FamilyMemberRecord selectActiveMember(@Param("userId") Long userId, @Param("patientId") Long patientId);

    /**
     * 锁定当前用户行，串行化同一账号的家庭成员变更。
     *
     * @param userId C端用户 ID
     * @return 已锁定用户 ID，不存在或已删除时返回 null
     */
    Long lockUserForFamilyMutation(@Param("userId") Long userId);

    /**
     * 统计当前账号的有效非本人家庭成员数量。
     *
     * @param userId C端用户 ID
     * @return 有效非本人家庭成员数量
     */
    int countActiveNonSelfMembers(@Param("userId") Long userId);

    /**
     * 检查当前账号下是否存在身份证号相同的有效其他成员。
     *
     * @param userId C端用户 ID
     * @param idCardNo 身份证号
     * @param excludePatientId 更新时排除的就诊人 ID，可为 null
     * @return 存在时返回 true
     */
    boolean existsActiveIdCard(@Param("userId") Long userId, @Param("idCardNo") String idCardNo,
                               @Param("excludePatientId") Long excludePatientId);
}
