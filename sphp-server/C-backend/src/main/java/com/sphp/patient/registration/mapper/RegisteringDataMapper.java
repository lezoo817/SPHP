package com.sphp.patient.registration.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;

/**
 * C端挂号订单跨表数据访问接口。
 */
@Mapper
public interface RegisteringDataMapper {

    /**
     * 查询当前账号的本人就诊人。
     *
     * @param userId C端用户 ID
     * @return 本人就诊人 ID，不存在时返回 null
     */
    Long selectRegisteringSelfPatientId(@Param("userId") Long userId);

    /**
     * 判断就诊人是否存在且未停用。
     *
     * @param patientId 就诊人 ID
     * @return 存在时返回 true
     */
    boolean existsRegisteringActivePatient(@Param("patientId") Long patientId);

    /**
     * 判断当前账号是否绑定有效就诊人。
     *
     * @param userId C端用户 ID
     * @param patientId 就诊人 ID
     * @return 存在有效绑定时返回 true
     */
    boolean hasActivePatientRelation(@Param("userId") Long userId, @Param("patientId") Long patientId);

    /**
     * 查询挂号锁号需要的时段、排班、医生和医院链路。
     *
     * @param hospitalId 医院 ID
     * @param slotId 时段 ID
     * @return 时段锁号记录，不可用时返回 null
     */
    RegisteringSlotLockRecord selectRegisteringSlotLockInfo(@Param("hospitalId") Long hospitalId,
                                                            @Param("slotId") Long slotId);

    /**
     * 统计时段当前可用号源快照数。
     *
     * @param slotId 时段 ID
     * @return 可用快照数
     */
    long countRegisteringAvailableSnapshots(@Param("slotId") Long slotId);

    /**
     * 条件锁定一个可用号源快照。
     *
     * @param slotId 时段 ID
     * @param patientId 就诊人 ID
     * @param lockedAt 锁定时间
     * @return 成功锁定的快照 ID，无可用快照时返回 null
     */
    Long registeringLockOneSnapshot(@Param("slotId") Long slotId, @Param("patientId") Long patientId,
                                    @Param("lockedAt") OffsetDateTime lockedAt);
}
