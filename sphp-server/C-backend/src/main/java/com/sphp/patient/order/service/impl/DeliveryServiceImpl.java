package com.sphp.patient.order.service.impl;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.common.constant.DeliveryConstant;
import com.sphp.patient.common.enums.DeliveryProvinceEnum;
import com.sphp.patient.common.enums.DeliverySortEnum;
import com.sphp.patient.order.config.DeliveryProperties;
import com.sphp.patient.order.dto.DeliveryAddressCreateRequest;
import com.sphp.patient.order.dto.DeliveryAddressUpdateRequest;
import com.sphp.patient.order.entity.DeliveryAddress;
import com.sphp.patient.order.mapper.DeliveryAddressMapper;
import com.sphp.patient.order.mapper.DeliveryDataMapper;
import com.sphp.patient.order.mapper.OrderDataMapper;
import com.sphp.patient.order.mapper.OrderPharmacyStockRecord;
import com.sphp.patient.order.mapper.OrderPrescriptionItemRecord;
import com.sphp.patient.order.mapper.OrderPrescriptionRecord;
import com.sphp.patient.order.service.DeliveryService;
import com.sphp.patient.order.support.DeliverySimulationCalculator;
import com.sphp.patient.order.support.DeliverySimulationResult;
import com.sphp.patient.order.vo.DeliveryAddressDeleteVO;
import com.sphp.patient.order.vo.DeliveryAddressVO;
import com.sphp.patient.order.vo.DeliveryPharmacyRecommendationVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;

import static com.sphp.patient.common.constant.DeliveryConstant.MAX_ACTIVE_ADDRESS_COUNT;
import static com.sphp.patient.common.enums.DeliverySortEnum.RECOMMENDED;
import static com.sphp.shared.common.enums.ErrorCodeEnum.*;

/**
 * C端收货地址服务实现。
 */
@Service
@RequiredArgsConstructor
public class DeliveryServiceImpl implements DeliveryService {

    // 收货地址数据映射
    private final DeliveryAddressMapper deliveryAddressMapper;
    // 配送数据映射
    private final DeliveryDataMapper deliveryDataMapper;
    // 购药订单数据映射
    private final OrderDataMapper orderDataMapper;
    // 配送配置
    private final DeliveryProperties deliveryProperties;
    // 配送模拟计算器
    private final DeliverySimulationCalculator deliverySimulationCalculator;

    /**
     * 列出当前用户的收货地址。
     * @return 收货地址列表。
     */
    @Override
    public List<DeliveryAddressVO> deliveryListAddresses() {
        return deliveryDataMapper.deliveryListAddresses(deliveryCurrentUserId()).stream()
                .map(this::deliveryToVo)
                .toList();
    }

