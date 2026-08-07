package com.sphp.patient.notification.service.impl;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.common.constant.NotificationConstant;
import com.sphp.patient.common.enums.NotificationTypeEnum;
import com.sphp.patient.notification.mapper.NotificationMapper;
import com.sphp.patient.notification.mapper.NotificationRecord;
import com.sphp.patient.notification.service.NotificationService;
import com.sphp.patient.notification.vo.NotificationPageVO;
import com.sphp.patient.notification.vo.NotificationReadVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.OffsetDateTime;
import java.util.List;

import static com.sphp.patient.common.constant.NotificationConstant.*;
import static com.sphp.shared.common.enums.ErrorCodeEnum.*;

/**
 * C端站内通知服务实现。
 */
@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private final NotificationMapper notificationMapper;

    /**
     * 分页查询当前账号通知。
     *
     * @param patientId 可选就诊人 ID
     * @param read 可选已读状态
     * @param type 可选通知类型
     * @param pageNo 页号
     * @param pageSize 页大小
     * @return 通知分页数据
     * @throws CAuthException 就诊人不存在、无归属权限或分页参数超出范围时抛出
     */
    @Override
    public NotificationPageVO listNotifications(Long patientId, Boolean read, String type, Integer pageNo, Integer pageSize) {
        Long userId = CUserContext.getRequired().userId();
        // 校验可选就诊人存在且属于当前账号。
        validateAccessiblePatient(userId, patientId);
        String resolvedType = resolveNotificationType(type);
        int resolvedPageNo = pageNo == null ? DEFAULT_PAGE_NO : pageNo;
        int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (resolvedPageNo < 1 || resolvedPageSize < 1 || resolvedPageSize > MAX_PAGE_SIZE) {
            throw new CAuthException(PARAMETER_OUT_OF_RANGE, HttpStatus.BAD_REQUEST, "分页参数超出允许范围");
        }
        List<NotificationPageVO.Item> records = notificationMapper.selectNotifications(userId, patientId, read, resolvedType,
                        resolvedPageSize, (long) (resolvedPageNo - 1) * resolvedPageSize)
                .stream().map(this::toPageItem).toList();
        return NotificationPageVO.builder()
                .pageNo(resolvedPageNo)
                .pageSize(resolvedPageSize)
                .total(notificationMapper.countNotifications(userId, patientId, read, resolvedType))
                .records(records)
                .build();
    }

    /**
     * 标记当前账号的一条通知已读。
     *
     * @param notificationId 通知 ID
     * @return 已读结果
     * @throws CAuthException 通知不存在、无归属权限或状态读取异常时抛出
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public NotificationReadVO markNotificationRead(Long notificationId) {
        Long userId = CUserContext.getRequired().userId();
        // 校验通知存在且属于当前账号。
        NotificationRecord notification = requireOwnedNotification(userId, notificationId);
        // 已读时间已存在则返回已读结果。
        if (notification.getReadAt() != null) {
            return toReadVO(notificationId, notification.getReadAt());
        }
        OffsetDateTime now = OffsetDateTime.now();
        // 条件更新确保并发请求仅有一个写入首次已读时间。
        if (notificationMapper.markNotificationRead(notificationId, userId, now) == 1) {
            return toReadVO(notificationId, now);
        }
        // 读取通知已读时间失败则返回已读结果。
        NotificationRecord latest = requireOwnedNotification(userId, notificationId);
        if (latest.getReadAt() == null) {
            throw new CAuthException(BUSINESS_STATUS_CONFLICT, HttpStatus.CONFLICT,
                    "通知状态已发生变化，请刷新后重试");
        }
        return toReadVO(notificationId, latest.getReadAt());
    }

    /**
     * 校验可选就诊人存在且属于当前账号。
     *
     * @param userId 当前 C端用户 ID
     * @param patientId 可选就诊人 ID
     * @throws CAuthException 就诊人不存在或无归属权限时抛出
     */
    private void validateAccessiblePatient(Long userId, Long patientId) {
        if (patientId == null) {
            return;
        }
        if (!notificationMapper.existsActivePatient(patientId)) {
            throw new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "就诊人不存在或已停用");
        }
        if (!notificationMapper.hasActivePatientRelation(userId, patientId)) {
            throw new CAuthException(UNAUTHORIZED, HttpStatus.FORBIDDEN, "无权访问该就诊人");
        }
    }

    /**
     * 校验并规范通知类型筛选条件。
     *
     * @param type 前端传入的可选通知类型
     * @return 合法通知类型；空白条件返回 null
     * @throws CAuthException 类型不在枚举范围内时抛出
     */
    private String resolveNotificationType(String type) {
        if (type == null || type.isBlank()) {
            return null;
        }
        try {
            return NotificationTypeEnum.valueOf(type).name();
        } catch (IllegalArgumentException exception) {
            throw new CAuthException(INVALID_PARAMETER, HttpStatus.BAD_REQUEST, "通知类型不在允许范围内");
        }
    }

    /**
     * 查询通知并校验它归属于当前账号。
     *
     * @param userId 当前 C端用户 ID
     * @param notificationId 通知 ID
     * @return 当前通知投影
     * @throws CAuthException 通知不存在或归属不匹配时抛出
     */
    private NotificationRecord requireOwnedNotification(Long userId, Long notificationId) {
        NotificationRecord notification = notificationMapper.selectNotification(notificationId);
        if (notification == null) {
            throw new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "通知不存在");
        }
        if (!userId.equals(notification.getUserId())) {
            throw new CAuthException(UNAUTHORIZED, HttpStatus.FORBIDDEN, "无权访问该通知");
        }
        return notification;
    }

    /**
     * 转换通知分页记录。
     *
     * @param record 通知查询投影
     * @return 面向 H5 的通知记录
     */
    private NotificationPageVO.Item toPageItem(NotificationRecord record) {
        return NotificationPageVO.Item.builder()
                .id(record.getId())
                .type(record.getType())
                .patientId(record.getPatientId())
                .patientName(record.getPatientName())
                .title(record.getTitle())
                .content(record.getContent())
                .read(record.getReadAt() != null)
                .createdAt(record.getCreatedAt())
                .build();
    }

    /**
     * 组装通知已读响应。
     *
     * @param notificationId 通知 ID
     * @param readAt 首次已读时间
     * @return 已读响应
     */
    private NotificationReadVO toReadVO(Long notificationId, OffsetDateTime readAt) {
        return NotificationReadVO.builder()
                .id(notificationId)
                .read(true)
                .readAt(readAt)
                .build();
    }

}
