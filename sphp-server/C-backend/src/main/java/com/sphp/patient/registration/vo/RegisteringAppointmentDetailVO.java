package com.sphp.patient.registration.vo;

import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;

/** 挂号订单详情响应对象。 */
@Getter
@Builder
public class RegisteringAppointmentDetailVO {

    /** 挂号订单 ID */
    private final Long id;

    /** 订单状态 */
    private final String status;

    /** 医生信息 */
    private final Doctor doctor;

    /** 号源时段信息 */
    private final Slot slot;

    /** 金额，单位分 */
    private final Integer amountCent;

    /** 待支付到期时间 */
    private final OffsetDateTime expireAt;

    /** 支付信息 */
    private final Payment payment;

    /** 医生展示信息。 */
    @Getter
    @Builder
    public static class Doctor {
        // 医生 ID
        private final Long id;
        // 医生名称
        private final String name;
        // 科室名称
        private final String departmentName;
    }

    /** 时段展示信息。 */
    @Getter
    @Builder public
    static class Slot {
        // 时段 ID
        private final Long id;
        // 时段开始时间
        private final OffsetDateTime startTime;
        // 时段结束时间
        private final OffsetDateTime endTime;
    }

    /** 支付展示信息。 */
    @Getter
    @Builder
    public static class Payment {
        // 支付单 ID
        private final Long id;
        // 支付单状态
        private final String status;

    }
}
