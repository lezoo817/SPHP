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
import com.sphp.patient.order.mapper.DrugOrderReminderOrderRecord;
import com.sphp.patient.order.mapper.DrugOrderTimeoutRecord;
import com.sphp.patient.order.mapper.MedicationReminderPlanRecord;
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
import com.sphp.patient.order.support.DeliveryOrderSnapshot;
import com.sphp.patient.order.support.OrderStockLockService;
import com.sphp.patient.order.vo.DrugOrderCancelVO;
import com.sphp.patient.order.vo.DrugOrderCreateVO;
import com.sphp.patient.order.vo.DrugOrderDetailVO;
import com.sphp.patient.order.vo.DrugOrderPageVO;
import com.sphp.patient.order.vo.DrugOrderReceiptVO;
import com.sphp.patient.order.vo.DrugOrderReminderActivationVO;
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
import java.time.LocalTime;
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
import static com.sphp.patient.health.support.ProposalMedicationReminderSupport.proposalCalculateNextReminderAt;
import static com.sphp.patient.health.support.ProposalMedicationReminderSupport.proposalResolveReminderTimes;
import static com.sphp.patient.health.support.ProposalMedicationReminderSupport.proposalSerializeReminderTimes;
import static com.sphp.shared.common.enums.ErrorCodeEnum.*;