    /**
     * 新增收货地址。
     * @param request 新增地址请求
     * @return 新增收货地址。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryAddressVO deliveryCreateAddress(DeliveryAddressCreateRequest request) {
        Long userId = deliveryCurrentUserId();
        // 用户锁
        deliveryLockUser(userId);
        if (deliveryDataMapper.deliveryCountAddresses(userId) >= MAX_ACTIVE_ADDRESS_COUNT) {
            throw deliveryInvalidInput("收货地址数量不能超过20条");
        }
        DeliveryAddress address = new DeliveryAddress();
        address.setUserId(userId);
        // 收货地址
        deliveryApplyCreateRequest(address, request);
        // 首个有效地址自动成为默认地址，减少首次下单前的额外操作。
        address.setIsDefault(deliveryDataMapper.deliveryCountAddresses(userId) == 0);
        if (deliveryAddressMapper.insert(address) != 1) {
            throw deliverySystemError("收货地址新增失败");
        }
        return deliveryToVo(address);
    }

    /**
     * 更新收货地址。
     * @param addressId 地址 ID
     * @param request 更新地址请求
     * @return 更新收货地址。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryAddressVO deliveryUpdateAddress(Long addressId, DeliveryAddressUpdateRequest request) {
        Long userId = deliveryCurrentUserId();
        // 用户锁
        deliveryLockUser(userId);
        // 收货地址
        DeliveryAddress address = deliveryRequireOwnedAddress(addressId, userId);
        // 更新
        deliveryApplyUpdateRequest(address, request);
        if (deliveryAddressMapper.updateById(address) != 1) {
            throw deliveryStatusConflict("收货地址状态已变化");
        }
        return deliveryToVo(address);
    }

    /**
     *  删除收货地址。
     * @param addressId 地址 ID
     * @return 删除收货地址。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryAddressDeleteVO deliveryDeleteAddress(Long addressId) {
        Long userId = deliveryCurrentUserId();
        // 用户锁
        deliveryLockUser(userId);
        // 收货地址
        DeliveryAddress address = deliveryRequireOwnedAddress(addressId, userId);
        OffsetDateTime now = OffsetDateTime.now();
        // 软删除
        if (deliveryDataMapper.deliverySoftDeleteAddress(userId, addressId, now) != 1) {
            throw deliveryStatusConflict("收货地址状态已变化");
        }
        // 删除默认地址后为剩余最早地址补位，维持一个稳定默认地址。
        if (Boolean.TRUE.equals(address.getIsDefault())) {
            // 获取剩余最早地址
            DeliveryAddress fallback = deliveryDataMapper.deliverySelectFirstAddress(userId);
            if (fallback != null && deliveryDataMapper.deliverySetDefault(userId, fallback.getId(), now) != 1) {
                throw deliveryStatusConflict("默认收货地址设置失败");
            }
        }
        return DeliveryAddressDeleteVO.builder().id(addressId).deletedAt(now).build();
    }

    /**
     * 设置默认收货地址。
     * @param addressId 地址 ID
     * @return 设置默认收货地址。
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public DeliveryAddressVO deliverySetDefaultAddress(Long addressId) {
        Long userId = deliveryCurrentUserId();
        // 用户锁
        deliveryLockUser(userId);
        // 收货地址
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

    /**
     * 解析订单收货地址。
     * @param addressId 新版地址簿 ID
     * @param legacyDeliveryAddress 旧版完整地址文本
     * @return 解析后的收货地址。
     */
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
     * 推荐配送 Pharmacy。
     * @param patientId 可选就诊人 ID，未传时使用本人
     * @param prescriptionId 已批准处方 ID
     * @param addressId 当前账号收货地址 ID
     * @param sort 排序方式
     * @return 推荐配送 Pharmacy。
     */
    @Override
    public List<DeliveryPharmacyRecommendationVO> deliveryRecommendPharmacies(Long patientId, Long prescriptionId, Long addressId, String sort) {
        Long userId = deliveryCurrentUserId();
        OrderPrescriptionRecord prescription = deliveryRequireAccessibleApprovedPrescription(userId, patientId, prescriptionId);
        DeliveryAddress address = deliveryRequireOwnedAddress(addressId, userId);
        String hospitalAddress = deliveryDataMapper.deliverySelectHospitalAddress(prescription.hospitalId());
        DeliveryProvinceEnum hospitalProvince = DeliveryProvinceEnum.resolveFromAddress(hospitalAddress);

        if (hospitalProvince == null) {
            // 医院地址未声明省市时拒绝生成虚假推荐，等待基础医院数据修正。
            throw deliverySystemError("医院地址缺少配送省市信息");
        }
        // 用户地址
        DeliveryProvinceEnum userProvince = DeliveryProvinceEnum.valueOf(address.getProvince());

        double coefficient = deliveryProperties.deliveryProvinceCoefficient(userProvince, hospitalProvince);
        // 配送省市
        DeliverySortEnum sortEnum = deliveryResolveSort(sort);
        Map<Long, Integer> quantities = new LinkedHashMap<>();
        for (OrderPrescriptionItemRecord item : orderDataMapper.selectOrderPrescriptionItems(prescriptionId)) {
            quantities.put(item.drugId(), item.quantity());
        }
        // 库存
        Map<Long, List<OrderPharmacyStockRecord>> pharmacyStocks = new LinkedHashMap<>();
        for (OrderPharmacyStockRecord stock : orderDataMapper.selectOrderPharmacyInventory(prescriptionId, prescription.hospitalId())) {
            pharmacyStocks.computeIfAbsent(stock.pharmacyId(), ignored -> new java.util.ArrayList<>()).add(stock);
        }
        // 候选
        List<DeliveryRecommendationCandidate> candidates = pharmacyStocks.values().stream()
                .map(stocks -> deliveryBuildRecommendationCandidate(address, hospitalProvince, coefficient, quantities, stocks)).toList();
        return deliverySortCandidates(candidates, sortEnum).stream().map(candidate -> deliveryToRecommendationVo(candidate, candidates)).toList();
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
        return value == null || value.isBlank() ? null : value.trim(); // 空白文本转为空值
    }

    /**
     * 转换地址实体为响应对象。
     *
     * @param address 地址实体
     * @return API 响应对象
     */
    private DeliveryAddressVO deliveryToVo(DeliveryAddress address) {
        DeliveryProvinceEnum province = DeliveryProvinceEnum.valueOf(address.getProvince());
        return DeliveryAddressVO.builder()
                .id(address.getId())
                .receiverName(address.getReceiverName())
                .receiverPhone(address.getReceiverPhone())
                .province(address.getProvince())
                .provinceName(province.getDisplayName())
                .city(address.getCity())
                .district(address.getDistrict())
                .detailAddress(address.getDetailAddress())
                .isDefault(address.getIsDefault())
                .createdAt(address.getCreatedAt())
                .updatedAt(address.getUpdatedAt())
                .build();
    }

