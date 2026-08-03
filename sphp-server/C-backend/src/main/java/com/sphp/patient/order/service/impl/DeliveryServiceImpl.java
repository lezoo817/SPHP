package com.sphp.patient.order.service.impl;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.common.constant.DeliveryConstant;
import com.sphp.patient.common.enums.DeliveryProvinceEnum;
import com.sphp.patient.order.dto.DeliveryAddressCreateRequest;
import com.sphp.patient.order.dto.DeliveryAddressUpdateRequest;
import com.sphp.patient.order.entity.DeliveryAddress;
import com.sphp.patient.order.mapper.DeliveryAddressMapper;
import com.sphp.patient.order.mapper.DeliveryDataMapper;
import com.sphp.patient.order.service.DeliveryService;
import com.sphp.patient.order.vo.DeliveryAddressDeleteVO;
import com.sphp.patient.order.vo.DeliveryAddressVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * C端收货地址服务实现。
 */
@Service
@RequiredArgsConstructor
public class DeliveryServiceImpl implements DeliveryService {

    private final DeliveryAddressMapper deliveryAddressMapper;
    private final DeliveryDataMapper deliveryDataMapper;

    /** {@inheritDoc} */
    @Override
    public List<DeliveryAddressVO> deliveryListAddresses() {
        return deliveryDataMapper.deliveryListAddresses(deliveryCurrentUserId()).stream().map(this::deliveryToVo).toList();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryAddressVO deliveryCreateAddress(DeliveryAddressCreateRequest request) {
        Long userId = deliveryCurrentUserId();
        deliveryLockUser(userId);
        if (deliveryDataMapper.deliveryCountAddresses(userId) >= DeliveryConstant.MAX_ACTIVE_ADDRESS_COUNT) {
            throw deliveryInvalidInput("收货地址数量不能超过20条");
        }
        DeliveryAddress address = new DeliveryAddress();
        address.setUserId(userId);
        deliveryApplyCreateRequest(address, request);
        // 首个有效地址自动成为默认地址，减少首次下单前的额外操作。
        address.setIsDefault(deliveryDataMapper.deliveryCountAddresses(userId) == 0);
        if (deliveryAddressMapper.insert(address) != 1) {
            throw deliverySystemError("收货地址新增失败");
        }
        return deliveryToVo(address);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryAddressVO deliveryUpdateAddress(Long addressId, DeliveryAddressUpdateRequest request) {
        Long userId = deliveryCurrentUserId();
        deliveryLockUser(userId);
        DeliveryAddress address = deliveryRequireOwnedAddress(addressId, userId);
        deliveryApplyUpdateRequest(address, request);
        if (deliveryAddressMapper.updateById(address) != 1) {
            throw deliveryStatusConflict("收货地址状态已变化");
        }
        return deliveryToVo(address);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryAddressDeleteVO deliveryDeleteAddress(Long addressId) {
        Long userId = deliveryCurrentUserId();
        deliveryLockUser(userId);
        DeliveryAddress address = deliveryRequireOwnedAddress(addressId, userId);
        OffsetDateTime now = OffsetDateTime.now();
        if (deliveryDataMapper.deliverySoftDeleteAddress(userId, addressId, now) != 1) {
            throw deliveryStatusConflict("收货地址状态已变化");
        }
        // 删除默认地址后为剩余最早地址补位，维持一个稳定默认地址。
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            DeliveryAddress fallback = deliveryDataMapper.deliverySelectFirstAddress(userId);
            if (fallback != null && deliveryDataMapper.deliverySetDefault(userId, fallback.getId(), now) != 1) {
                throw deliveryStatusConflict("默认收货地址设置失败");
            }
        }
        return DeliveryAddressDeleteVO.builder().id(addressId).deletedAt(now).build();
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryAddressVO deliverySetDefaultAddress(Long addressId) {
        Long userId = deliveryCurrentUserId();
        deliveryLockUser(userId);
        DeliveryAddress address = deliveryRequireOwnedAddress(addressId, userId);
        OffsetDateTime now = OffsetDateTime.now();
        // 先清空旧默认标记，再设置新默认标记以满足部分唯一索引约束。
        deliveryDataMapper.deliveryClearDefault(userId, now);
        if (deliveryDataMapper.deliverySetDefault(userId, addressId, now) != 1) {
            throw deliveryStatusConflict("默认收货地址状态已变化");
        }
        address.setIsDefault(true);
        address.setUpdatedAt(now);
        return deliveryToVo(address);
    }

    /** {@inheritDoc} */
    @Override
    public String deliveryResolveOrderAddress(Long addressId, String legacyDeliveryAddress) {
        boolean hasAddressId = addressId != null;
        boolean hasLegacyAddress = legacyDeliveryAddress != null && !legacyDeliveryAddress.isBlank();
        if (hasAddressId == hasLegacyAddress) {
            throw deliveryInvalidInput("addressId 与 deliveryAddress 必须且只能传入一个");
        }
        if (hasLegacyAddress) {
            return legacyDeliveryAddress.trim();
        }
        DeliveryAddress address = deliveryRequireOwnedAddress(addressId, deliveryCurrentUserId());
        String snapshot = address.getReceiverName() + " " + address.getReceiverPhone() + " "
                + DeliveryProvinceEnum.valueOf(address.getProvince()).getDisplayName() + address.getCity()
                + (address.getDistrict() == null ? "" : address.getDistrict()) + address.getDetailAddress();
        if (snapshot.length() > 500) {
            throw deliveryInvalidInput("收货地址快照不能超过500个字符");
        }
        return snapshot;
    }

    /**
     * 获取当前 C端登录用户 ID。
     *
     * @return 当前用户 ID
     */
    private Long deliveryCurrentUserId() {
        return CUserContext.getRequired().userId();
    }

    /**
     * 对当前用户行加锁，避免并发修改默认地址状态。
     *
     * @param userId 当前用户 ID
     */
    private void deliveryLockUser(Long userId) {
        if (deliveryDataMapper.deliveryLockUser(userId) == null) {
            throw deliveryNotFound("当前账号不存在");
        }
    }

    /**
     * 查询并校验当前用户拥有的有效地址。
     *
     * @param addressId 地址 ID
     * @param userId 当前用户 ID
     * @return 已校验地址
     */
    private DeliveryAddress deliveryRequireOwnedAddress(Long addressId, Long userId) {
        DeliveryAddress address = deliveryDataMapper.deliverySelectAddress(addressId);
        if (address == null) {
            throw deliveryNotFound("收货地址不存在");
        }
        if (!userId.equals(address.getUserId())) {
            throw deliveryForbidden("无权访问该收货地址");
        }
        return address;
    }

    /**
     * 将新增请求字段白名单复制到地址实体。
     *
     * @param address 待保存地址实体
     * @param request 新增请求
     */
    private void deliveryApplyCreateRequest(DeliveryAddress address, DeliveryAddressCreateRequest request) {
        address.setReceiverName(request.getReceiverName().trim());
        address.setReceiverPhone(request.getReceiverPhone());
        address.setProvince(request.getProvince());
        address.setCity(request.getCity().trim());
        address.setDistrict(deliveryTrimToNull(request.getDistrict()));
        address.setDetailAddress(request.getDetailAddress().trim());
    }

    /**
     * 将更新请求字段白名单复制到地址实体。
     *
     * @param address 已存在地址实体
     * @param request 更新请求
     */
    private void deliveryApplyUpdateRequest(DeliveryAddress address, DeliveryAddressUpdateRequest request) {
        address.setReceiverName(request.getReceiverName().trim());
        address.setReceiverPhone(request.getReceiverPhone());
        address.setProvince(request.getProvince());
        address.setCity(request.getCity().trim());
        address.setDistrict(deliveryTrimToNull(request.getDistrict()));
        address.setDetailAddress(request.getDetailAddress().trim());
    }

    /**
     * 将空白可选文本统一转换为空值。
     *
     * @param value 原始文本
     * @return 去空白后的文本或空值
     */
    private String deliveryTrimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    /**
     * 转换地址实体为响应对象。
     *
     * @param address 地址实体
     * @return API 响应对象
     */
    private DeliveryAddressVO deliveryToVo(DeliveryAddress address) {
        DeliveryProvinceEnum province = DeliveryProvinceEnum.valueOf(address.getProvince());
        return DeliveryAddressVO.builder().id(address.getId()).receiverName(address.getReceiverName())
                .receiverPhone(address.getReceiverPhone()).province(address.getProvince()).provinceName(province.getDisplayName())
                .city(address.getCity()).district(address.getDistrict()).detailAddress(address.getDetailAddress())
                .isDefault(address.getIsDefault()).createdAt(address.getCreatedAt()).updatedAt(address.getUpdatedAt()).build();
    }

    /** 创建参数错误异常。 */
    private CAuthException deliveryInvalidInput(String message) {
        return new CAuthException(ErrorCodeEnum.INVALID_PARAMETER, HttpStatus.BAD_REQUEST, message);
    }
    /** 创建资源不存在异常。 */
    private CAuthException deliveryNotFound(String message) {
        return new CAuthException(ErrorCodeEnum.INVALID_USER_INPUT, HttpStatus.NOT_FOUND, message);
    }
    /** 创建资源越权异常。 */
    private CAuthException deliveryForbidden(String message) {
        return new CAuthException(ErrorCodeEnum.UNAUTHORIZED, HttpStatus.FORBIDDEN, message);
    }
    /** 创建状态冲突异常。 */
    private CAuthException deliveryStatusConflict(String message) {
        return new CAuthException(ErrorCodeEnum.BUSINESS_STATUS_CONFLICT, HttpStatus.CONFLICT, message);
    }
    /** 创建系统异常。 */
    private CAuthException deliverySystemError(String message) {
        return new CAuthException(ErrorCodeEnum.SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
