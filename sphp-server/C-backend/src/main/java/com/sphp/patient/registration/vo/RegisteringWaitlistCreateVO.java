package com.sphp.patient.registration.vo;
import lombok.Builder;
import lombok.Getter;
/** 候补登记响应对象。 */
@Getter @Builder public class RegisteringWaitlistCreateVO { /** 候补 ID */ private final Long waitlistId; /** 时段 ID */ private final Long slotId; /** 候补状态 */ private final String status; /** 排队号 */ private final Integer queueNo; }
