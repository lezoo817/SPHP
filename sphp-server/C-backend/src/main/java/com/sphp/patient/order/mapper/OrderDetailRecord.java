package com.sphp.patient.order.mapper;
import java.time.OffsetDateTime;

/**
 * OrderDetailRecord
 * @param id 订单ID
 * @param prescriptionId 处方ID
 * @param patientId 患者ID
 * @param pharmacyId 药店ID
 * @param pharmacyName 药店名称
 * @param status 订单状态
 * @param deliveryMethod 配送方式
 * @param deliveryAddress 配送地址
 * @param logisticsCompany 物流公司
 * @param trackingNo 跟踪编号
 * @param logisticsStatus 物流状态
 * @param amountCent 订单金额（分）
 * @param expireAt 过期时间
 * @param paymentId 支付ID
 * @param paymentStatus 支付状态
 * @param patientName 患者名称
 * @param patientPhone 患者电话
 * @param expectedDeliveryAt 预计送达时间
 */
public record OrderDetailRecord(Long id, Long prescriptionId, Long patientId, Long pharmacyId, String pharmacyName, String status,
                                String deliveryMethod, String deliveryAddress, String logisticsCompany,
                                String trackingNo, String logisticsStatus, Integer amountCent, OffsetDateTime expireAt,
                                Long paymentId, String paymentStatus, String patientName, String patientPhone,
                                OffsetDateTime expectedDeliveryAt) { }
