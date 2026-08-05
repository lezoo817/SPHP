package com.sphp.patient.family.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDate;
import java.time.OffsetDateTime;


/**
 * C端本人资料跨表数据访问接口。
 */
@Mapper
public interface ProfileMapper {

    /**
     * 查询当前账号有效 SELF 关系关联的本人资料。
     *
     * @param userId 当前 C端用户 ID
     * @return 本人资料，不存在或已软删除时返回 null
     */
    ProfileRecord selectSelfProfile(@Param("userId") Long userId);

    /**
     * 锁定当前用户行，串行化本人资料与家庭成员的身份证号变更。
     *
     * @param userId 当前 C端用户 ID
     * @return 已锁定用户 ID，不存在或已删除时返回 null
     */
    Long lockUserForProfileMutation(@Param("userId") Long userId);

    /**
     * 检查当前账号下是否存在身份证号相同的有效其他就诊人。
     *
     * @param userId 当前 C端用户 ID
     * @param idCardNo 规范化后的身份证号
     * @param excludePatientId 排除的本人就诊人 ID
     * @return 存在相同身份证号时返回 true
     */
    boolean existsActiveIdCard(@Param("userId") Long userId, @Param("idCardNo") String idCardNo,
                               @Param("excludePatientId") Long excludePatientId);

    /**
     * 以当前用户 SELF 关系为边界条件更新本人资料。
     *
     * @param userId 当前 C端用户 ID
     * @param patientId 本人就诊人 ID
     * @param name 姓名
     * @param gender 性别编码
     * @param birthday 出生日期
     * @param phone 手机号存储字段
     * @param idCardNo 身份证号明文存储字段
     * @param emergencyContact 紧急联系人
     * @param updatedAt 更新时间
     * @return 实际更新行数
     */
    int updateSelfProfile(@Param("userId") Long userId, @Param("patientId") Long patientId,
                          @Param("name") String name, @Param("gender") String gender,
                          @Param("birthday") LocalDate birthday, @Param("phone") String phone,
                          @Param("idCardNo") String idCardNo,
                          @Param("emergencyContact") String emergencyContact,
                          @Param("updatedAt") OffsetDateTime updatedAt);

}
