package com.sphp.patient.registration.vo;

import lombok.Builder;
import lombok.Getter;
import java.time.OffsetDateTime;
import java.util.List;

/** 挂号订单分页响应对象。 */
@Getter @Builder
public class RegisteringAppointmentListVO {
    /** 当前页码 */ private final int pageNo;
    /** 页大小 */ private final int pageSize;
    /** 订单总数 */ private final long total;
    /** 订单记录 */ private final List<Item> records;
    /** 挂号订单列表项。 */
    @Getter @Builder public static class Item {
        /** 挂号订单 ID */ private final Long id;
        /** 医生姓名 */ private final String doctorName;
        /** 科室名称 */ private final String departmentName;
        /** 就诊开始时间 */ private final OffsetDateTime startTime;
        /** 订单状态 */ private final String status;
        /** 金额，单位分 */ private final Integer amountCent;
        /** 待支付到期时间 */ private final OffsetDateTime expireAt;
    }
}
