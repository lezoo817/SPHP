package com.sphp.patient.order.controller;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.order.dto.DrugOrderCreateRequest;
import com.sphp.patient.order.service.OrderService;
import com.sphp.patient.order.vo.DrugOrderCancelVO;
import com.sphp.patient.order.vo.DrugOrderCreateVO;
import com.sphp.patient.order.vo.DrugOrderDetailVO;
import com.sphp.patient.order.vo.DrugOrderPageVO;
import com.sphp.patient.order.vo.DrugOrderReceiptVO;
import com.sphp.patient.order.vo.DrugOrderReminderActivationVO;
import com.sphp.patient.order.vo.PharmacyInventoryVO;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import java.util.List;

import static com.sphp.shared.common.constant.HeaderConstant.IDEMPOTENCY_KEY;

/** C端药房库存与购药订单接口。 */
@RestController @Validated @RequestMapping("/c/v1") @Tag(name = "C端购药", description = "药房库存、购药订单与物流") @RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    // 幂等性服务
    private final CIdempotencyService idempotencyService;

    /**
     * 查询药店和处方药库存。
     * @param patientId 就诊人 ID，可不传以查询本人
     * @param prescriptionId 处方 ID
     * @return 处方药库存列表
     */
    @GetMapping("/pharmacies/inventory")
    @Operation(summary = "查询药店和处方药库存")
    public Result<List<PharmacyInventoryVO>> listPharmacyInventory(@RequestParam(required = false) @Positive Long patientId,
                                                                     @RequestParam @Positive Long prescriptionId) {
        return Result.success("查询成功", orderService.listPharmacyInventory(patientId, prescriptionId));
    }

    /**
     * 创建购药订单。
     * @param idempotencyKey 请求幂等性标识
     * @param request 购药订单创建请求
     * @return 购药订单创建结果
     */
    @PostMapping("/drug-orders")
    @Operation(summary = "创建购药订单")
    public Result<DrugOrderCreateVO> createDrugOrder(@RequestHeader(IDEMPOTENCY_KEY) @NotBlank String idempotencyKey,
                                                      @Valid @RequestBody DrugOrderCreateRequest request) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<DrugOrderCreateVO> payload = idempotencyService.execute(userId, "/c/v1/drug-orders", idempotencyKey, request, DrugOrderCreateVO.class,
                () -> new IdempotencyPayload<>("购药订单已创建，请在15分钟内完成支付", orderService.createDrugOrder(request)));
        return Result.success(payload.message(), payload.data());
    }
    /**
     * 分页查询购药订单。
     *
     * @param patientId 就诊人 ID，可不传以查询本人
     * @param status 订单状态筛选条件
     * @param logisticsStatus 物流状态筛选条件
     * @param keyword 订单名称模糊查询关键词
     * @param pageNo 页码
     * @param pageSize 每页条数
     * @return 当前就诊人的订单分页结果
     */
    @GetMapping("/drug-orders")
    @Operation(summary = "查询购药订单列表")
    public Result<DrugOrderPageVO> listDrugOrders(@RequestParam(required = false) @Positive Long patientId,
                                                    @RequestParam(required = false) String status, @RequestParam(required = false) String logisticsStatus,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) @Positive Integer pageNo,
                                                  @RequestParam(required = false) @Positive Integer pageSize) {
        return Result.success("查询成功", orderService.listDrugOrders(patientId, status, logisticsStatus, keyword, pageNo, pageSize));
    }

    /**
     * 查询购药订单详情。
     * @param drugOrderId 购药订单 ID
     * @return 购药订单详情
     */
    @GetMapping("/drug-orders/{drugOrderId}")
    @Operation(summary = "查询购药订单详情")
    public Result<DrugOrderDetailVO> getDrugOrderDetail(@PathVariable @Positive Long drugOrderId) {
        return Result.success("查询成功", orderService.getDrugOrderDetail(drugOrderId));
    }

    /**
     * 取消购药订单。
     * @param drugOrderId 购药订单 ID
     * @param idempotencyKey 请求幂等性标识
     * @return 取消购药订单结果
     */
    @PostMapping("/drug-orders/{drugOrderId}/cancel") @Operation(summary = "取消购药订单")
    public Result<DrugOrderCancelVO> cancelDrugOrder(@PathVariable @Positive Long drugOrderId, @RequestHeader(IDEMPOTENCY_KEY) @NotBlank String idempotencyKey) {
        Long userId=CUserContext.getRequired().userId();
        IdempotencyPayload<DrugOrderCancelVO> payload=idempotencyService
                .execute(userId,
                        "/c/v1/drug-orders/"+drugOrderId+"/cancel",
                        idempotencyKey,
                        drugOrderId,
                        DrugOrderCancelVO.class,
                        ()->new IdempotencyPayload<>("购药订单已取消",
                                orderService.cancelDrugOrder(drugOrderId)));  // 执行取消购药订单
        return Result.success(payload.message(),payload.data());
    }

    /**
     * 确认购药订单收货。
     * @param drugOrderId 购药订单 ID
     * @param idempotencyKey 请求幂等性标识
     * @return 确认购药订单收货结果
     */
    @PostMapping("/drug-orders/{drugOrderId}/confirm-receipt")
    @Operation(summary = "确认购药订单收货")
    public Result<DrugOrderReceiptVO> confirmReceipt(@PathVariable @Positive Long drugOrderId, @RequestHeader(IDEMPOTENCY_KEY) @NotBlank String idempotencyKey) {
        Long userId=CUserContext.getRequired().userId();
        IdempotencyPayload<DrugOrderReceiptVO> payload=idempotencyService
                .execute(userId,
                        "/c/v1/drug-orders/"+drugOrderId+"/confirm-receipt",
                        idempotencyKey,
                        drugOrderId,
                        DrugOrderReceiptVO.class,
                        ()->new IdempotencyPayload<>("确认收货成功"
                                ,orderService.confirmDrugOrderReceipt(drugOrderId)));
        return Result.success(payload.message(),payload.data());
    }

    /**
     * 登记订单收货后自动开启用药提醒。
     *
     * @param drugOrderId 购药订单 ID
     * @param idempotencyKey 请求幂等性标识
     * @return 自动提醒授权结果
     */
    @PostMapping("/drug-orders/{drugOrderId}/reminder-after-receipt")
    @Operation(summary = "登记收货后自动开启用药提醒")
    public Result<DrugOrderReminderActivationVO> authorizeDrugOrderReminderAfterReceipt(
            @PathVariable @Positive Long drugOrderId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank String idempotencyKey) {
        Long userId = CUserContext.getRequired().userId();
        IdempotencyPayload<DrugOrderReminderActivationVO> payload = idempotencyService.execute(
                userId,
                "/c/v1/drug-orders/" + drugOrderId + "/reminder-after-receipt",
                idempotencyKey,
                drugOrderId,
                DrugOrderReminderActivationVO.class,
                () -> {
                    // 根据实际是否已经收货返回准确提示，避免承诺尚未发生的状态。
                    DrugOrderReminderActivationVO result = orderService
                            .authorizeDrugOrderReminderAfterReceipt(drugOrderId);
                    String message = "ACTIVATED".equals(result.getStatus())
                            ? "相关操作已设置，您的用药提醒已开启。"
                            : "相关操作已设置，收货后即可自动开启您的用药提醒。";
                    return new IdempotencyPayload<>(message, result);
                });
        return Result.success(payload.message(), payload.data());
    }
}
