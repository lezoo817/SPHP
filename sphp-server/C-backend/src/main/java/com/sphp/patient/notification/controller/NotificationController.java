package com.sphp.patient.notification.controller;

import com.sphp.patient.notification.service.NotificationService;
import com.sphp.patient.notification.vo.NotificationPageVO;
import com.sphp.patient.notification.vo.NotificationReadVO;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.common.constant.NotificationConstant;
import com.sphp.patient.common.enums.NotificationTypeEnum;
import com.sphp.patient.support.idempotency.CIdempotencyService;
import com.sphp.patient.support.idempotency.IdempotencyPayload;
import com.sphp.shared.common.constant.HeaderConstant;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import static com.sphp.patient.common.constant.NotificationConstant.READ_PATH_PREFIX;
import static com.sphp.shared.common.constant.HeaderConstant.IDEMPOTENCY_KEY;
import static com.sphp.shared.common.enums.ErrorCodeEnum.INVALID_PARAMETER;

/**
 * C端站内通知接口。
 */
@RestController
@Validated
@RequestMapping("/c/v1/notifications")
@Tag(name = "C端站内通知", description = "查询通知并标记已读")
@RequiredArgsConstructor
public class NotificationController {
    // 通知服务
    private final NotificationService notificationService;
    // 幂等服务
    private final CIdempotencyService idempotencyService;

    /**
     * 分页查询当前账号通知。
     *
     * @param patientId 可选就诊人 ID
     * @param read 可选已读状态
     * @param type 可选通知类型
     * @param pageNo 页号
     * @param pageSize 页大小
     * @return 通知分页数据
     */
    @GetMapping
    @Operation(summary = "查询通知")
    public Result<NotificationPageVO> listNotifications(
            @RequestParam(required = false) @Positive(message = "就诊人ID必须为正整数") Long patientId,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) String type,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize) {
        validateListParameters(patientId, type, pageNo, pageSize);
        return Result.success("查询成功", notificationService.listNotifications(patientId, read, type, pageNo, pageSize));
    }

    /**
     * 校验通知查询的正数分页参数，保证独立调用与 Web 参数校验行为一致。
     *
     * @param patientId 可选就诊人 ID
     * @param type 可选通知类型
     * @param pageNo 可选页号
     * @param pageSize 可选页大小
     * @throws CAuthException 参数非正数时抛出
     */
    private void validateListParameters(Long patientId, String type, Integer pageNo, Integer pageSize) {
        if ((patientId != null && patientId < 1) || (pageNo != null && pageNo < 1) || (pageSize != null && pageSize < 1)) {
            throw new CAuthException(INVALID_PARAMETER, HttpStatus.BAD_REQUEST, "分页参数或就诊人ID必须为正整数");
        }
        if (type != null && !type.isBlank()) {
            try {
                NotificationTypeEnum.valueOf(type);
            } catch (IllegalArgumentException exception) {
                throw new CAuthException(INVALID_PARAMETER, HttpStatus.BAD_REQUEST, "通知类型不在允许范围内");
            }
        }
    }

    /**
     * 标记当前账号的一条通知已读。
     *
     * @param notificationId 通知 ID
     * @param idempotencyKey 客户端幂等键
     * @return 已读结果
     */
    @PostMapping("/{notificationId}/read")
    @Operation(summary = "标记通知已读")
    public Result<NotificationReadVO> markNotificationRead(
            @PathVariable @Positive(message = "通知ID必须为正整数") Long notificationId,
            @RequestHeader(IDEMPOTENCY_KEY) @NotBlank(message = "幂等键不能为空") String idempotencyKey) {
        Long userId = CUserContext.getRequired().userId();
        // 幂等
        IdempotencyPayload<NotificationReadVO> payload = idempotencyService.execute(
                userId,
                READ_PATH_PREFIX + notificationId + "/read",
                idempotencyKey,
                notificationId,
                NotificationReadVO.class,
                () -> new IdempotencyPayload<>("通知已标记为已读", notificationService.markNotificationRead(notificationId))
        );
        return Result.success(payload.message(), payload.data());
    }
}
