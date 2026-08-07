package com.sphp.patient.order.service;

import com.sphp.patient.order.dto.DeliveryAddressCreateRequest;
import com.sphp.patient.order.dto.DeliveryAddressUpdateRequest;
import com.sphp.patient.order.support.DeliveryOrderSnapshot;
import com.sphp.patient.order.vo.DeliveryAddressDeleteVO;
import com.sphp.patient.order.vo.DeliveryAddressVO;
import com.sphp.patient.order.vo.DeliveryPharmacyRecommendationVO;

import java.util.List;

/**
 * C端收货地址与模拟配送服务。
 */
public interface DeliveryService {

    /**
     * 查询当前登录账号的全部有效收货地址。
     *
     * @return 默认地址优先的地址列表
     */
    List<DeliveryAddressVO> deliveryListAddresses();

    /**
     * 新增当前登录账号的收货地址。
     *
     * @param request 新增地址请求
     * @return 新增后的地址
     */
    DeliveryAddressVO deliveryCreateAddress(DeliveryAddressCreateRequest request);

    /**
     * 更新当前登录账号的收货地址。
     *
     * @param addressId 地址 ID
     * @param request 更新地址请求
     * @return 更新后的地址
     */
    DeliveryAddressVO deliveryUpdateAddress(Long addressId, DeliveryAddressUpdateRequest request);

    /**
     * 软删除当前登录账号的收货地址。
     *
     * @param addressId 地址 ID
     * @return 删除结果
     */
    DeliveryAddressDeleteVO deliveryDeleteAddress(Long addressId);

    /**
     * 将当前登录账号的指定收货地址设置为默认地址。
     *
     * @param addressId 地址 ID
     * @return 设置后的地址
     */
    DeliveryAddressVO deliverySetDefaultAddress(Long addressId);

    /**
     * 为购药订单解析地址簿快照或兼容旧地址文本。
     *
     * @param addressId 新版地址簿 ID
     * @param legacyDeliveryAddress 旧版完整地址文本
     * @return 可写入订单的不可变地址快照
     */
    String deliveryResolveOrderAddress(Long addressId, String legacyDeliveryAddress);

    /**
     * 为购药订单解析不可变地址与模拟配送时效快照。
     *
     * @param addressId 新版地址簿 ID
     * @param legacyDeliveryAddress 旧版完整地址文本
     * @param hospitalId 药房所属医院 ID
     * @param pharmacyId 选定院内药房 ID
     * @return 可写入订单的地址与预计配送分钟数快照
     */
    DeliveryOrderSnapshot deliveryResolveOrderSnapshot(Long addressId, String legacyDeliveryAddress,
                                                       Long hospitalId, Long pharmacyId);

    /**
     * 基于已批准处方、当前账号地址和真实库存推荐院内药房。
     *
     * @param patientId 可选就诊人 ID，未传时使用本人
     * @param prescriptionId 已批准处方 ID
     * @param addressId 当前账号收货地址 ID
     * @param sort 排序方式
     * @return 药房推荐列表
     */
    List<DeliveryPharmacyRecommendationVO> deliveryRecommendPharmacies(Long patientId, Long prescriptionId, Long addressId, String sort);
}
