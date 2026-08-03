package com.sphp.patient.order.mapper;

import com.sphp.patient.order.entity.DeliveryAddress;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;
import java.util.List;

/**
 * C端收货地址与配送推荐跨表数据访问接口。
 */
@Mapper
public interface DeliveryDataMapper {

    /** 对当前用户行加锁以串行化默认地址变更。 */
    Long deliveryLockUser(@Param("userId") Long userId);

    /** 查询当前用户全部有效收货地址。 */
    List<DeliveryAddress> deliveryListAddresses(@Param("userId") Long userId);

    /** 查询未软删除的收货地址，不预先限制用户以区分越权和不存在。 */
    DeliveryAddress deliverySelectAddress(@Param("addressId") Long addressId);

    /** 统计当前用户有效地址数量。 */
    long deliveryCountAddresses(@Param("userId") Long userId);

    /** 清空当前用户有效地址的默认标记。 */
    int deliveryClearDefault(@Param("userId") Long userId, @Param("now") OffsetDateTime now);

    /** 将指定有效地址设置为默认地址。 */
    int deliverySetDefault(@Param("userId") Long userId, @Param("addressId") Long addressId, @Param("now") OffsetDateTime now);

    /** 软删除当前用户的指定有效地址。 */
    int deliverySoftDeleteAddress(@Param("userId") Long userId, @Param("addressId") Long addressId, @Param("now") OffsetDateTime now);

    /** 查询当前用户最早创建的有效地址，用于补位默认地址。 */
    DeliveryAddress deliverySelectFirstAddress(@Param("userId") Long userId);

    /** 查询医院地址以解析模拟配送省市。 */
    String deliverySelectHospitalAddress(@Param("hospitalId") Long hospitalId);
}
