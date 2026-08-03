package com.sphp.patient.order.controller;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.order.dto.DrugOrderCreateRequest;
import com.sphp.patient.order.service.OrderService;
import com.sphp.patient.order.vo.DrugOrderCancelVO;
import com.sphp.patient.order.vo.DrugOrderCreateVO;
import com.sphp.patient.order.vo.DrugOrderDetailVO;
import com.sphp.patient.order.vo.DrugOrderPageVO;
import com.sphp.patient.order.vo.DrugOrderReceiptVO;
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

/** C端药房库存与购药订单接口。 */
@RestController @Validated @RequestMapping("/c/v1") @Tag(name = "C端购药", description = "药房库存、购药订单与物流") @RequiredArgsConstructor
public class OrderController {
    private final OrderService orderService;
    private final CIdempotencyService idempotencyService;

    /** 查询已批准处方可购买的院内药房库存。 */
    @GetMapping("/pharmacies/inventory") @Operation(summary = "查询药店和处方药库存")
    public Result<List<PharmacyInventoryVO>> listPharmacyInventory(@RequestParam(required = false) @Positive Long patientId,
                                                                     @RequestParam @Positive Long prescriptionId) {
        return Result.success("查询成功", orderService.listPharmacyInventory(patientId, prescriptionId));
    }
    /** 创建待支付购药订单。 */
    @PostMapping("/drug-orders") @Operation(summary = "创建购药订单")
    public Result<DrugOrderCreateVO> createDrugOrder(@RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank String idempotencyKey,
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
    @GetMapping("/drug-orders") @Operation(summary = "查询购药订单列表")
    public Result<DrugOrderPageVO> listDrugOrders(@RequestParam(required = false) @Positive Long patientId,
                                                    @RequestParam(required = false) String status, @RequestParam(required = false) String logisticsStatus,
                                                    @RequestParam(required = false) String keyword,
                                                    @RequestParam(required = false) @Positive Integer pageNo, @RequestParam(required = false) @Positive Integer pageSize) {
        return Result.success("查询成功", orderService.listDrugOrders(patientId, status, logisticsStatus, keyword, pageNo, pageSize));
    }
    /** 查询购药订单详情。 */
    @GetMapping("/drug-orders/{drugOrderId}") @Operation(summary = "查询购药订单详情")
    public Result<DrugOrderDetailVO> getDrugOrderDetail(@PathVariable @Positive Long drugOrderId) { return Result.success("查询成功", orderService.getDrugOrderDetail(drugOrderId)); }
    /** 取消待支付购药订单。 */
    @PostMapping("/drug-orders/{drugOrderId}/cancel") @Operation(summary = "取消购药订单")
    public Result<DrugOrderCancelVO> cancelDrugOrder(@PathVariable @Positive Long drugOrderId, @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank String idempotencyKey) {
        Long userId=CUserContext.getRequired().userId(); IdempotencyPayload<DrugOrderCancelVO> payload=idempotencyService.execute(userId,"/c/v1/drug-orders/"+drugOrderId+"/cancel",idempotencyKey,drugOrderId,DrugOrderCancelVO.class,()->new IdempotencyPayload<>("购药订单已取消",orderService.cancelDrugOrder(drugOrderId))); return Result.success(payload.message(),payload.data());
    }
    /** 确认购药订单收货。 */
    @PostMapping("/drug-orders/{drugOrderId}/confirm-receipt") @Operation(summary = "确认购药订单收货")
    public Result<DrugOrderReceiptVO> confirmReceipt(@PathVariable @Positive Long drugOrderId, @RequestHeader(HeaderConstant.IDEMPOTENCY_KEY) @NotBlank String idempotencyKey) {
        Long userId=CUserContext.getRequired().userId(); IdempotencyPayload<DrugOrderReceiptVO> payload=idempotencyService.execute(userId,"/c/v1/drug-orders/"+drugOrderId+"/confirm-receipt",idempotencyKey,drugOrderId,DrugOrderReceiptVO.class,()->new IdempotencyPayload<>("确认收货成功",orderService.confirmDrugOrderReceipt(drugOrderId))); return Result.success(payload.message(),payload.data());
    }
}
