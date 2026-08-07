package com.sphp.patient.order.service;

import com.sphp.patient.order.dto.DrugOrderCreateRequest;
import com.sphp.patient.order.vo.DrugOrderCancelVO;
import com.sphp.patient.order.vo.DrugOrderCreateVO;
import com.sphp.patient.order.vo.DrugOrderDetailVO;
import com.sphp.patient.order.vo.DrugOrderPageVO;
import com.sphp.patient.order.vo.DrugOrderReminderActivationVO;
import com.sphp.patient.order.vo.DrugOrderReceiptVO;
import com.sphp.patient.order.vo.PharmacyInventoryVO;
import com.sphp.patient.registration.dto.RegisteringPaymentSimulateRequest;
import com.sphp.patient.registration.vo.RegisteringPaymentSuccessVO;
import java.util.List;

/** C端药房库存与购药订单服务。 */
public interface OrderService {
    /**
     * 查询当前账号可访问就诊人的药房库存。
     * @param patientId 就诊人 ID
     * @param prescriptionId 处方 ID
     * @return 药房库存列表
     */
    List<PharmacyInventoryVO> listPharmacyInventory(Long patientId, Long prescriptionId);

    /**
     * 创建购药订单。
     * @param request 购药订单创建请求
     * @return 购药订单
     */
    DrugOrderCreateVO createDrugOrder(DrugOrderCreateRequest request);

    /**
     * 分页查询当前账号可访问就诊人的购药订单。
     *
     * @param patientId 就诊人 ID
     * @param status 订单状态
     * @param logisticsStatus 物流状态
     * @param keyword 订单名称模糊查询关键词
     * @param pageNo 页码
     * @param pageSize 每页条数
     * @return 订单分页结果
     */
    DrugOrderPageVO listDrugOrders(Long patientId, String status, String logisticsStatus, String keyword, Integer pageNo, Integer pageSize);

    /**
     * 获取购药订单详情。
     * @param drugOrderId 购药订单
     * @return 购药订单详情
     */
    DrugOrderDetailVO getDrugOrderDetail(Long drugOrderId);

    /**
     * 取消购药订单。
     * @param drugOrderId 购药订单
     * @return 取消结果
     */
    DrugOrderCancelVO cancelDrugOrder(Long drugOrderId);

    /**
     * 确认购药订单收货。
     * @param drugOrderId 购药订单
     * @return 收货结果
     */
    DrugOrderReceiptVO confirmDrugOrderReceipt(Long drugOrderId);

    /**
     * 登记购药订单收货后自动开启用药提醒的授权。
     *
     * @param drugOrderId 购药订单 ID
     * @return 授权结果
     */
    DrugOrderReminderActivationVO authorizeDrugOrderReminderAfterReceipt(Long drugOrderId);


    /**
     * 模拟购药订单支付。
     * @param paymentId 支付单
     * @param request 支付请求
     * @return 支付结果
     */
    RegisteringPaymentSuccessVO simulateDrugOrderPayment(Long paymentId, RegisteringPaymentSimulateRequest request);

    /**
     * 订单超时处理。
     * @param drugOrderId 购药订单 ID
     */
    void expireDrugOrder(Long drugOrderId);
}
