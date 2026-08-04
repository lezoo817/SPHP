package com.sphp.patient.order.entity;

import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableName;
import com.sphp.shared.entity.BaseDeleteDO;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * C端登录账号的收货地址实体。
 */
@Getter
@Setter
@NoArgsConstructor
@TableName("c_user_delivery_address")
public class DeliveryAddress extends BaseDeleteDO {

    /** 地址所属 C端用户 ID。 */
    @TableField("user_id")
    private Long userId;

    /** 收件人姓名。 */
    @TableField("receiver_name")
    private String receiverName;

    /** 收件人手机号。 */
    @TableField("receiver_phone")
    private String receiverPhone;

    /** 省市编码。 */
    @TableField("province")
    private String province;

    /** 城市名称。 */
    @TableField("city")
    private String city;

    /** 区县名称。 */
    @TableField("district")
    private String district;

    /** 详细收货地址。 */
    @TableField("detail_address")
    private String detailAddress;

    /** 是否为当前账号默认地址。 */
    @TableField("is_default")
    private Boolean isDefault;
}