    /**
     * 校验当前账号可访问且状态为已批准的处方。
     *
     * @param userId 当前用户 ID
     * @param requestedPatientId 可选就诊人 ID
     * @param prescriptionId 处方 ID
     * @return 已校验处方投影
     */
    private OrderPrescriptionRecord deliveryRequireAccessibleApprovedPrescription(Long userId, Long requestedPatientId, Long prescriptionId) {
        OrderPrescriptionRecord prescription = orderDataMapper.selectOrderPrescription(prescriptionId);
        //若处方不存在或状态非已批准，则返回错误
        if (prescription == null || !"APPROVED".equals(prescription.status())) {
            throw deliveryNotFound("处方不存在");
        }
        Long targetPatientId = requestedPatientId == null ? orderDataMapper.selectOrderSelfPatientId(userId) : requestedPatientId;
        //若就诊人不存在或状态非正常，则返回错误
        if (targetPatientId == null || !orderDataMapper.existsOrderActivePatient(targetPatientId)) {
            throw deliveryNotFound("就诊人不存在");
        }
        //若当前账号与处方关联就诊人不一致，则返回错误
        if (!orderDataMapper.hasOrderActivePatientRelation(userId, targetPatientId) || !targetPatientId.equals(prescription.patientId())) {
            throw deliveryForbidden("无权访问该处方");
        }
        return prescription;
    }

    /**
     * 根据单个药房的真实库存与稳定模拟结果创建候选项。
     *
     * @param address 当前账号地址
     * @param hospitalProvince 医院省市
     * @param coefficient 跨省系数
     * @param quantities 处方药品数量
     * @param stocks 单个药房库存项
     * @return 推荐候选项
     */
    private DeliveryRecommendationCandidate deliveryBuildRecommendationCandidate(DeliveryAddress address, DeliveryProvinceEnum hospitalProvince,
                                                                                   double coefficient, Map<Long, Integer> quantities,
                                                                                   List<OrderPharmacyStockRecord> stocks) {
        //获取单个药房的 simulations
        OrderPharmacyStockRecord first = stocks.getFirst();
        DeliverySimulationResult simulation = deliverySimulationCalculator.deliveryCalculate(
                DeliveryProvinceEnum.valueOf(address.getProvince()),  // 省
                        address.getDetailAddress(), // 详细地址
                        first.hospitalId(),
                        first.pharmacyId(),
                        hospitalProvince, // 医院省市
                        coefficient // 省系数
        );
        int amountCent = stocks.stream()
                .mapToInt(stock -> stock.unitPriceCent() * quantities.getOrDefault(stock.drugId(),
                        0))
                .sum();
        List<DeliveryPharmacyRecommendationVO.Item> items = stocks.stream()
                .map(stock -> DeliveryPharmacyRecommendationVO.Item.builder()
                        .drugId(stock.drugId())
                        .quantity(quantities.get(stock.drugId())) // 数量
                        .availableCount(stock.availableCount()) // 库存数量
                        .unitPriceCent(stock.unitPriceCent()) // 单价
                        .build())
                .toList();
        return new DeliveryRecommendationCandidate(first, simulation, amountCent, items);
    }

    /**
     * 根据调用方指定排序方式排列药房候选项。
     *
     * @param candidates 药房候选项
     * @param sort 排序方式
     * @return 已排序候选项
     */
    private List<DeliveryRecommendationCandidate> deliverySortCandidates(List<DeliveryRecommendationCandidate> candidates, DeliverySortEnum sort) {
        Comparator<DeliveryRecommendationCandidate> comparator = switch (sort) {
            case PRICE -> Comparator.comparingInt(DeliveryRecommendationCandidate::amountCent);
            case DISTANCE -> Comparator.comparingLong(item -> item.simulation().distanceMeters());
            case DELIVERY_TIME -> Comparator.comparingInt(item -> item.simulation().estimatedDeliveryMinutes());
            case RECOMMENDED -> Comparator.comparingDouble((DeliveryRecommendationCandidate item) -> -deliveryScore(item, candidates));
        };
        return candidates.stream().sorted(comparator.thenComparing(item -> item.stock().pharmacyId())).toList();
    }

