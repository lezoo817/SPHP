package com.sphp.patient.health.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * 健康档案的患者归属与资料查询接口。
 */
@Mapper
public interface HealthPatientMapper {

    /**
     * 查询当前 C端账号有效的本人就诊人 ID。
     *
     * @param userId C端用户 ID
     * @return 本人就诊人 ID，不存在时返回 null
     */
    Long selectSelfPatientId(@Param("userId") Long userId);

    /**
     * 判断就诊人是否存在且未被软删除。
     *
     * @param patientId 就诊人 ID
     * @return 存在时返回 true
     */
    boolean existsActivePatient(@Param("patientId") Long patientId);

    /**
     * 判断当前账号是否拥有有效的就诊人关系。
     *
     * @param userId C端用户 ID
     * @param patientId 就诊人 ID
     * @return 有效关系存在时返回 true
     */
    boolean hasActivePatientRelation(@Param("userId") Long userId, @Param("patientId") Long patientId);

    /**
     * 查询健康档案展示所需的最小患者资料。
     *
     * @param patientId 就诊人 ID
     * @return 最小患者资料，不存在时返回 null
     */
    HealthPatientProfileRecord selectActiveProfile(@Param("patientId") Long patientId);
}
