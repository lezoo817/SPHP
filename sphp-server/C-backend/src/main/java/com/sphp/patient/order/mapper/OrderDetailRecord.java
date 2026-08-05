package com.sphp.patient.order.mapper;
import java.time.OffsetDateTime;
/** 购药订单详情投影。 */
public record OrderDetailRecord(Long id, Long prescriptionId, Long patientId, Long pharmacyId, String pharmacyName, String status,
                                String deliveryMethod, String deliveryAddress, String logisticsCompany,
                                String trackingNo, String logisticsStatus, Integer amountCent, OffsetDateTime expireAt,
                                Long paymentId, String paymentStatus, String patientName, String patientPhone,
                                OffsetDateTime expectedDeliveryAt) { }
