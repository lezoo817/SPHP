package com.sphp.patient.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * C端购药订单跨表数据访问接口。
 */
@Mapper
public interface OrderDataMapper {

    /** 查询当前账号的本人就诊人 ID。 */
    Long selectOrderSelfPatientId(@Param("userId") Long userId);

    /** 判断就诊人是否有效。 */
    boolean existsOrderActivePatient(@Param("patientId") Long patientId);

    /** 判断当前账号是否拥有有效就诊人关系。 */
    boolean hasOrderActivePatientRelation(@Param("userId") Long userId, @Param("patientId") Long patientId);

    /** 查询处方资源及其所属医院。 */
    OrderPrescriptionRecord selectOrderPrescription(@Param("prescriptionId") Long prescriptionId);

    /** 查询处方药品及购买数量。 */
    List<OrderPrescriptionItemRecord> selectOrderPrescriptionItems(@Param("prescriptionId") Long prescriptionId);

    /** 查询可满足处方的院内药房库存。 */
    List<OrderPharmacyStockRecord> selectOrderPharmacyInventory(@Param("prescriptionId") Long prescriptionId,
                                                                 @Param("hospitalId") Long hospitalId);

    /** 查询选定药房对处方各药品的库存记录。 */
    List<OrderStockRecord> selectOrderStocks(@Param("pharmacyId") Long pharmacyId,
                                             @Param("prescriptionId") Long prescriptionId);

    /** 条件预扣单个药品库存。 */
    int lockOrderStock(@Param("pharmacyId") Long pharmacyId, @Param("drugId") Long drugId,
                       @Param("quantity") Integer quantity, @Param("now") OffsetDateTime now);

    /** 归还单个已锁定药品库存。 */
    int releaseOrderStock(@Param("pharmacyId") Long pharmacyId, @Param("drugId") Long drugId,
                          @Param("quantity") Integer quantity, @Param("now") OffsetDateTime now);

    /** 支付成功后将单个药品锁定库存转为最终消耗。 */
    int consumeOrderLockedStock(@Param("pharmacyId") Long pharmacyId, @Param("drugId") Long drugId,
                                @Param("quantity") Integer quantity, @Param("now") OffsetDateTime now);

    /**
     * 按患者、状态和订单名称分页查询购药订单。
     *
     * @param patientId 已完成归属校验的就诊人 ID
     * @param status 订单状态筛选条件
     * @param logisticsStatus 物流状态筛选条件
     * @param keyword 订单名称关键词
     * @param limit 每页条数
     * @param offset 偏移量
     * @return 订单列表投影
     */
    List<OrderListRecord> selectOrderList(@Param("patientId") Long patientId, @Param("status") String status,
                                          @Param("logisticsStatus") String logisticsStatus, @Param("keyword") String keyword, @Param("limit") int limit,
                                          @Param("offset") long offset);
    /**
     * 按与列表相同条件统计购药订单数量。
     *
     * @param patientId 已完成归属校验的就诊人 ID
     * @param status 订单状态筛选条件
     * @param logisticsStatus 物流状态筛选条件
     * @param keyword 订单名称关键词
     * @return 匹配的订单数量
     */
    long countOrderList(@Param("patientId") Long patientId, @Param("status") String status,
                        @Param("logisticsStatus") String logisticsStatus, @Param("keyword") String keyword);
    /** 查询购药订单详情。 */
    OrderDetailRecord selectOrderDetail(@Param("drugOrderId") Long drugOrderId);

    /** 查询订单明细。 */
    List<OrderItemRecord> selectOrderItems(@Param("drugOrderId") Long drugOrderId);

    /** 查询订单物流轨迹。 */
    List<OrderTraceRecord> selectOrderTraces(@Param("drugOrderId") Long drugOrderId);

    /** 条件取消待支付购药订单。 */
    int cancelPendingDrugOrder(@Param("drugOrderId") Long drugOrderId, @Param("now") OffsetDateTime now);

    /** 条件标记待支付购药订单超时。 */
    int expirePendingDrugOrder(@Param("drugOrderId") Long drugOrderId, @Param("now") OffsetDateTime now);

    /** 条件关闭购药订单支付单。 */
    int closePendingDrugOrderPayment(@Param("drugOrderId") Long drugOrderId, @Param("now") OffsetDateTime now);

    /** 条件确认收货。 */
    int confirmOrderReceipt(@Param("drugOrderId") Long drugOrderId, @Param("now") OffsetDateTime now);

    /** 查询支付单关联业务类型。 */
    PaymentBusinessRecord selectPaymentBusiness(@Param("paymentId") Long paymentId);

    /** 查询购药支付单及订单归属。 */
    DrugOrderPaymentRecord selectDrugOrderPayment(@Param("paymentId") Long paymentId);

    /** 条件标记购药支付单成功。 */
    int markDrugOrderPaymentSuccess(@Param("paymentId") Long paymentId, @Param("now") OffsetDateTime now);

    /** 条件标记购药订单已支付。 */
    int markDrugOrderPaid(@Param("drugOrderId") Long drugOrderId, @Param("now") OffsetDateTime now);

    /** 根据已支付购药订单创建用药计划。 */
    int createMedicationPlans(@Param("drugOrderId") Long drugOrderId, @Param("now") OffsetDateTime now);

    /** 查询支付超时订单及其用户。 */
    DrugOrderTimeoutRecord selectDrugOrderTimeout(@Param("drugOrderId") Long drugOrderId);

}
