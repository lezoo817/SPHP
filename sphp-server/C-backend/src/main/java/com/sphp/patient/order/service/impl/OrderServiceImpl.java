package com.sphp.patient.order.service.impl;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.common.constant.OrderConstant;
import com.sphp.patient.common.enums.DrugOrderLogisticsStatusEnum;
import com.sphp.patient.common.enums.DrugOrderStatusEnum;
import com.sphp.patient.common.enums.RegisteringPaymentStatusEnum;
import com.sphp.patient.common.enums.NotificationTypeEnum;
import com.sphp.patient.notification.mq.producer.NotificationEventProducer;
import com.sphp.patient.order.dto.DrugOrderCreateRequest;
import com.sphp.patient.order.entity.DrugOrder;
import com.sphp.patient.order.entity.DrugOrderItem;
import com.sphp.patient.order.entity.DrugOrderPayment;
import com.sphp.patient.order.event.DrugOrderPendingEvent;
import com.sphp.patient.order.mapper.DrugOrderItemMapper;
import com.sphp.patient.order.mapper.DrugOrderMapper;
import com.sphp.patient.order.mapper.DrugOrderPaymentMapper;
import com.sphp.patient.order.mapper.DrugOrderPaymentRecord;
import com.sphp.patient.order.mapper.DrugOrderTimeoutRecord;
import com.sphp.patient.order.mapper.OrderDataMapper;
import com.sphp.patient.order.mapper.OrderDetailRecord;
import com.sphp.patient.order.mapper.OrderItemRecord;
import com.sphp.patient.order.mapper.OrderListRecord;
import com.sphp.patient.order.mapper.OrderPharmacyStockRecord;
import com.sphp.patient.order.mapper.OrderPrescriptionItemRecord;
import com.sphp.patient.order.mapper.OrderPrescriptionRecord;
import com.sphp.patient.order.mapper.OrderStockRecord;
import com.sphp.patient.order.mapper.OrderTraceRecord;
import com.sphp.patient.order.service.OrderService;
import com.sphp.patient.order.service.DeliveryService;
import com.sphp.patient.order.support.OrderStockLockService;
import com.sphp.patient.order.vo.DrugOrderCancelVO;
import com.sphp.patient.order.vo.DrugOrderCreateVO;
import com.sphp.patient.order.vo.DrugOrderDetailVO;
import com.sphp.patient.order.vo.DrugOrderPageVO;
import com.sphp.patient.order.vo.DrugOrderReceiptVO;
import com.sphp.patient.order.vo.PharmacyInventoryVO;
import com.sphp.patient.registration.config.RegistrationProperties;
import com.sphp.patient.registration.dto.RegisteringPaymentSimulateRequest;
import com.sphp.patient.registration.vo.RegisteringPaymentSuccessVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.mindrot.jbcrypt.BCrypt;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** C端药房库存与购药订单服务实现。 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    private final OrderDataMapper orderDataMapper;
    private final DrugOrderMapper drugOrderMapper;
    private final DrugOrderItemMapper drugOrderItemMapper;
    private final DrugOrderPaymentMapper drugOrderPaymentMapper;
    private final OrderStockLockService stockLockService;
    private final RegistrationProperties registrationProperties;
    private final ApplicationEventPublisher eventPublisher;
    private final NotificationEventProducer notificationEventProducer;
    private final DeliveryService deliveryService;

    /** 查询当前账号可访问处方的院内药房库存。 */
    @Override
    public List<PharmacyInventoryVO> listPharmacyInventory(Long patientId, Long prescriptionId) {
        OrderPrescriptionRecord prescription = requireAccessibleApprovedPrescription(patientId, prescriptionId);
        return toPharmacyInventory(orderDataMapper.selectOrderPharmacyInventory(prescriptionId, prescription.hospitalId()));
    }

    /** 创建待支付购药订单并锁定数据库库存。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DrugOrderCreateVO createDrugOrder(DrugOrderCreateRequest request) {
        OrderPrescriptionRecord prescription = requireAccessibleApprovedPrescription(request.getPatientId(), request.getPrescriptionId());
        // 下单只接受地址簿快照或旧版文本，避免客户端伪造已保存地址的归属。
        String deliveryAddress = deliveryService.deliveryResolveOrderAddress(request.getAddressId(), request.getDeliveryAddress());
        List<OrderStockRecord> stocks = orderDataMapper.selectOrderStocks(request.getPharmacyId(), request.getPrescriptionId());
        List<OrderPrescriptionItemRecord> prescriptionItems = orderDataMapper.selectOrderPrescriptionItems(request.getPrescriptionId());
        if (stocks.size() != prescriptionItems.size() || stocks.isEmpty()) {
            throw outOfStock("药房库存不足或不支持该处方药品");
        }
        boolean pharmacyEligible = orderDataMapper.selectOrderPharmacyInventory(request.getPrescriptionId(), prescription.hospitalId())
                .stream().anyMatch(item -> request.getPharmacyId().equals(item.pharmacyId()));
        if (!pharmacyEligible) {
            throw notFound("药房不存在、已停用或库存不足");
        }
        return stockLockService.executeWithStockLocks(stocks, () -> createLockedDrugOrder(request, prescription, stocks, deliveryAddress));
    }

    /** 分页查询当前账号指定就诊人的购药订单。 */
    @Override
    public DrugOrderPageVO listDrugOrders(Long patientId, String status, String logisticsStatus, String keyword, Integer pageNo, Integer pageSize) {
        Long targetPatientId = resolveAccessiblePatient(CUserContext.getRequired().userId(), patientId);
        validateEnum(status, DrugOrderStatusEnum.values(), "订单状态不在允许范围内");
        validateEnum(logisticsStatus, DrugOrderLogisticsStatusEnum.values(), "物流状态不在允许范围内");
        int resolvedPageNo = pageNo == null ? OrderConstant.DEFAULT_PAGE_NO : pageNo;
        int resolvedPageSize = pageSize == null ? OrderConstant.DEFAULT_PAGE_SIZE : pageSize;
        if (resolvedPageSize > OrderConstant.MAX_PAGE_SIZE) throw parameterOutOfRange("pageSize 不能超过100");
        // 空白关键词不参与查询，避免无意义地影响列表与总数。
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<DrugOrderPageVO.Item> records = orderDataMapper.selectOrderList(targetPatientId, status, logisticsStatus, normalizedKeyword,
                resolvedPageSize, (long) (resolvedPageNo - 1) * resolvedPageSize).stream().map(this::toOrderListItem).toList();
        return DrugOrderPageVO.builder().pageNo(resolvedPageNo).pageSize(resolvedPageSize)
                .total(orderDataMapper.countOrderList(targetPatientId, status, logisticsStatus, normalizedKeyword)).records(records).build();
    }

    /** 查询当前账号可访问的购药订单详情。 */
    @Override
    public DrugOrderDetailVO getDrugOrderDetail(Long drugOrderId) {
        OrderDetailRecord detail = requireOwnedOrder(drugOrderId);
        return toOrderDetail(detail);
    }

    /** 条件取消待支付购药订单并释放锁定库存。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DrugOrderCancelVO cancelDrugOrder(Long drugOrderId) {
        OrderDetailRecord detail = requireOwnedOrder(drugOrderId);
        OffsetDateTime now = OffsetDateTime.now();
        if (orderDataMapper.cancelPendingDrugOrder(drugOrderId, now) != 1) throw statusConflict("当前购药订单不可取消");
        orderDataMapper.closePendingDrugOrderPayment(drugOrderId, now);
        releaseOrderStocks(detail, now);
        return DrugOrderCancelVO.builder().drugOrderId(drugOrderId).status(DrugOrderStatusEnum.CANCELLED.name()).cancelledAt(now).build();
    }

    /** 条件确认已送达购药订单收货。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DrugOrderReceiptVO confirmDrugOrderReceipt(Long drugOrderId) {
        requireOwnedOrder(drugOrderId);
        OffsetDateTime now = OffsetDateTime.now();
        if (orderDataMapper.confirmOrderReceipt(drugOrderId, now) != 1) throw statusConflict("当前物流状态不可确认收货");
        return DrugOrderReceiptVO.builder().drugOrderId(drugOrderId)
                .logisticsStatus(DrugOrderLogisticsStatusEnum.RECEIVED.name()).receivedAt(now).build();
    }

    /** 校验密码后完成购药支付和库存最终消耗。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisteringPaymentSuccessVO simulateDrugOrderPayment(Long paymentId, RegisteringPaymentSimulateRequest request) {
        DrugOrderPaymentRecord payment = orderDataMapper.selectDrugOrderPayment(paymentId);
        if (payment == null) throw notFound("支付单不存在");
        Long userId = CUserContext.getRequired().userId();
        if (!userId.equals(payment.payerUserId())) throw forbidden("无权访问该支付单");
        resolveAccessiblePatient(userId, payment.patientId());
        OffsetDateTime now = OffsetDateTime.now();
        if (!RegisteringPaymentStatusEnum.PENDING.name().equals(payment.paymentStatus())
                || !DrugOrderStatusEnum.PENDING_PAYMENT.name().equals(payment.orderStatus())) throw statusConflict("支付单状态不允许付款");
        if (!payment.expireAt().isAfter(now)) {
            throw new CAuthException(ErrorCodeEnum.PAYMENT_TIMEOUT, HttpStatus.CONFLICT, "支付单已过期");
        }
        if (!BCrypt.checkpw(request.getLoginPassword(), payment.passwordHash())) {
            throw new CAuthException(ErrorCodeEnum.PASSWORD_VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "支付密码校验失败");
        }
        if (orderDataMapper.markDrugOrderPaymentSuccess(paymentId, now) != 1
                || orderDataMapper.markDrugOrderPaid(payment.drugOrderId(), now) != 1) throw statusConflict("支付单状态已变化");
        OrderDetailRecord detail = requireOwnedOrder(payment.drugOrderId());
        for (OrderItemRecord item : orderDataMapper.selectOrderItems(payment.drugOrderId())) {
            if (orderDataMapper.consumeOrderLockedStock(detail.pharmacyId(), item.drugId(), item.quantity(), now) != 1) {
                throw systemError("锁定药品库存状态异常");
            }
        }
        // 支付状态条件更新成功后才生成用药计划，重复支付不会重复创建。
        orderDataMapper.createMedicationPlans(payment.drugOrderId(), now);
        notificationEventProducer.publishNotification("DRUG_ORDER_PAYMENT_SUCCESS", payment.drugOrderId(),
                payment.payerUserId(), payment.patientId(), NotificationTypeEnum.DRUG_ORDER,
                "购药支付成功", "您的购药订单已支付成功，药房将尽快处理。");
        return RegisteringPaymentSuccessVO.builder().paymentId(paymentId).status(RegisteringPaymentStatusEnum.SUCCESS.name()).paidAt(now).build();
    }

    /** 超时关闭待支付订单并释放库存，供 RabbitMQ 消费者调用。 */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void expireDrugOrder(Long drugOrderId) {
        DrugOrderTimeoutRecord timeout = orderDataMapper.selectDrugOrderTimeout(drugOrderId);
        if (timeout == null) return;
        OffsetDateTime now = OffsetDateTime.now();
        if (orderDataMapper.expirePendingDrugOrder(drugOrderId, now) == 1) {
            orderDataMapper.closePendingDrugOrderPayment(drugOrderId, now);
            OrderDetailRecord detail = orderDataMapper.selectOrderDetail(drugOrderId);
            if (detail != null) releaseOrderStocks(detail, now);
            // 仅在待支付订单确实超时后创建通知，重复超时消息不会重复通知。
            notificationEventProducer.publishNotification("DRUG_ORDER_TIMEOUT", timeout.drugOrderId(), timeout.payerUserId(),
                    timeout.patientId(), NotificationTypeEnum.DRUG_ORDER, "购药订单已超时",
                    "订单未在规定时间内支付，已自动取消。");
        }
    }

    /** 在库存锁内条件扣减并创建订单、明细和支付单。 */
    private DrugOrderCreateVO createLockedDrugOrder(DrugOrderCreateRequest request, OrderPrescriptionRecord prescription,
                                                     List<OrderStockRecord> stocks, String deliveryAddress) {
        OffsetDateTime now = OffsetDateTime.now();
        for (OrderStockRecord stock : stocks) {
            if (orderDataMapper.lockOrderStock(stock.pharmacyId(), stock.drugId(), stock.quantity(), now) != 1) {
                throw outOfStock("药品库存不足");
            }
        }
        int amountCent = stocks.stream().mapToInt(stock -> stock.unitPriceCent() * stock.quantity()).sum();
        OffsetDateTime expireAt = now.plusSeconds(Math.max(registrationProperties.getPaymentTimeout(), 1));
        DrugOrder order = new DrugOrder();
        order.setPatientId(prescription.patientId()); order.setPrescriptionId(prescription.id()); order.setPharmacyId(request.getPharmacyId());
        order.setPharmacyNameSnapshot(stocks.getFirst().pharmacyName()); order.setDeliveryMethod("COURIER"); order.setDeliveryAddress(deliveryAddress);
        order.setStatus(DrugOrderStatusEnum.PENDING_PAYMENT.name()); order.setLogisticsStatus(DrugOrderLogisticsStatusEnum.PENDING_SHIPMENT.name());
        order.setAmountCent(amountCent); order.setExpireAt(expireAt);
        if (drugOrderMapper.insert(order) != 1) throw systemError("购药订单创建失败");
        for (OrderStockRecord stock : stocks) {
            DrugOrderItem item = new DrugOrderItem(); item.setDrugOrderId(order.getId()); item.setDrugId(stock.drugId());
            item.setDrugNameSnapshot(stock.drugName()); item.setQuantity(stock.quantity()); item.setUnitPriceCent(stock.unitPriceCent());
            if (drugOrderItemMapper.insert(item) != 1) throw systemError("购药订单明细创建失败");
        }
        DrugOrderPayment payment = new DrugOrderPayment(); payment.setDrugOrderId(order.getId()); payment.setPayerUserId(CUserContext.getRequired().userId());
        payment.setAmountCent(amountCent); payment.setStatus(RegisteringPaymentStatusEnum.PENDING.name()); payment.setExpireAt(expireAt);
        if (drugOrderPaymentMapper.insert(payment) != 1) throw systemError("购药支付单创建失败");
        eventPublisher.publishEvent(DrugOrderPendingEvent.of(order.getId(), order.getPatientId(), payment.getPayerUserId()));
        notificationEventProducer.publishNotification("DRUG_ORDER_PENDING", order.getId(), payment.getPayerUserId(),
                order.getPatientId(), NotificationTypeEnum.DRUG_ORDER, "购药订单待支付", "请在规定时间内完成支付。");
        return DrugOrderCreateVO.builder().drugOrderId(order.getId()).status(order.getStatus()).deliveryMethod(order.getDeliveryMethod())
                .amountCent(amountCent).expireAt(expireAt).paymentId(payment.getId()).items(stocks.stream().map(stock -> DrugOrderCreateVO.Item.builder()
                        .drugId(stock.drugId()).drugName(stock.drugName()).quantity(stock.quantity()).build()).toList()).build();
    }

    /** 按订单明细归还已锁定库存。 */
    private void releaseOrderStocks(OrderDetailRecord detail, OffsetDateTime now) {
        for (OrderItemRecord item : orderDataMapper.selectOrderItems(detail.id())) {
            if (orderDataMapper.releaseOrderStock(detail.pharmacyId(), item.drugId(), item.quantity(), now) != 1) throw systemError("锁定药品库存释放失败");
        }
    }

    /** 校验当前账号可访问的已批准处方。 */
    private OrderPrescriptionRecord requireAccessibleApprovedPrescription(Long requestedPatientId, Long prescriptionId) {
        OrderPrescriptionRecord prescription = orderDataMapper.selectOrderPrescription(prescriptionId);
        if (prescription == null) throw notFound("处方不存在");
        Long targetPatientId = resolveAccessiblePatient(CUserContext.getRequired().userId(), requestedPatientId);
        if (!targetPatientId.equals(prescription.patientId())) throw forbidden("无权访问该处方");
        if (!"APPROVED".equals(prescription.status())) throw notFound("处方不存在");
        return prescription;
    }

    /** 从订单资源反查患者并校验归属。 */
    private OrderDetailRecord requireOwnedOrder(Long drugOrderId) {
        OrderDetailRecord detail = orderDataMapper.selectOrderDetail(drugOrderId);
        if (detail == null) throw notFound("购药订单不存在");
        resolveAccessiblePatient(CUserContext.getRequired().userId(), detail.patientId());
        return detail;
    }

    /** 解析当前账号可访问的就诊人。 */
    private Long resolveAccessiblePatient(Long userId, Long requestedPatientId) {
        Long patientId = requestedPatientId == null ? orderDataMapper.selectOrderSelfPatientId(userId) : requestedPatientId;
        if (patientId == null || !orderDataMapper.existsOrderActivePatient(patientId)) throw notFound("就诊人不存在或已停用");
        if (!orderDataMapper.hasOrderActivePatientRelation(userId, patientId)) throw forbidden("无权访问该就诊人");
        return patientId;
    }

    /** 将药房库存行按药房聚合为响应。 */
    private List<PharmacyInventoryVO> toPharmacyInventory(List<OrderPharmacyStockRecord> rows) {
        Map<Long, List<OrderPharmacyStockRecord>> grouped = new LinkedHashMap<>();
        rows.forEach(row -> grouped.computeIfAbsent(row.pharmacyId(), ignored -> new java.util.ArrayList<>()).add(row));
        return grouped.values().stream().map(group -> { OrderPharmacyStockRecord first = group.getFirst(); return PharmacyInventoryVO.builder()
                .pharmacyId(first.pharmacyId()).name(first.pharmacyName()).hospitalId(first.hospitalId()).isDefault(first.isDefault()).deliveryMethod("COURIER")
                .items(group.stream().map(row -> PharmacyInventoryVO.Item.builder().drugId(row.drugId()).availableCount(row.availableCount()).unitPriceCent(row.unitPriceCent()).build()).toList()).build(); }).toList();
    }

    /** 转换购药订单列表项。 */
    private DrugOrderPageVO.Item toOrderListItem(OrderListRecord record) { return DrugOrderPageVO.Item.builder().id(record.id()).orderName(record.orderName()).pharmacyName(record.pharmacyName()).status(record.status()).logisticsStatus(record.logisticsStatus()).latestLogisticsNode(record.latestLogisticsNode()).amountCent(record.amountCent()).expireAt(record.expireAt()).build(); }
    /** 转换购药订单详情。 */
    private DrugOrderDetailVO toOrderDetail(OrderDetailRecord record) { return DrugOrderDetailVO.builder().id(record.id()).status(record.status())
            .pharmacy(DrugOrderDetailVO.Pharmacy.builder().id(record.pharmacyId()).name(record.pharmacyName()).build())
            .delivery(DrugOrderDetailVO.Delivery.builder().method(record.deliveryMethod()).address(record.deliveryAddress()).company(record.logisticsCompany()).trackingNo(record.trackingNo()).logisticsStatus(record.logisticsStatus()).traces(orderDataMapper.selectOrderTraces(record.id()).stream().map(trace -> DrugOrderDetailVO.Trace.builder().node(trace.node()).occurredAt(trace.occurredAt()).build()).toList()).build())
            .items(orderDataMapper.selectOrderItems(record.id()).stream().map(item -> DrugOrderDetailVO.Item.builder().drugId(item.drugId()).drugName(item.drugName()).quantity(item.quantity()).unitPriceCent(item.unitPriceCent()).build()).toList())
            .amountCent(record.amountCent()).payment(DrugOrderDetailVO.Payment.builder().id(record.paymentId()).status(record.paymentStatus()).build()).build(); }
    /** 校验可选枚举筛选值。 */
    private <T extends Enum<T>> void validateEnum(String value, T[] values, String message) { if (value != null && !value.isBlank() && Arrays.stream(values).noneMatch(item -> item.name().equals(value))) throw parameterOutOfRange(message); }
    /** 创建不存在异常。 */
    private CAuthException notFound(String message) { return new CAuthException(ErrorCodeEnum.INVALID_USER_INPUT, HttpStatus.NOT_FOUND, message); }
    /** 创建越权异常。 */
    private CAuthException forbidden(String message) { return new CAuthException(ErrorCodeEnum.UNAUTHORIZED, HttpStatus.FORBIDDEN, message); }
    /** 创建状态冲突异常。 */
    private CAuthException statusConflict(String message) { return new CAuthException(ErrorCodeEnum.ORDER_CLOSED_OR_STATUS_INVALID, HttpStatus.CONFLICT, message); }
    /** 创建库存不足异常。 */
    private CAuthException outOfStock(String message) { return new CAuthException(ErrorCodeEnum.OUT_OF_STOCK, HttpStatus.CONFLICT, message); }
    /** 创建参数范围异常。 */
    private CAuthException parameterOutOfRange(String message) { return new CAuthException(ErrorCodeEnum.PARAMETER_OUT_OF_RANGE, HttpStatus.BAD_REQUEST, message); }
    /** 创建系统异常。 */
    private CAuthException systemError(String message) { return new CAuthException(ErrorCodeEnum.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message); }
}
