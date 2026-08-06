package com.sphp.patient.registration.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

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
     * 锁定当前有效 C 端用户，串行化同一账号的挂号和支付限约判断。
     *
     * @param userId C 端用户 ID
     * @return 锁定成功的用户 ID，用户不存在或已删除时返回 null
     */
    Long registeringLockActiveUser(@Param("userId") Long userId);

    /**
     * 判断当前登录账号是否在冷却期内成功预约过指定医生。
     *
     * @param userId C 端用户 ID
     * @param doctorId 医生 ID
     * @param cutoffAt 支付成功冷却期的开始时间
     * @return 存在冷却期内已支付或已完成挂号时返回 true
     */
    boolean existsRegisteringDoctorAppointmentWithinCooldown(@Param("userId") Long userId,
                                                              @Param("doctorId") Long doctorId,
                                                              @Param("cutoffAt") OffsetDateTime cutoffAt);

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

    /** 分页查询就诊人的挂号订单。 */
    List<RegisteringAppointmentRecord> selectRegisteringAppointments(@Param("patientId") Long patientId, @Param("status") String status, @Param("limit") int limit, @Param("offset") long offset);
    /** 统计就诊人的挂号订单数。 */
    long countRegisteringAppointments(@Param("patientId") Long patientId, @Param("status") String status);
    /** 查询挂号订单详情记录。 */
    RegisteringAppointmentRecord selectRegisteringAppointment(@Param("appointmentId") Long appointmentId);
    /** 查询挂号支付单及所属订单。 */
    RegisteringPaymentRecord selectRegisteringPayment(@Param("paymentId") Long paymentId);
    /** 条件取消未支付挂号订单。 */
    int registeringCancelUnpaidAppointment(@Param("appointmentId") Long appointmentId, @Param("now") OffsetDateTime now);
    /** 条件取消尚未开始的已支付挂号订单。 */
    int registeringCancelPaidAppointment(@Param("appointmentId") Long appointmentId, @Param("now") OffsetDateTime now);
    /** 条件关闭待支付挂号支付单。 */
    int registeringClosePendingPayment(@Param("appointmentId") Long appointmentId, @Param("now") OffsetDateTime now);
    /** 条件释放已锁定号源快照。 */
    int registeringReleaseLockedSnapshot(@Param("snapshotId") Long snapshotId, @Param("now") OffsetDateTime now);
    /** 条件释放已支付订单关联的已售号源快照。 */
    int registeringReleaseSoldSnapshot(@Param("snapshotId") Long snapshotId, @Param("now") OffsetDateTime now);
    /** 为候补登记锁定有效已发布时段。 */
    RegisteringSlotLockRecord lockRegisteringWaitlistSlot(@Param("slotId") Long slotId);
    /** 判断当前就诊人是否已有活跃候补。 */
    boolean existsRegisteringActiveWaitlist(@Param("patientId") Long patientId, @Param("slotId") Long slotId);
    /** 查询时段的下一个候补排队号。 */
    int selectRegisteringNextQueueNo(@Param("slotId") Long slotId);
    /**
     * 锁定尚未开始的候补晋级时段，串行化同一时段的候补状态变更。
     *
     * @param slotId 时段 ID
     * @param now 当前时间
     * @return 锁定成功的时段 ID，时段不存在或已开始时返回 null
     */
    Long registeringLockWaitlistPromotionSlot(@Param("slotId") Long slotId, @Param("now") OffsetDateTime now);
    /** 统计可重新预约的号源快照数量。 */
    long countRegisteringRebookableSnapshots(@Param("slotId") Long slotId);
    /** 统计当前已通知但未过期的候补数量。 */
    long countRegisteringNotifiedWaitlists(@Param("slotId") Long slotId);
    /** 锁定当前时段排队最靠前的待通知候补。 */
    RegisteringWaitlistCandidateRecord registeringLockNextWaitingWaitlist(@Param("slotId") Long slotId);
    /** 条件将候补状态从排队中更新为已通知。 */
    int registeringNotifyWaitlist(@Param("waitlistId") Long waitlistId, @Param("now") OffsetDateTime now);
    /** 查询已超过通知期限的候补记录。 */
    List<RegisteringWaitlistCandidateRecord> selectRegisteringExpiredNotifiedWaitlists(
            @Param("deadline") OffsetDateTime deadline);
    /** 条件将已通知候补更新为过期。 */
    int registeringExpireNotifiedWaitlist(@Param("waitlistId") Long waitlistId,
                                           @Param("deadline") OffsetDateTime deadline,
                                           @Param("now") OffsetDateTime now);
    /** 将已开始时段的活跃候补统一更新为过期。 */
    int registeringExpireStartedWaitlists(@Param("now") OffsetDateTime now);
    /** 当前登记账号成功锁号后将其已通知候补标记为已履约。 */
    int registeringFulfillNotifiedWaitlist(@Param("userId") Long userId,
                                            @Param("patientId") Long patientId,
                                            @Param("slotId") Long slotId,
                                            @Param("now") OffsetDateTime now);
    /** 条件完成挂号支付。 */
    int registeringMarkPaymentSuccess(@Param("paymentId") Long paymentId, @Param("now") OffsetDateTime now);
    /** 条件确认挂号订单已支付。 */
    int registeringMarkAppointmentPaid(@Param("appointmentId") Long appointmentId, @Param("now") OffsetDateTime now);
    /** 条件确认号源快照已售出。 */
    int registeringMarkSnapshotSold(@Param("snapshotId") Long snapshotId, @Param("now") OffsetDateTime now);
}
