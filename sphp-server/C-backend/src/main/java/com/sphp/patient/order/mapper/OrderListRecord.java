package com.sphp.patient.order.mapper;
import java.time.OffsetDateTime;

/**
 * 订单列表记录
 * @param id 订单ID
 * @param prescriptionId 处方ID
 * @param orderName 订单名称
 * @param pharmacyName 药店名称
 * @param status 订单状态
 * @param logisticsStatus 物流状态
 * @param latestLogisticsNode 最新物流节点
 * @param amountCent 订单金额（分）
 * @param expireAt 过期时间
 * @param patientName 患者名称
 */
public record OrderListRecord(Long id, Long prescriptionId, String orderName, String pharmacyName, String status, String logisticsStatus,
                              String latestLogisticsNode, Integer amountCent, OffsetDateTime expireAt, String patientName) { }
