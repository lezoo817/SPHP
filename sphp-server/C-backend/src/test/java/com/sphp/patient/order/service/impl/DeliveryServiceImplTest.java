package com.sphp.patient.order.service.impl;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.order.dto.DeliveryAddressCreateRequest;
import com.sphp.patient.order.entity.DeliveryAddress;
import com.sphp.patient.order.mapper.DeliveryAddressMapper;
import com.sphp.patient.order.mapper.DeliveryDataMapper;
import com.sphp.patient.order.vo.DeliveryAddressDeleteVO;
import com.sphp.patient.order.vo.DeliveryAddressVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C端收货地址服务测试。
 */
class DeliveryServiceImplTest {

    /** 清理测试线程用户上下文。 */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
    }

    /** 验证首个收货地址自动设置为默认地址。 */
    @Test
    void deliveryCreateAddressMarksFirstAddressAsDefault() {
        DeliveryAddressMapper addressMapper = mock(DeliveryAddressMapper.class);
        DeliveryDataMapper dataMapper = mock(DeliveryDataMapper.class);
        DeliveryServiceImpl service = new DeliveryServiceImpl(addressMapper, dataMapper);
        context();
        when(dataMapper.deliveryLockUser(10001L)).thenReturn(10001L);
        when(dataMapper.deliveryCountAddresses(10001L)).thenReturn(0L);
        when(addressMapper.insert(any(DeliveryAddress.class))).thenAnswer(invocation -> {
            invocation.getArgument(0, DeliveryAddress.class).setId(30001L);
            return 1;
        });

        DeliveryAddressVO result = service.deliveryCreateAddress(createRequest());

        assertEquals(30001L, result.getId());
        assertEquals(true, result.getIsDefault());
        verify(addressMapper).insert(any(DeliveryAddress.class));
    }

    /** 验证删除默认地址后将剩余最早地址补为默认地址。 */
    @Test
    void deliveryDeleteAddressPromotesFallbackDefault() {
        DeliveryAddressMapper addressMapper = mock(DeliveryAddressMapper.class);
        DeliveryDataMapper dataMapper = mock(DeliveryDataMapper.class);
        DeliveryServiceImpl service = new DeliveryServiceImpl(addressMapper, dataMapper);
        context();
        DeliveryAddress defaultAddress = address(30001L, true);
        DeliveryAddress fallback = address(30002L, false);
        when(dataMapper.deliveryLockUser(10001L)).thenReturn(10001L);
        when(dataMapper.deliverySelectAddress(30001L)).thenReturn(defaultAddress);
        when(dataMapper.deliverySoftDeleteAddress(eq(10001L), eq(30001L), any())).thenReturn(1);
        when(dataMapper.deliverySelectFirstAddress(10001L)).thenReturn(fallback);
        when(dataMapper.deliverySetDefault(eq(10001L), eq(30002L), any())).thenReturn(1);

        DeliveryAddressDeleteVO result = service.deliveryDeleteAddress(30001L);

        assertEquals(30001L, result.getId());
        verify(dataMapper).deliverySetDefault(eq(10001L), eq(30002L), any());
    }

    /** 验证跨账号访问有效地址返回禁止访问。 */
    @Test
    void deliveryResolveOrderAddressRejectsAnotherUsersAddress() {
        DeliveryAddressMapper addressMapper = mock(DeliveryAddressMapper.class);
        DeliveryDataMapper dataMapper = mock(DeliveryDataMapper.class);
        DeliveryServiceImpl service = new DeliveryServiceImpl(addressMapper, dataMapper);
        context();
        when(dataMapper.deliverySelectAddress(30001L)).thenReturn(addressForUser(30001L, 10002L, false));

        CAuthException exception = assertThrows(CAuthException.class,
                () -> service.deliveryResolveOrderAddress(30001L, null));

        assertEquals("A0301", exception.getCode());
    }

    /** 创建新增地址请求。 */
    private DeliveryAddressCreateRequest createRequest() {
        DeliveryAddressCreateRequest request = new DeliveryAddressCreateRequest();
        request.setReceiverName("张三");
        request.setReceiverPhone("13800138000");
        request.setProvince("HENAN");
        request.setCity("郑州市");
        request.setDetailAddress("中心路1号");
        return request;
    }

    /** 创建当前用户地址实体。 */
    private DeliveryAddress address(Long id, boolean isDefault) {
        return addressForUser(id, 10001L, isDefault);
    }

    /** 创建指定用户地址实体。 */
    private DeliveryAddress addressForUser(Long id, Long userId, boolean isDefault) {
        DeliveryAddress address = new DeliveryAddress();
        address.setId(id);
        address.setUserId(userId);
        address.setReceiverName("张三");
        address.setReceiverPhone("13800138000");
        address.setProvince("HENAN");
        address.setCity("郑州市");
        address.setDetailAddress("中心路1号");
        address.setIsDefault(isDefault);
        return address;
    }

    /** 设置当前登录用户上下文。 */
    private void context() {
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
    }
}
