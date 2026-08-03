package com.sphp.patient.notification.controller;

import com.sphp.patient.notification.service.NotificationService;
import com.sphp.patient.notification.vo.NotificationPageVO;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Positive;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * C端站内通知接口。
 */
@RestController
@Validated
@RequestMapping("/c/v1/notifications")
@Tag(name = "C端站内通知", description = "查询通知并标记已读")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    /**
     * 分页查询当前账号通知。
     *
     * @param patientId 可选就诊人 ID
     * @param read 可选已读状态
     * @param pageNo 页号
     * @param pageSize 页大小
     * @return 通知分页数据
     */
    @GetMapping
    @Operation(summary = "查询通知")
    public Result<NotificationPageVO> listNotifications(
            @RequestParam(required = false) @Positive(message = "就诊人ID必须为正整数") Long patientId,
            @RequestParam(required = false) Boolean read,
            @RequestParam(required = false) Integer pageNo,
            @RequestParam(required = false) Integer pageSize) {
        validateListParameters(patientId, pageNo, pageSize);
        return Result.success("查询成功", notificationService.listNotifications(patientId, read, pageNo, pageSize));
    }

    /**
     * 校验通知查询的正数分页参数，保证独立调用与 Web 参数校验行为一致。
     *
     * @param patientId 可选就诊人 ID
     * @param pageNo 可选页号
     * @param pageSize 可选页大小
     * @throws CAuthException 参数非正数时抛出
     */
    private void validateListParameters(Long patientId, Integer pageNo, Integer pageSize) {
        if ((patientId != null && patientId < 1) || (pageNo != null && pageNo < 1) || (pageSize != null && pageSize < 1)) {
            throw new CAuthException(ErrorCodeEnum.INVALID_PARAMETER, HttpStatus.BAD_REQUEST, "分页参数或就诊人ID必须为正整数");
        }
    }
}
