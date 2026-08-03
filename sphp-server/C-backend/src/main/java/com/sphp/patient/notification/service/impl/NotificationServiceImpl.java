package com.sphp.patient.notification.service.impl;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.common.constant.NotificationConstant;
import com.sphp.patient.notification.mapper.NotificationMapper;
import com.sphp.patient.notification.mapper.NotificationRecord;
import com.sphp.patient.notification.service.NotificationService;
import com.sphp.patient.notification.vo.NotificationPageVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import java.util.List;

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
     * @param pageNo 页号
     * @param pageSize 页大小
     * @return 通知分页数据
     * @throws CAuthException 就诊人不存在、无归属权限或分页参数超出范围时抛出
     */
    @Override
    public NotificationPageVO listNotifications(Long patientId, Boolean read, Integer pageNo, Integer pageSize) {
        Long userId = CUserContext.getRequired().userId();
        validateAccessiblePatient(userId, patientId);
        int resolvedPageNo = pageNo == null ? NotificationConstant.DEFAULT_PAGE_NO : pageNo;
        int resolvedPageSize = pageSize == null ? NotificationConstant.DEFAULT_PAGE_SIZE : pageSize;
        if (resolvedPageNo < 1 || resolvedPageSize < 1 || resolvedPageSize > NotificationConstant.MAX_PAGE_SIZE) {
            throw new CAuthException(ErrorCodeEnum.PARAMETER_OUT_OF_RANGE, HttpStatus.BAD_REQUEST, "分页参数超出允许范围");
        }
        List<NotificationPageVO.Item> records = notificationMapper.selectNotifications(userId, patientId, read,
                        resolvedPageSize, (long) (resolvedPageNo - 1) * resolvedPageSize)
                .stream().map(this::toPageItem).toList();
        return NotificationPageVO.builder()
                .pageNo(resolvedPageNo)
                .pageSize(resolvedPageSize)
                .total(notificationMapper.countNotifications(userId, patientId, read))
                .records(records)
                .build();
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
            throw new CAuthException(ErrorCodeEnum.INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "就诊人不存在或已停用");
        }
        if (!notificationMapper.hasActivePatientRelation(userId, patientId)) {
            throw new CAuthException(ErrorCodeEnum.UNAUTHORIZED, HttpStatus.FORBIDDEN, "无权访问该就诊人");
        }
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

}
