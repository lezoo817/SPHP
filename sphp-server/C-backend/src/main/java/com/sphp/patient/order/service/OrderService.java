package com.sphp.patient.order.service;

import com.sphp.patient.order.dto.DrugOrderCreateRequest;
import com.sphp.patient.order.vo.DrugOrderCancelVO;
import com.sphp.patient.order.vo.DrugOrderCreateVO;
import com.sphp.patient.order.vo.DrugOrderDetailVO;
import com.sphp.patient.order.vo.DrugOrderPageVO;
import com.sphp.patient.order.vo.DrugOrderReceiptVO;
import com.sphp.patient.order.vo.PharmacyInventoryVO;
import com.sphp.patient.registration.dto.RegisteringPaymentSimulateRequest;
import com.sphp.patient.registration.vo.RegisteringPaymentSuccessVO;
import java.util.List;

/** C端药房库存与购药订单服务。 */
public interface OrderService {
    /** 查询已批准处方可购买的院内药房库存。 */
    List<PharmacyInventoryVO> listPharmacyInventory(Long patientId, Long prescriptionId);
    /** 创建待支付购药订单。 */
    DrugOrderCreateVO createDrugOrder(DrugOrderCreateRequest request);
    /** 分页查询购药订单。 */
    DrugOrderPageVO listDrugOrders(Long patientId, String status, String logisticsStatus, Integer pageNo, Integer pageSize);
    /** 查询购药订单详情。 */
    DrugOrderDetailVO getDrugOrderDetail(Long drugOrderId);
    /** 取消待支付购药订单。 */
    DrugOrderCancelVO cancelDrugOrder(Long drugOrderId);
    /** 确认购药订单收货。 */
    DrugOrderReceiptVO confirmDrugOrderReceipt(Long drugOrderId);
    /** 模拟支付购药订单。 */
    RegisteringPaymentSuccessVO simulateDrugOrderPayment(Long paymentId, RegisteringPaymentSimulateRequest request);
    /** 处理购药订单超时。 */
    void expireDrugOrder(Long drugOrderId);
}
