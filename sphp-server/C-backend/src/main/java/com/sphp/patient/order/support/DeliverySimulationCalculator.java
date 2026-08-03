package com.sphp.patient.order.support;

import com.sphp.patient.common.enums.DeliveryProvinceEnum;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * 基于地址、省市和院内药房的确定性模拟配送计算器。
 */
public class DeliverySimulationCalculator {

    /**
     * 计算药房到收货地址的模拟距离与配送时效。
     *
     * @param userProvince 收货地址省市
     * @param detailAddress 收货详细地址
     * @param hospitalId 医院 ID
     * @param pharmacyId 药房 ID
     * @param hospitalProvince 医院地址解析出的省市
     * @param provinceCoefficient 跨省距离系数；同省时传 {@code 0}
     * @return 稳定的模拟配送结果
     */
    public DeliverySimulationResult deliveryCalculate(DeliveryProvinceEnum userProvince, String detailAddress,
                                                       Long hospitalId, Long pharmacyId, DeliveryProvinceEnum hospitalProvince,
                                                       double provinceCoefficient) {
        String normalizedAddress = deliveryNormalizeAddress(detailAddress);
        String baseSeed = userProvince.name() + ':' + normalizedAddress + ':' + hospitalId;
        long pharmacyDistanceOffset = deliveryPositiveHash(pharmacyId + ":distance") % 2_001L;
        int pharmacyTimeOffset = (int) (deliveryPositiveHash(pharmacyId + ":time") % 16L);

        if (userProvince == hospitalProvince) {
            long baseDistanceMeters = (5L + deliveryPositiveHash(baseSeed + ":same-distance") % 45L) * 1_000L;
            int baseDeliveryMinutes = 900 + (int) (deliveryPositiveHash(baseSeed + ":same-time") % 46L);
            return new DeliverySimulationResult(baseDistanceMeters + pharmacyDistanceOffset,
                    baseDeliveryMinutes + pharmacyTimeOffset);
        }

        long jitterKilometers = deliveryPositiveHash(baseSeed + ":cross-distance") % 61L - 30L;
        long baseDistanceMeters = Math.round((150D + provinceCoefficient * 450D + jitterKilometers) * 1_000D);
        int baseDeliveryMinutes = 960 + (int) Math.ceil(provinceCoefficient * 240D)
                + (int) (deliveryPositiveHash(baseSeed + ":cross-time") % 91L);
        return new DeliverySimulationResult(baseDistanceMeters + pharmacyDistanceOffset,
                baseDeliveryMinutes + pharmacyTimeOffset);
    }

    /**
     * 标准化地址以消除门牌、楼栋和房间号造成的无意义差异。
     *
     * @param detailAddress 原始详细地址
     * @return 用于稳定哈希的地址摘要文本
     */
    private String deliveryNormalizeAddress(String detailAddress) {
        String compact = detailAddress == null ? "" : detailAddress.replaceAll("\\s+", "");
        return compact.replaceFirst("[0-9０-９]+.*$", "");
    }

    /**
     * 计算文本的正数哈希值，避免运行时随机数导致展示结果漂移。
     *
     * @param value 参与计算的稳定文本
     * @return 非负哈希值
     */
    private long deliveryPositiveHash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            long result = 0L;
            for (int index = 0; index < Long.BYTES; index++) {
                result = (result << Byte.SIZE) | (digest[index] & 0xffL);
            }
            return result & Long.MAX_VALUE;
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 算法不可用", exception);
        }
    }
}