/** C端药房库存与购药订单服务实现。 */
@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {
    // 药房库存、处方与订单的联表查询接口
    private final OrderDataMapper orderDataMapper;
    // 购药订单主表写入接口
    private final DrugOrderMapper drugOrderMapper;
    // 购药订单明细写入接口
    private final DrugOrderItemMapper drugOrderItemMapper;
    // 购药支付单写入接口
    private final DrugOrderPaymentMapper drugOrderPaymentMapper;
    // Redis 药品库存预扣协调服务
    private final OrderStockLockService stockLockService;
    // 订单支付超时时间配置
    private final RegistrationProperties registrationProperties;
    // 事务后订单事件发布器
    private final ApplicationEventPublisher eventPublisher;
    // 事务后站内通知事件生产器
    private final NotificationEventProducer notificationEventProducer;
    // 地址快照与模拟配送时效服务
    private final DeliveryService deliveryService;

    /**
     * 列出药房库存。
     * @param patientId 患者ID
     * @param prescriptionId 处方ID
     * @return 药房库存
     */
    @Override
    public List<PharmacyInventoryVO> listPharmacyInventory(Long patientId, Long prescriptionId) {
        // 处方归属和 APPROVED 状态是库存展示的前置条件，避免越权读取药品价格与库存。
        OrderPrescriptionRecord prescription = requireAccessibleApprovedPrescription(patientId, prescriptionId);
        // 仅查询处方所属医院内已启用且能满足全部处方药数量的院内药房。
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
        // 解析地址簿归属并冻结配送地址与模拟时效，避免客户端伪造地址或后续配置变更影响订单。
        DeliveryOrderSnapshot deliverySnapshot = deliveryService.deliveryResolveOrderSnapshot(request.getAddressId(),
                request.getDeliveryAddress(), prescription.hospitalId(), request.getPharmacyId());
        // 分别读取药房库存与处方明细，后续以数量一致性校验药房是否能完整配药。
        List<OrderStockRecord> stocks = orderDataMapper.selectOrderStocks(request.getPharmacyId(), request.getPrescriptionId());
        List<OrderPrescriptionItemRecord> prescriptionItems = orderDataMapper.selectOrderPrescriptionItems(request.getPrescriptionId());
        if (stocks.size() != prescriptionItems.size() || stocks.isEmpty()) {
            throw outOfStock("药房库存不足或不支持该处方药品");
        }
        // 复用库存展示口径验证药房启用状态、医院归属和全部药品的可售数量。
        boolean pharmacyEligible = orderDataMapper.selectOrderPharmacyInventory(request.getPrescriptionId(), prescription.hospitalId())
                .stream()
                .anyMatch(item -> request.getPharmacyId().equals(item.pharmacyId()));
        if (!pharmacyEligible) {
            throw notFound("药房不存在、已停用或库存不足");
        }
        // Redis 预扣与数据库行锁组合执行，业务失败时由库存锁服务负责补偿预扣。
        return stockLockService.executeWithStockLocks(
                stocks,
                () -> createLockedDrugOrder(request, prescription, stocks, deliverySnapshot)
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
        // 将可选就诊人解析为当前账号可访问的患者，后续查询只使用该受信任 ID。
        Long targetPatientId = resolveAccessiblePatient(CUserContext.getRequired().userId(), patientId);
        // 订单与物流状态均为白名单枚举，避免未经约束的筛选条件进入 SQL。
        validateEnum(status, DrugOrderStatusEnum.values(), "订单状态不在允许范围内");
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
        // 订单详情必须沿订单反查就诊人归属，不能信任客户端携带的患者信息。
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
        // 先校验订单归属并保留药房信息，供后续按原药房归还锁定库存。
        OrderDetailRecord detail = requireOwnedOrder(drugOrderId);
        OffsetDateTime now = OffsetDateTime.now();
        if (orderDataMapper.cancelPendingDrugOrder(drugOrderId, now) != 1) throw statusConflict("当前购药订单不可取消");
        // 只有订单条件取消成功后才关闭待支付单，避免误关闭已成功或已结束支付单。
        orderDataMapper.closePendingDrugOrderPayment(drugOrderId, now);
        // 订单取消后逐项释放数据库 LOCKED 库存，Redis 预扣已由超时或取消流程协调恢复。
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
        // 锁定订单并校验归属，避免确认收货与提醒授权发生并发竞态。
        requireOwnedReminderOrderForUpdate(drugOrderId);
        OffsetDateTime now = OffsetDateTime.now();
        if (orderDataMapper.confirmOrderReceipt(drugOrderId, now) != 1) throw statusConflict("当前物流状态不可确认收货");
        // 订单实际收货后才消费用户预先授权，绝不在待收货状态提前启动提醒。
        activateAuthorizedMedicationReminders(drugOrderId, now);
        return DrugOrderReceiptVO.builder()
                .drugOrderId(drugOrderId)
                .logisticsStatus(RECEIVED.name()) // 物流状态
                .receivedAt(now) // 收货时间
                .build();
    }

    /**
     * 登记购药订单收货后自动开启用药提醒的授权。
     *
     * @param drugOrderId 购药订单 ID
     * @return 授权状态；订单已收货时同步完成启用
     * @throws CAuthException 订单无权、未支付或处方频次不支持提醒时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DrugOrderReminderActivationVO authorizeDrugOrderReminderAfterReceipt(Long drugOrderId) {
        // 对订单行加锁，避免授权和确认收货交错后遗漏自动启用。
        DrugOrderReminderOrderRecord order = requireOwnedReminderOrderForUpdate(drugOrderId);
        if (!"PAID".equals(order.orderStatus())) {
            throw statusConflict("订单尚未支付成功，暂无法设置自动用药提醒");
        }
        List<MedicationReminderPlanRecord> plans = orderDataMapper.selectOrderMedicationReminderPlans(drugOrderId);
        if (plans.isEmpty()) {
            throw statusConflict("订单用药计划尚未生成，暂无法设置自动用药提醒");
        }
        // 授权前校验全部计划频次，避免确认收货时因提醒配置异常影响订单状态。
        for (MedicationReminderPlanRecord plan : plans) {
            proposalResolveReminderTimes(plan.frequency());
        }
        OffsetDateTime now = OffsetDateTime.now();
        Long userId = CUserContext.getRequired().userId();
        // 唯一订单约束与幂等键共同保证重复确认不会重复登记授权。
        orderDataMapper.upsertDrugOrderReminderActivation(drugOrderId, userId, order.patientId(), now);
        if (RECEIVED.name().equals(order.logisticsStatus())) {
            // 用户在已收货后才授权时立即生效，仍只处理该订单绑定的计划。
            activateAuthorizedMedicationReminders(drugOrderId, now);
            return DrugOrderReminderActivationVO.builder()
                    .drugOrderId(drugOrderId)
                    .status("ACTIVATED")
                    .authorizedAt(now)
                    .activatedAt(now)
                    .build();
        }
        return DrugOrderReminderActivationVO.builder()
                .drugOrderId(drugOrderId)
                .status("PENDING_RECEIPT") // 订单已收货时同步完成启用
                .authorizedAt(now)
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
        // 支付单查询同时取得付款账号、订单状态、库存快照和密码哈希，避免支付过程二次拼接数据。
        DrugOrderPaymentRecord payment = orderDataMapper.selectDrugOrderPayment(paymentId);

        if (payment == null) throw notFound("支付单不存在");

        Long userId = CUserContext.getRequired().userId();

        if (!userId.equals(payment.payerUserId())) throw forbidden("无权访问该支付单");

        // 支付账号通过后仍需确认订单患者属于当前账号，防止失效的家庭成员关系继续付款。
        resolveAccessiblePatient(userId, payment.patientId());

        OffsetDateTime now = OffsetDateTime.now();
        // 仅允许待支付单驱动待付款订单进入成功状态。
        if (!PENDING.name().equals(payment.paymentStatus())
                || !PENDING_PAYMENT.name().equals(payment.orderStatus())) throw statusConflict("支付单状态不允许付款");

        // 支付入口自行兜底到期判断，不能依赖异步超时消息准时到达。
        if (!payment.expireAt().isAfter(now)) {
            throw new CAuthException(PAYMENT_TIMEOUT, HttpStatus.CONFLICT, "支付单已过期");
        }
        // BCrypt 只在内存校验当前登录密码，禁止记录或传递明文密码。
        if (!BCrypt.checkpw(request.getLoginPassword(), payment.passwordHash())) {
            throw new CAuthException(PASSWORD_VALIDATION_FAILED, HttpStatus.BAD_REQUEST, "支付密码校验失败");
        }
        // 支付单与订单均以条件更新流转，重复支付或超时竞争时只能有一个请求成功。
        if (orderDataMapper.markDrugOrderPaymentSuccess(paymentId, now) != 1
                || orderDataMapper.markDrugOrderPaid(payment.drugOrderId(), now) != 1) throw statusConflict("支付单状态已变化");
        // 重新按订单归属读取药房信息，用于将所有 LOCKED 库存转为最终已售库存。
        OrderDetailRecord detail = requireOwnedOrder(payment.drugOrderId());
        // 每个订单明细都必须完成一次条件扣减，任一失败均回滚支付事务。
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
        // 通知由事务后事件发布，避免数据库回滚后仍向用户发送支付成功消息。
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
        // 超时投递只携带订单 ID，先查询付款人与就诊人以便在条件取消成功后发送准确通知。
        DrugOrderTimeoutRecord timeout = orderDataMapper.selectDrugOrderTimeout(drugOrderId);

        if (timeout == null) return;

        OffsetDateTime now = OffsetDateTime.now();
        // 条件更新是超时消息的幂等边界，只有待付款订单会真正执行后续补偿。
        if (orderDataMapper.expirePendingDrugOrder(drugOrderId, now) == 1) {
            // 关闭仍处于待支付状态的支付单，防止超时订单后续被再次付款。
            orderDataMapper.closePendingDrugOrderPayment(drugOrderId, now);
            OrderDetailRecord detail = orderDataMapper.selectOrderDetail(drugOrderId);
            // 订单明细仍保持 LOCKED 时才归还库存；查询缺失时不伪造库存补偿。
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
     * @param deliverySnapshot 购药订单配送地址与时效快照
     * @return 购药订单创建结果
     */
    private DrugOrderCreateVO createLockedDrugOrder(DrugOrderCreateRequest request, OrderPrescriptionRecord prescription,
                                                     List<OrderStockRecord> stocks, DeliveryOrderSnapshot deliverySnapshot) {
        OffsetDateTime now = OffsetDateTime.now();
        // Redis 已预扣总余量后，再逐药品执行数据库条件锁定，PostgreSQL 作为库存最终事实来源。
        for (OrderStockRecord stock : stocks) {
            if (orderDataMapper.lockOrderStock(stock.pharmacyId(), stock.drugId(), stock.quantity(), now) != 1) {
                throw outOfStock("药品库存不足");
            }
        }
        int amountCent = stocks.stream()
                .mapToInt(stock -> stock.unitPriceCent() * stock.quantity())
                .sum();

        OffsetDateTime expireAt = now.plusSeconds(Math.max(registrationProperties.getPaymentTimeout(), 1));
        // 订单主表保存药房名称、收货地址和配送时效快照，后续基础数据变化不得影响历史订单。
        DrugOrder order = new DrugOrder();

        order.setPatientId(prescription.patientId());
        order.setPrescriptionId(prescription.id());
        order.setPharmacyId(request.getPharmacyId());
        order.setPharmacyNameSnapshot(stocks.getFirst().pharmacyName()); // 药房名称快照
        order.setDeliveryMethod("COURIER"); // 快递
        order.setDeliveryAddress(deliverySnapshot.deliveryAddress());
        // 下单时冻结模拟时效，避免地址簿或配置变更影响已支付订单的预计送达时间。
        order.setEstimatedDeliveryMinutes(deliverySnapshot.estimatedDeliveryMinutes());
        order.setStatus(PENDING_PAYMENT.name());
        order.setLogisticsStatus(PENDING_SHIPMENT.name()); // 待发货
        order.setAmountCent(amountCent);
        order.setExpireAt(expireAt);

        if (drugOrderMapper.insert(order) != 1) throw systemError("购药订单创建失败");

        // 将实际锁定的库存行复制为订单明细，单价和药品名称均使用下单时快照。
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
        payment.setAmountCent(amountCent);
        payment.setStatus(PENDING.name());
        payment.setExpireAt(expireAt);

        if (drugOrderPaymentMapper.insert(payment) != 1) throw systemError("购药支付单创建失败");

        // 发布领域事件，由事务提交后监听器投递支付超时消息，避免未提交订单提前超时。
        eventPublisher.publishEvent(
                DrugOrderPendingEvent.of(
                        order.getId(),
                        order.getPatientId(),
                        payment.getPayerUserId())
        );
        // 发布事务后站内通知，通知失败不应破坏订单与库存的一致性。
        notificationEventProducer.publishNotification(
                "DRUG_ORDER_PENDING",
                order.getId(),
                payment.getPayerUserId(),
                order.getPatientId(),
                DRUG_ORDER,
                "购药订单待支付",
                "请在规定时间内完成支付。"
        );
        return DrugOrderCreateVO.builder()
                .drugOrderId(order.getId())
                .status(order.getStatus())
                .deliveryMethod(order.getDeliveryMethod())
                .amountCent(amountCent)
                .expireAt(expireAt)
                .paymentId(payment.getId())
                .items(stocks.stream().map(stock -> DrugOrderCreateVO.Item.builder()
                        .drugId(stock.drugId())
                        .drugName(stock.drugName())
                        .quantity(stock.quantity())
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
        // 以订单明细数量回补原药房库存，条件更新失败表示库存锁状态已被其他流程处理。
        for (OrderItemRecord item : orderDataMapper.selectOrderItems(detail.id())) {
            if (orderDataMapper.releaseOrderStock(detail.pharmacyId(), item.drugId(), item.quantity(), now) != 1)
                throw systemError("锁定药品库存释放失败");
        }
    }

    /**
     * 获取可访问的处方。
     * @param requestedPatientId 请求选择的就诊人 ID
     * @param prescriptionId 处方 ID
     * @return 已校验归属且状态为 APPROVED 的处方
     */
    private OrderPrescriptionRecord requireAccessibleApprovedPrescription(Long requestedPatientId, Long prescriptionId) {

        OrderPrescriptionRecord prescription = orderDataMapper.selectOrderPrescription(prescriptionId);
        if (prescription == null) throw notFound("处方不存在");
        // 将请求患者解析为当前账号可访问患者，再与处方患者比对防止跨患者购买。
        Long targetPatientId = resolveAccessiblePatient(CUserContext.getRequired().userId(), requestedPatientId);
        if (!targetPatientId.equals(prescription.patientId())) throw forbidden("无权访问该处方");
        if (!"APPROVED".equals(prescription.status())) throw notFound("处方不存在");
        return prescription;
    }

    /**
     * 获取可访问的购药订单。
     * @param drugOrderId 购药订单 ID
     * @return 已校验当前账号患者归属的购药订单
     */
    private OrderDetailRecord requireOwnedOrder(Long drugOrderId) {
        OrderDetailRecord detail = orderDataMapper.selectOrderDetail(drugOrderId);
        if (detail == null) throw notFound("购药订单不存在");
        // 订单资源始终通过 patient_id 反查当前账号关系，避免只凭订单 ID 访问详情。
        resolveAccessiblePatient(CUserContext.getRequired().userId(), detail.patientId());
        return detail;
    }

    /**
     * 锁定并校验当前账号可访问的购药订单。
     *
     * @param drugOrderId 购药订单 ID
     * @return 锁定后的订单投影
     * @throws CAuthException 订单不存在或当前账号无权访问时抛出
     */
    private DrugOrderReminderOrderRecord requireOwnedReminderOrderForUpdate(Long drugOrderId) {
        DrugOrderReminderOrderRecord order = orderDataMapper.selectDrugOrderReminderOrderForUpdate(drugOrderId);
        if (order == null) {
            throw notFound("购药订单不存在");
        }
        // 权限始终由订单反查就诊人，不能信任 Agent 传入的任何患者标识。
        resolveAccessiblePatient(CUserContext.getRequired().userId(), order.patientId());
        return order;
    }

    /**
     * 启用订单已授权的用药提醒。
     *
     * @param drugOrderId 已确认收货的购药订单 ID
     * @param now 启用时间
     * @throws CAuthException 授权状态竞争或计划频次异常时抛出并回滚当前事务
     */
    private void activateAuthorizedMedicationReminders(Long drugOrderId, OffsetDateTime now) {
        if (!"PENDING_RECEIPT".equals(orderDataMapper.selectDrugOrderReminderActivationStatus(drugOrderId))) {
            return;
        }
        for (MedicationReminderPlanRecord plan : orderDataMapper.selectOrderMedicationReminderPlans(drugOrderId)) {
            // 解析计划频次为具体时间
            List<LocalTime> reminderTimes = proposalResolveReminderTimes(plan.frequency());
            // 条件更新尊重用户收货前已暂停、完成或手动开启的计划状态。
            orderDataMapper.enableOrderMedicationReminderPlan(
                    plan.planId(),
                    proposalCalculateNextReminderAt(reminderTimes, now),
                    proposalSerializeReminderTimes(reminderTimes),
                    now);
        }
        // 只有仍待收货的授权可以完成，避免重复收货重复开启。
        if (orderDataMapper.markDrugOrderReminderActivationActivated(drugOrderId, now) != 1) {
            throw statusConflict("自动用药提醒授权状态已变化");
        }
    }

    /**
     * 获取可访问的就诊人。
     * @param userId 当前登录用户 ID
     * @param requestedPatientId 请求选择的就诊人 ID
     * @return 已授权且有效的就诊人 ID
     */
    private Long resolveAccessiblePatient(Long userId, Long requestedPatientId) {
        // 未传患者时使用本人；传入患者必须同时有效且与当前账号保持有效关系。
        Long patientId = requestedPatientId == null ? orderDataMapper.selectOrderSelfPatientId(userId) : requestedPatientId;

        if (patientId == null || !orderDataMapper.existsOrderActivePatient(patientId))
            throw notFound("就诊人不存在或已停用");

        if (!orderDataMapper.hasOrderActivePatientRelation(userId, patientId))
            throw forbidden("无权访问该就诊人");

        return patientId;
    }

    /**
     * 转换药房库存。
     * @param rows 药房库存联表结果
     * @return 按药房聚合后的库存展示列表
     */
    private List<PharmacyInventoryVO> toPharmacyInventory(List<OrderPharmacyStockRecord> rows) {
        Map<Long, List<OrderPharmacyStockRecord>> grouped = new LinkedHashMap<>();
        // 同一药房的多条药品库存聚合为一个药房卡片，保持接口展示层次稳定。
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
                                            .availableCount(row.availableCount()) // 可用库存
                                            .unitPriceCent(row.unitPriceCent()) // 单价
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
                .prescriptionId(record.prescriptionId())
                .orderName(record.orderName())
                .pharmacyName(record.pharmacyName())
                .status(record.status())
                .logisticsStatus(record.logisticsStatus()) // 物流状态
                .latestLogisticsNode(record.latestLogisticsNode()) // 最新物流节点
                .amountCent(record.amountCent())
                .expireAt(record.expireAt()) // 订单过期时间
                .patientName(record.patientName())
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
                .prescriptionId(record.prescriptionId())
                .status(record.status())
                .patientName(record.patientName())
                .patientPhone(maskPhone(record.patientPhone()))
                .pharmacy(DrugOrderDetailVO.Pharmacy.builder()
                        .id(record.pharmacyId())
                        .name(record.pharmacyName())
                        .build())
                .delivery(DrugOrderDetailVO.Delivery.builder()
                        .method(record.deliveryMethod())
                        .address(record.deliveryAddress())
                        .company(record.logisticsCompany())
                        .trackingNo(record.trackingNo())
                        .logisticsStatus(record.logisticsStatus())
                        .expectedDeliveryAt(record.expectedDeliveryAt())
                        .traces(orderDataMapper.selectOrderTraces(record.id()).stream()
                                .map(trace -> DrugOrderDetailVO.Trace.builder()
                                        .node(trace.node())
                                        .occurredAt(trace.occurredAt())
                                        .build())
                                .toList())
                        .build())
                .items(orderDataMapper.selectOrderItems(record.id()).stream()
                        .map(item -> DrugOrderDetailVO.Item.builder()
                                .drugId(item.drugId())
                                .drugName(item.drugName())
                                .quantity(item.quantity())
                                .unitPriceCent(item.unitPriceCent())
                                .build())
                        .toList())
                .amountCent(record.amountCent()).payment(DrugOrderDetailVO.Payment.builder()
                        .id(record.paymentId())
                        .status(record.paymentStatus())
                        .build())
                .reminderActivationStatus(orderDataMapper.selectDrugOrderReminderActivationStatus(record.id()))
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
    /**
     * 校验可选枚举筛选值。
     *
     * @param value 客户端传入的筛选值
     * @param values 允许的枚举集合
     * @param message 参数不合法时的错误消息
     * @param <T> 枚举类型
     * @throws CAuthException 筛选值不属于允许枚举时抛出
     */
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
