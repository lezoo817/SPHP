package com.sphp.patient.registration.mapper;

/**
 * 候补队列晋级所需的最小记录投影。
 *
 * @param id 候补记录 ID
 * @param userId 登记候补的 C 端用户 ID
 * @param patientId 候补就诊人 ID
 * @param slotId 候补时段 ID
 * @param queueNo 候补排队号
 */
public record RegisteringWaitlistCandidateRecord(
        Long id,
        Long userId,
        Long patientId,
        Long slotId,
        Integer queueNo
) {
}