    /**
     * 计算价格、距离和配送时效的综合推荐分数。
     *
     * @param candidate 当前候选项
     * @param candidates 全部候选项
     * @return 范围为零至一百的分数
     */
    private double deliveryScore(DeliveryRecommendationCandidate candidate, List<DeliveryRecommendationCandidate> candidates) {
        int minAmount = candidates.stream().mapToInt(DeliveryRecommendationCandidate::amountCent).min().orElse(0);
        int maxAmount = candidates.stream().mapToInt(DeliveryRecommendationCandidate::amountCent).max().orElse(0);
        long minDistance = candidates.stream().mapToLong(item -> item.simulation().distanceMeters()).min().orElse(0L);
        long maxDistance = candidates.stream().mapToLong(item -> item.simulation().distanceMeters()).max().orElse(0L);
        int minMinutes = candidates.stream().mapToInt(item -> item.simulation().estimatedDeliveryMinutes()).min().orElse(0);
        int maxMinutes = candidates.stream().mapToInt(item -> item.simulation().estimatedDeliveryMinutes()).max().orElse(0);
        return 45D * deliveryNormalizeScore(candidate.amountCent(), minAmount, maxAmount)
                + 30D * deliveryNormalizeScore(candidate.simulation().distanceMeters(), minDistance, maxDistance)
                + 25D * deliveryNormalizeScore(candidate.simulation().estimatedDeliveryMinutes(), minMinutes, maxMinutes);
    }

    /**
     * 将较小值归一化为较高推荐分数。
     *
     * @param value 当前值
     * @param min 最小值
     * @param max 最大值
     * @return 零至一的归一化分数
     */
    private double deliveryNormalizeScore(long value, long min, long max) {
        return min == max ? 1D : 1D - (double) (value - min) / (max - min);
    }

    /**
     * 转换候选项为接口响应对象。
     *
     * @param candidate 当前候选项
     * @param candidates 全部候选项
     * @return 推荐响应
     */
    private DeliveryPharmacyRecommendationVO deliveryToRecommendationVo(DeliveryRecommendationCandidate candidate,
                                                                          List<DeliveryRecommendationCandidate> candidates) {
        return DeliveryPharmacyRecommendationVO.builder()
                .pharmacyId(candidate.stock().pharmacyId())
                .name(candidate.stock().pharmacyName())
                .hospitalId(candidate.stock().hospitalId())
                .isDefault(candidate.stock().isDefault())
                .distanceMeters(candidate.simulation().distanceMeters())
                .estimatedDeliveryMinutes(candidate.simulation().estimatedDeliveryMinutes())
                .totalAmountCent(candidate.amountCent())
                .score((int) Math.round(deliveryScore(candidate, candidates)))
                .recommendReasons(List.of("处方药品均有货", "价格、距离与配送时效综合推荐"))
                .items(candidate.items())
                .build();
    }

    /**
     * 解析可选的推荐排序参数。
     *
     * @param sort 原始排序参数
     * @return 有效排序枚举
     */
    private DeliverySortEnum deliveryResolveSort(String sort) {
        // 未指定排序
        if (sort == null || sort.isBlank()) {
            return RECOMMENDED;
        }
        try {
            return DeliverySortEnum.valueOf(sort.trim()); // 排序参数
        } catch (IllegalArgumentException exception) {
            throw deliveryInvalidInput("sort 不在允许范围内");
        }
    }

    /**
     * 药房推荐计算过程中的临时候选项。
     *
     * @param stock 药房基础库存信息
     * @param simulation 模拟配送结果
     * @param amountCent 处方总价
     * @param items 处方库存项
     */
    private record DeliveryRecommendationCandidate(OrderPharmacyStockRecord stock, DeliverySimulationResult simulation,
                                                   int amountCent, List<DeliveryPharmacyRecommendationVO.Item> items) {
    }

    /** 创建参数错误异常。 */
    private CAuthException deliveryInvalidInput(String message) {
        return new CAuthException(INVALID_PARAMETER, HttpStatus.BAD_REQUEST, message);
    }
    /** 创建资源不存在异常。 */
    private CAuthException deliveryNotFound(String message) {
        return new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, message);
    }
    /** 创建资源越权异常。 */
    private CAuthException deliveryForbidden(String message) {
        return new CAuthException(UNAUTHORIZED, HttpStatus.FORBIDDEN, message);
    }
    /** 创建状态冲突异常。 */
    private CAuthException deliveryStatusConflict(String message) {
        return new CAuthException(BUSINESS_STATUS_CONFLICT, HttpStatus.CONFLICT, message);
    }
    /** 创建系统异常。 */
    private CAuthException deliverySystemError(String message) {
        return new CAuthException(SYSTEM_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, message);
    }
}
