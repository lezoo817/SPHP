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
import com.sphp.patient.order.mq.event.DrugOrderLogisticsAdvanceEvent;
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
import org.springframework.util.StringUtils;

import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static com.sphp.patient.common.constant.OrderConstant.*;
import static com.sphp.patient.common.enums.DrugOrderLogisticsStatusEnum.PENDING_SHIPMENT;
import static com.sphp.patient.common.enums.DrugOrderLogisticsStatusEnum.RECEIVED;
import static com.sphp.patient.common.enums.DrugOrderStatusEnum.CANCELLED;
import static com.sphp.patient.common.enums.DrugOrderStatusEnum.PENDING_PAYMENT;
import static com.sphp.patient.common.enums.NotificationTypeEnum.DRUG_ORDER;
import static com.sphp.patient.common.enums.RegisteringPaymentStatusEnum.PENDING;
import static com.sphp.patient.common.enums.RegisteringPaymentStatusEnum.SUCCESS;
import static com.sphp.shared.common.enums.ErrorCodeEnum.*;

/** C端药房库存与购药订单服务实现。 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    // 数据访问
    private final OrderDataMapper orderDataMapper;
    // 数据操作
    private final DrugOrderMapper drugOrderMapper;
    // 数据操作
    private final DrugOrderItemMapper drugOrderItemMapper;
    // 数据操作
    private final DrugOrderPaymentMapper drugOrderPaymentMapper;
    // 库存锁
    private final OrderStockLockService stockLockService;
    // 挂号配置
    private final RegistrationProperties registrationProperties;
    // 事件发布
    private final ApplicationEventPublisher eventPublisher;
    // 通知事件发布
    private final NotificationEventProducer notificationEventProducer;
    // 配送服务
    private final DeliveryService deliveryService;

    /**
     * 列出药房库存。
     * @param patientId 患者ID
     * @param prescriptionId 处方ID
     * @return 药房库存
     */
    @Override
    public List<PharmacyInventoryVO> listPharmacyInventory(Long patientId, Long prescriptionId) {
        OrderPrescriptionRecord prescription = requireAccessibleApprovedPrescription(patientId, prescriptionId);
        // 查询
        return toPharmacyInventory(
                orderDataMapper.selectOrderPharmacyInventory(prescriptionId, prescription.hospitalId())
        );
    }

    /**
     * 创建购药订单。
     * @param request 购药订单创建请求
     * @return 购药订单
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DrugOrderCreateVO createDrugOrder(DrugOrderCreateRequest request) {
        // 校验当前账号可访问的已批准处方
        OrderPrescriptionRecord prescription = requireAccessibleApprovedPrescription(request.getPatientId(), request.getPrescriptionId());
        // 下单只接受地址簿快照或旧版文本，避免客户端伪造已保存地址的归属。
        String deliveryAddress = deliveryService.deliveryResolveOrderAddress(request.getAddressId(), request.getDeliveryAddress());
        List<OrderStockRecord> stocks = orderDataMapper.selectOrderStocks(request.getPharmacyId(), request.getPrescriptionId());
        List<OrderPrescriptionItemRecord> prescriptionItems = orderDataMapper.selectOrderPrescriptionItems(request.getPrescriptionId());
        if (stocks.size() != prescriptionItems.size() || stocks.isEmpty()) {
            throw outOfStock("药房库存不足或不支持该处方药品");
        }
        // 检查药房
        boolean pharmacyEligible = orderDataMapper.selectOrderPharmacyInventory(request.getPrescriptionId(), prescription.hospitalId())
                .stream()
                .anyMatch(item -> request.getPharmacyId() // 只要流中任意一个元素满足给定条件就立即返回
                        .equals(item.pharmacyId()) // 只要找到一个匹配项就立刻返回 true
                );
        if (!pharmacyEligible) {
            throw notFound("药房不存在、已停用或库存不足");
        }
        return stockLockService.executeWithStockLocks(
                stocks,
                () -> createLockedDrugOrder(request, prescription, stocks, deliveryAddress) //创建订单
        );
    }

    /**
     * 列出购药订单。
     * @param patientId 就诊人 ID
     * @param status 订单状态
     * @param logisticsStatus 物流状态
     * @param keyword 订单名称模糊查询关键词
     * @param pageNo 页码
     * @param pageSize 每页条数
     * @return 购药订单列表
     */
    @Override
    public DrugOrderPageVO listDrugOrders(Long patientId, String status, String logisticsStatus, String keyword, Integer pageNo, Integer pageSize) {
        // 检查权限
        Long targetPatientId = resolveAccessiblePatient(CUserContext.getRequired().userId(), patientId);
        // 检查参数
        validateEnum(status, DrugOrderStatusEnum.values(), "订单状态不在允许范围内");
        // 检查物流状态
        validateEnum(logisticsStatus, DrugOrderLogisticsStatusEnum.values(), "物流状态不在允许范围内");
        int resolvedPageNo = pageNo == null ? DEFAULT_PAGE_NO : pageNo;
        int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (resolvedPageSize > MAX_PAGE_SIZE) throw parameterOutOfRange("pageSize 不能超过100");
        // 空白关键词不参与查询，避免无意义地影响列表与总数。
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        List<DrugOrderPageVO.Item> records = orderDataMapper.selectOrderList(targetPatientId, status, logisticsStatus, normalizedKeyword,
                resolvedPageSize, (long) (resolvedPageNo - 1) * resolvedPageSize).stream().map(this::toOrderListItem).toList();
        return DrugOrderPageVO.builder()
                .pageNo(resolvedPageNo)
                .pageSize(resolvedPageSize)
                .total(orderDataMapper.countOrderList(targetPatientId, status, logisticsStatus, normalizedKeyword))
                .records(records)
                .build();
    }

    /**
     * 获取购药订单详情。
     * @param drugOrderId 购药订单 ID
     * @return 购药订单详情
     */
    @Override
    public DrugOrderDetailVO getDrugOrderDetail(Long drugOrderId) {
        // 检查权限
        OrderDetailRecord detail = requireOwnedOrder(drugOrderId);
        return toOrderDetail(detail);
    }

    /**
     * 取消购药订单。
     * @param drugOrderId 购药订单 ID
     * @return 购药订单取消结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DrugOrderCancelVO cancelDrugOrder(Long drugOrderId) {
        // 检查权限
        OrderDetailRecord detail = requireOwnedOrder(drugOrderId);
        OffsetDateTime now = OffsetDateTime.now();
        if (orderDataMapper.cancelPendingDrugOrder(drugOrderId, now) != 1) throw statusConflict("当前购药订单不可取消");
        // 取消支付
        orderDataMapper.closePendingDrugOrderPayment(drugOrderId, now);
        // 释放库存
        releaseOrderStocks(detail, now);
        return DrugOrderCancelVO.builder()
                .drugOrderId(drugOrderId)
                .status(CANCELLED.name())
                .cancelledAt(now)
                .build();
    }

    /**
     * 确认购药订单收货。
     * @param drugOrderId 购药订单 ID
     * @return 购药订单收货结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DrugOrderReceiptVO confirmDrugOrderReceipt(Long drugOrderId) {
        // 检查权限
        requireOwnedOrder(drugOrderId);
        OffsetDateTime now = OffsetDateTime.now();
        if (orderDataMapper.confirmOrderReceipt(drugOrderId, now) != 1) throw statusConflict("当前物流状态不可确认收货");
        return DrugOrderReceiptVO.builder()
                .drugOrderId(drugOrderId)
                .logisticsStatus(RECEIVED.name()) // 物流状态
                .receivedAt(now) // 收货时间
                .build();
    }

    /**
     * 模拟购药订单付款。
     * @param paymentId 支付单 ID
     * @param request 模拟付款请求
     * @return 模拟付款结果
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public RegisteringPaymentSuccessVO simulateDrugOrderPayment(Long paymentId, RegisteringPaymentSimulateRequest request) {
        DrugOrderPaymentRecord payment = orderDataMapper.selectDrugOrderPayment(paymentId);

        if (payment == null) throw notFound("支付单不存在");

        Long userId = CUserContext.getRequired().userId();

        if (!userId.equals(payment.payerUserId())) throw forbidden("无权访问该支付单");

        // 检查权限
        resolveAccessiblePatient(userId, payment.patientId());

        OffsetDateTime now = OffsetDateTime.now();
        // 检查支付单状态
        if (!PENDING.name().equals(payment.paymentStatus())
                || !PENDING_PAYMENT.name().equals(payment.orderStatus())) throw statusConflict("支付单状态不允许付款");

        // 检查支付单是否已超时
        if (!payment.expireAt().isAfter(now)) {
            throw new CAuthException(PAYMENT_TIMEOUT, HttpStatus.CONFLICT, "支付单已过期");
        }
        // 检查支付密码
        if (!BCrypt.checkpw(request.getLoginPassword(), payment.passwordHash())) {
            throw new CAuthException(PASSWORD_VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "支付密码校验失败");
        }
        // 更新支付单状态
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
        // 支付成功后记录待发货节点，并在事务提交后安排首个物流推进消息。
        if (orderDataMapper.insertDrugOrderLogisticsTrace(payment.drugOrderId(),
                DRUG_ORDER_PAYMENT_SUCCESS_TRACE, now) != 1) {
            throw systemError("购药订单待发货轨迹写入失败");
        }
        eventPublisher.publishEvent(DrugOrderLogisticsAdvanceEvent.toInTransit(payment.drugOrderId()));
        // 发送通知
        notificationEventProducer.publishNotification(
                "DRUG_ORDER_PAYMENT_SUCCESS",  // 事件类型
                payment.drugOrderId(),
                payment.payerUserId(),
                payment.patientId(),
                DRUG_ORDER, // 通知类型
                "购药支付成功",
                "您的购药订单已支付成功，药房将尽快处理。"
        );
        return RegisteringPaymentSuccessVO.builder()
                .paymentId(paymentId)
                .status(SUCCESS.name())
                .paidAt(now)
                .build();
    }

    /**
     * 订单超时处理。
     * @param drugOrderId 购药订单 ID
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public void expireDrugOrder(Long drugOrderId) {

        DrugOrderTimeoutRecord timeout = orderDataMapper.selectDrugOrderTimeout(drugOrderId);

        if (timeout == null) return;

        OffsetDateTime now = OffsetDateTime.now();
        // 检查权限
        if (orderDataMapper.expirePendingDrugOrder(drugOrderId, now) == 1) {
            // 取消支付
            orderDataMapper.closePendingDrugOrderPayment(drugOrderId, now);
            OrderDetailRecord detail = orderDataMapper.selectOrderDetail(drugOrderId);
            // 释放库存
            if (detail != null) releaseOrderStocks(detail, now);
            // 仅在待支付订单确实超时后创建通知，重复超时消息不会重复通知。
            notificationEventProducer.publishNotification(
                    "DRUG_ORDER_TIMEOUT",  // 事件类型
                    timeout.drugOrderId(),
                    timeout.payerUserId(),
                    timeout.patientId(),
                    DRUG_ORDER,  // 通知类型
                    "购药订单已超时",
                    "订单未在规定时间内支付，已自动取消。");
        }
    }

    /**
     * 创建购药订单。
     * @param request 购药订单创建请求
     * @param prescription 处方
     * @param stocks 购药订单药品库存
     * @param deliveryAddress 购药订单配送地址
     * @return 购药订单创建结果
     */
    private DrugOrderCreateVO createLockedDrugOrder(DrugOrderCreateRequest request, OrderPrescriptionRecord prescription,
                                                     List<OrderStockRecord> stocks, String deliveryAddress) {
        OffsetDateTime now = OffsetDateTime.now();
        for (OrderStockRecord stock : stocks) {
            if (orderDataMapper.lockOrderStock(stock.pharmacyId(), stock.drugId(), stock.quantity(), now) != 1) {
                throw outOfStock("药品库存不足");
            }
        }
        int amountCent = stocks.stream()
                .mapToInt(stock -> stock.unitPriceCent() * stock.quantity())
                .sum();

        OffsetDateTime expireAt = now.plusSeconds(Math.max(registrationProperties.getPaymentTimeout(), 1));
        DrugOrder order = new DrugOrder();

        order.setPatientId(prescription.patientId());
        order.setPrescriptionId(prescription.id());
        order.setPharmacyId(request.getPharmacyId());
        order.setPharmacyNameSnapshot(stocks.getFirst().pharmacyName()); // 药房名称快照
        order.setDeliveryMethod("COURIER"); // 快递
        order.setDeliveryAddress(deliveryAddress);
        order.setStatus(PENDING_PAYMENT.name());
        order.setLogisticsStatus(PENDING_SHIPMENT.name()); // 待发货
        order.setAmountCent(amountCent);
        order.setExpireAt(expireAt);

        if (drugOrderMapper.insert(order) != 1) throw systemError("购药订单创建失败");

        for (OrderStockRecord stock : stocks) {
            DrugOrderItem item = new DrugOrderItem();
            item.setDrugOrderId(order.getId());
            item.setDrugId(stock.drugId());
            item.setDrugNameSnapshot(stock.drugName());
            item.setQuantity(stock.quantity());
            item.setUnitPriceCent(stock.unitPriceCent());
            if (drugOrderItemMapper.insert(item) != 1) throw systemError("购药订单明细创建失败");
        }
        DrugOrderPayment payment = new DrugOrderPayment();
        payment.setDrugOrderId(order.getId());
        payment.setPayerUserId(CUserContext.getRequired().userId());
        payment.setAmountCent(amountCent); payment.setStatus(PENDING.name());
        payment.setExpireAt(expireAt);

        if (drugOrderPaymentMapper.insert(payment) != 1) throw systemError("购药支付单创建失败");

        // 发送待支付事件
        eventPublisher.publishEvent(
                DrugOrderPendingEvent.of(
                        order.getId(),
                        order.getPatientId(),
                        payment.getPayerUserId())
        );
        // 发送通知
        notificationEventProducer.publishNotification(
                "DRUG_ORDER_PENDING",  //
                order.getId(),
                payment.getPayerUserId(),
                order.getPatientId(),
                DRUG_ORDER,  // 通知类型
                "购药订单待支付",
                "请在规定时间内完成支付。"
        );
        return DrugOrderCreateVO.builder()
                .drugOrderId(order.getId())
                .status(order.getStatus())
                .deliveryMethod(order.getDeliveryMethod())
                .amountCent(amountCent) // 金额
                .expireAt(expireAt) // 过期时间
                .paymentId(payment.getId())
                .items(stocks.stream().map(stock -> DrugOrderCreateVO.Item.builder()
                        .drugId(stock.drugId()).
                        drugName(stock.drugName()).
                        quantity(stock.quantity())
                        .build())
                        .toList())
                .build();
    }

    /**
     * 释放购药订单药品库存。
     * @param detail 购药订单
     * @param now 当前时间
     */
    private void releaseOrderStocks(OrderDetailRecord detail, OffsetDateTime now) {
        for (OrderItemRecord item : orderDataMapper.selectOrderItems(detail.id())) {
            if (orderDataMapper.releaseOrderStock(detail.pharmacyId(), item.drugId(), item.quantity(), now) != 1)
                throw systemError("锁定药品库存释放失败");
        }
    }

    /**
     * 获取可访问的处方。
     * @param requestedPatientId 访问者
     * @param prescriptionId 处方
     * @return 处方
     */
    private OrderPrescriptionRecord requireAccessibleApprovedPrescription(Long requestedPatientId, Long prescriptionId) {

        OrderPrescriptionRecord prescription = orderDataMapper.selectOrderPrescription(prescriptionId);
        if (prescription == null) throw notFound("处方不存在");
        // 检查权限
        Long targetPatientId = resolveAccessiblePatient(CUserContext.getRequired().userId(), requestedPatientId);
        if (!targetPatientId.equals(prescription.patientId())) throw forbidden("无权访问该处方");
        if (!"APPROVED".equals(prescription.status())) throw notFound("处方不存在");
        return prescription;
    }

    /**
     * 获取可访问的购药订单。
     * @param drugOrderId 购药订单
     * @return 购药订单
     */
    private OrderDetailRecord requireOwnedOrder(Long drugOrderId) {
        OrderDetailRecord detail = orderDataMapper.selectOrderDetail(drugOrderId);
        if (detail == null) throw notFound("购药订单不存在");
        // 检查权限
        resolveAccessiblePatient(CUserContext.getRequired().userId(), detail.patientId());
        return detail;
    }

    /**
     * 获取可访问的就诊人。
     * @param userId  用户
     * @param requestedPatientId 就诊人
     * @return 就诊人
     */
    private Long resolveAccessiblePatient(Long userId, Long requestedPatientId) {
        // 获取本人或显式就诊人并校验当前用户有效归属
        Long patientId = requestedPatientId == null ? orderDataMapper.selectOrderSelfPatientId(userId) : requestedPatientId;

        if (patientId == null || !orderDataMapper.existsOrderActivePatient(patientId))
            throw notFound("就诊人不存在或已停用");

        if (!orderDataMapper.hasOrderActivePatientRelation(userId, patientId))
            throw forbidden("无权访问该就诊人");

        return patientId;
    }

    /**
     * 转换药房库存。
     * @param rows // 药房库存
     * @return 列表
     */
    private List<PharmacyInventoryVO> toPharmacyInventory(List<OrderPharmacyStockRecord> rows) {
        Map<Long, List<OrderPharmacyStockRecord>> grouped = new LinkedHashMap<>();
        // 分组
        rows.forEach(row -> grouped.computeIfAbsent(row.pharmacyId(),
                ignored -> new java.util.ArrayList<>()).
                add(row));
        return grouped.values().stream()
                .map(group -> {
                    OrderPharmacyStockRecord first = group.getFirst();
                    return PharmacyInventoryVO.builder()
                           .pharmacyId(first.pharmacyId())
                            .name(first.pharmacyName())
                            .hospitalId(first.hospitalId())
                            .isDefault(first.isDefault())
                            .deliveryMethod("COURIER")
                             .items(group.stream()
                                     .map(row -> PharmacyInventoryVO.Item.builder()
                                             .drugId(row.drugId())
                                             .availableCount(row.availableCount())
                                             .unitPriceCent(row.unitPriceCent())
                                             .build())
                                     .toList())
                            .build();
                })
                .toList();
    }

    /**
     * 转换购药订单列表项。
     * @param record 购药订单
     * @return 列表项
     */
    private DrugOrderPageVO.Item toOrderListItem(OrderListRecord record) {
        return DrugOrderPageVO.Item.builder()
                .id(record.id())
                .prescriptionId(record.prescriptionId()) // 关联处方，用于购药页展示购买状态
                .orderName(record.orderName())
                .pharmacyName(record.pharmacyName())
                .status(record.status())
                .logisticsStatus(record.logisticsStatus()) // 物流状态
                .latestLogisticsNode(record.latestLogisticsNode()) // 物流状态
                .amountCent(record.amountCent())
                .expireAt(record.expireAt())
                .patientName(record.patientName()) // 列表展示当前处方就诊人
                .build();
    }

    /**
     * 转换购药订单详情。
     * @param record 购药订单
     * @return 详情
     */
    private DrugOrderDetailVO toOrderDetail(OrderDetailRecord record) {
        return DrugOrderDetailVO.builder()
                .id(record.id())
                .prescriptionId(record.prescriptionId()) // 保留订单与处方的准确关联
                .status(record.status())
                .patientName(record.patientName())
                .patientPhone(maskPhone(record.patientPhone())) // 详情仅返回脱敏手机号
                .pharmacy(DrugOrderDetailVO.Pharmacy.builder()
                        .id(record.pharmacyId())
                        .name(record.pharmacyName())
                        .build())
                .delivery(DrugOrderDetailVO.Delivery.builder()
                        .method(record.deliveryMethod())
                        .address(record.deliveryAddress())
                        .company(record.logisticsCompany()) // 物流公司
                        .trackingNo(record.trackingNo()) // 物流单号
                        .logisticsStatus(record.logisticsStatus())
                        .expectedDeliveryAt(record.expectedDeliveryAt()) // 后端模拟物流的预计送达时间
                        .traces(orderDataMapper.selectOrderTraces(record.id()).stream()
                                .map(trace -> DrugOrderDetailVO.Trace.builder()
                                        .node(trace.node()) // 物流节点
                                        .occurredAt(trace.occurredAt())
                                        .build())
                                .toList())
                        .build())
                .items(orderDataMapper.selectOrderItems(record.id()).stream()
                        .map(item -> DrugOrderDetailVO.Item.builder()
                                .drugId(item.drugId())
                                .drugName(item.drugName())
                                .quantity(item.quantity())
                                .unitPriceCent(item.unitPriceCent()) // 单价
                                .build())
                        .toList())
                .amountCent(record.amountCent()).payment(DrugOrderDetailVO.Payment.builder()
                        .id(record.paymentId())
                        .status(record.paymentStatus())
                        .build())
                .build();
    }

    /**
     * 按 C 端展示规则脱敏就诊人手机号。
     *
     * @param phone 手机号原始值
     * @return 脱敏手机号；未填写或格式异常时返回 null
     */
    private String maskPhone(String phone) {
        if (!StringUtils.hasText(phone) || phone.length() != 11) {
            return null;
        }
        return phone.substring(0, 3) + "****" + phone.substring(7);
    }
    /** 校验可选枚举筛选值。 */
    private <T extends Enum<T>> void validateEnum(String value, T[] values, String message) {
        if (value != null && !value.isBlank() && Arrays.stream(values)
                .noneMatch(item -> item.name().equals(value)))
            throw parameterOutOfRange(message);
    }

    /** 创建不存在异常。 */
    private CAuthException notFound(String message) {
        return new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, message);
    }

    /** 创建越权异常。 */
    private CAuthException forbidden(String message) {
        return new CAuthException(UNAUTHORIZED, HttpStatus.FORBIDDEN, message);
    }

    /** 创建状态冲突异常。 */
    private CAuthException statusConflict(String message) {
        return new CAuthException(ORDER_CLOSED_OR_STATUS_INVALID, HttpStatus.CONFLICT, message);
    }

    /** 创建库存不足异常。 */
    private CAuthException outOfStock(String message) {
        return new CAuthException(OUT_OF_STOCK,
                HttpStatus.CONFLICT, message);
    }

    /** 创建参数范围异常。 */
    private CAuthException parameterOutOfRange(String message) {
        return new CAuthException(PARAMETER_OUT_OF_RANGE, HttpStatus.BAD_REQUEST, message);
    }

    /** 创建系统异常。 */
    private CAuthException systemError(String message) {
        return new CAuthException(SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
