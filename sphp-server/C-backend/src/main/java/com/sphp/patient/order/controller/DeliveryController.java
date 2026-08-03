package com.sphp.patient.order.controller;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.order.dto.DeliveryAddressCreateRequest;
import com.sphp.patient.order.dto.DeliveryAddressUpdateRequest;
import com.sphp.patient.order.service.DeliveryService;
import com.sphp.patient.order.vo.DeliveryAddressDeleteVO;
import com.sphp.patient.order.vo.DeliveryAddressVO;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import com.sphp.shared.common.constant.HeaderConstant;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C端收货地址与模拟配送接口。
 */
@RestController
@Validated
@RequestMapping("/c/v1")
@Tag(name = "C端收货地址", description = "当前登录账号的收货地址簿")
@RequiredArgsConstructor
public class DeliveryController {

    private final DeliveryService deliveryService;
    private final CIdempotencyService idempotencyService;

    /**
     * 查询当前登录账号的有效收货地址。
     *
     * @return 默认地址优先的地址列表
     */
    @GetMapping("/delivery-addresses")
    @Operation(summary = "查询收货地址")
    public Result<List<DeliveryAddressVO>> deliveryListAddresses() {
        return Result.success("查询成功", deliveryService.deliveryListAddresses());
    }

    /**
     * 新增当前登录账号收货地址。
     *
     * @param idempotencyKey 客户端幂等键
     * @param request 新增地址请求
     * @return 新增后的地址
     */
    @PostMapping("/delivery-addresses")
    @Operation(summary = "新增收货地址")
    public Result<DeliveryAddressVO> deliveryCreateAddress(
            @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey,
            @Valid @RequestBody DeliveryAddressCreateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<DeliveryAddressVO> payload = idempotencyService.execute(userId, "/c/v1/delivery-addresses",
                idempotencyKey, request, DeliveryAddressVO.class,
                () -> new IdempotencyPayload<>("收货地址已新增", deliveryService.deliveryCreateAddress(request)));
        return Result.success(payload.message(), payload.data());
    }

    /**
     * 更新当前登录账号收货地址。
     *
     * @param addressId 地址 ID
     * @param idempotencyKey 客户端幂等键
     * @param request 更新地址请求
     * @return 更新后的地址
     */
    @PutMapping("/delivery-addresses/{addressId}")
    @Operation(summary = "更新收货地址")
    public Result<DeliveryAddressVO> deliveryUpdateAddress(@PathVariable @Positive Long addressId,
                                                            @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey,
                                                            @Valid @RequestBody DeliveryAddressUpdateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<DeliveryAddressVO> payload = idempotencyService.execute(userId, "/c/v1/delivery-addresses/" + addressId,
                idempotencyKey, request, DeliveryAddressVO.class,
                () -> new IdempotencyPayload<>("收货地址已更新", deliveryService.deliveryUpdateAddress(addressId, request)));
        return Result.success(payload.message(), payload.data());
    }

    /**
     * 删除当前登录账号收货地址。
     *
     * @param addressId 地址 ID
     * @param idempotencyKey 客户端幂等键
     * @return 删除结果
     */
    @DeleteMapping("/delivery-addresses/{addressId}")
    @Operation(summary = "删除收货地址")
    public Result<DeliveryAddressDeleteVO> deliveryDeleteAddress(@PathVariable @Positive Long addressId,
                                                                  @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<DeliveryAddressDeleteVO> payload = idempotencyService.execute(userId, "/c/v1/delivery-addresses/" + addressId,
                idempotencyKey, addressId, DeliveryAddressDeleteVO.class,
                () -> new IdempotencyPayload<>("收货地址已删除", deliveryService.deliveryDeleteAddress(addressId)));
        return Result.success(payload.message(), payload.data());
    }

    /**
     * 设置当前登录账号默认收货地址。
     *
     * @param addressId 地址 ID
     * @param idempotencyKey 客户端幂等键
     * @return 设置后的默认地址
     */
    @PostMapping("/delivery-addresses/{addressId}/default")
    @Operation(summary = "设置默认收货地址")
    public Result<DeliveryAddressVO> deliverySetDefaultAddress(@PathVariable @Positive Long addressId,
                                                                @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<DeliveryAddressVO> payload = idempotencyService.execute(userId,
                "/c/v1/delivery-addresses/" + addressId + "/default", idempotencyKey, addressId, DeliveryAddressVO.class,
                () -> new IdempotencyPayload<>("默认收货地址已设置", deliveryService.deliverySetDefaultAddress(addressId)));
        return Result.success(payload.message(), payload.data());
    }
}
