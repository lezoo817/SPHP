package com.sphp.patient.consultation.service.impl;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.common.constant.PrescriptionConstant;
import com.sphp.patient.common.enums.ConsultationPrescriptionStatusEnum;
import com.sphp.patient.common.enums.PrescriptionInterpretationStatusEnum;
import com.sphp.patient.consultation.mapper.PrescriptionDataMapper;
import com.sphp.patient.consultation.mapper.PrescriptionDetailRecord;
import com.sphp.patient.consultation.mapper.PrescriptionInterpretationRecord;
import com.sphp.patient.consultation.mapper.PrescriptionItemRecord;
import com.sphp.patient.consultation.mapper.PrescriptionListRecord;
import com.sphp.patient.consultation.mapper.PrescriptionResourceRecord;
import com.sphp.patient.consultation.service.PrescriptionService;
import com.sphp.patient.consultation.vo.ConsultationPrescriptionDetailVO;
import com.sphp.patient.consultation.vo.ConsultationPrescriptionPageVO;
import com.sphp.patient.consultation.vo.PrescriptionInterpretationVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.OffsetDateTime;
import java.util.List;

import static com.sphp.patient.common.constant.PrescriptionConstant.*;
import static com.sphp.patient.common.enums.ConsultationPrescriptionStatusEnum.APPROVED;
import static com.sphp.patient.common.enums.PrescriptionInterpretationStatusEnum.READY;
import static com.sphp.shared.common.enums.ErrorCodeEnum.*;

/**
 * C端处方查询与解读服务实现。
 */
@Service("cPrescriptionServiceImpl")
@RequiredArgsConstructor
public class PrescriptionServiceImpl implements PrescriptionService {
    // 处方数据访问接口
    private final PrescriptionDataMapper prescriptionDataMapper;

    /**
     * 分页查询当前账号可访问患者的已批准处方。
     *
     * @param patientId 可选就诊人 ID，未传时使用本人
     * @param pageNo 可选页码
     * @param pageSize 可选每页数量
     * @param recentDays 可选最近天数，仅允许 1 至 30 天
     * @return 已批准处方分页结果
     * @throws CAuthException 患者归属或分页参数非法时抛出
     */
    @Override
    public ConsultationPrescriptionPageVO prescriptionList(Long patientId, Integer pageNo, Integer pageSize,
                                                            Integer recentDays) {
        Long resolvedPatientId = prescriptionResolveAccessiblePatient(CUserContext.getRequired().userId(), patientId);
        //若未传页码/大小，则使用默认值
        int resolvedPageNo = pageNo == null ? DEFAULT_PAGE_NO : pageNo;
        int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        if (resolvedPageSize > MAX_PAGE_SIZE) {
            throw prescriptionBadRequest("pageSize 不能超过" + MAX_PAGE_SIZE);
        }
        // 最近记录按服务端当前时间计算下界，避免前端时钟偏差影响筛选结果。
        OffsetDateTime issuedSince = recentDays == null ? null : OffsetDateTime.now().minusDays(recentDays);
        long offset = (long) (resolvedPageNo - 1) * resolvedPageSize;
        List<ConsultationPrescriptionPageVO.Item> records = prescriptionDataMapper
                .prescriptionSelectApprovedList(resolvedPatientId, resolvedPageSize, offset, issuedSince)
                .stream()
                .map(this::prescriptionToListItem)
                .toList();
        return ConsultationPrescriptionPageVO.builder()
                .pageNo(resolvedPageNo)
                .pageSize(resolvedPageSize)
                .total(prescriptionDataMapper.prescriptionCountApprovedList(resolvedPatientId, issuedSince))
                .records(records)
                .build();
    }

    /**
     * 查询当前账号可访问的已批准处方详情与药品明细。
     *
     * @param prescriptionId 处方 ID
     * @return 处方详情
     * @throws CAuthException 处方不存在、不可展示或无权访问时抛出
     */
    @Override
    public ConsultationPrescriptionDetailVO prescriptionGetDetail(Long prescriptionId) {
        // 读取处方资源并校验当前用户对所属患者的访问权限和处方展示状态
        PrescriptionResourceRecord resource = prescriptionRequireApprovedResource(prescriptionId);
        // 读取处方详情
        PrescriptionDetailRecord detail = prescriptionDataMapper.prescriptionSelectApprovedDetail(prescriptionId);
        if (detail == null) {
            throw prescriptionNotFound("处方不存在");
        }
        List<ConsultationPrescriptionDetailVO.Item> items = prescriptionDataMapper.prescriptionSelectItems(prescriptionId)
                .stream()
                .map(this::prescriptionToDetailItem) // 转换成详情项
                .toList();
        return ConsultationPrescriptionDetailVO.builder()
                .id(resource.id())
                .status(APPROVED.name())
                .doctorName(detail.doctorName())
                .doctor(ConsultationPrescriptionDetailVO.Doctor.builder()
                        .id(detail.doctorId())
                        .name(detail.doctorName()) //
                        .title(detail.doctorTitle()) // 医生职称
                        .build())
                .items(items) // 药品明细
                .build();
    }

    /**
     * 查询已生成的处方解读；未就绪的解读不向患者端暴露。
     *
     * @param prescriptionId 处方 ID
     * @return READY 状态处方解读
     * @throws CAuthException 处方不可访问或解读未就绪时抛出
     */
    @Override
    public PrescriptionInterpretationVO prescriptionGetInterpretation(Long prescriptionId) {
        // 读取处方资源并校验当前用户对所属患者的访问权限和处方展示状态
        prescriptionRequireApprovedResource(prescriptionId);
        // 读取处方解读
        PrescriptionInterpretationRecord interpretation = prescriptionDataMapper
                .prescriptionSelectInterpretation(prescriptionId);
        // 未就绪
        if (interpretation == null || !READY.name().equals(interpretation.status())
                || interpretation.content() == null || interpretation.content().isBlank()) {
            throw prescriptionInterpretationNotReady(); // 未生成异常
        }
        return PrescriptionInterpretationVO.builder()
                .prescriptionId(interpretation.prescriptionId()) // 处方 ID
                .content(interpretation.content()) // 内容
                .disclaimer(interpretation.disclaimer()) // 免责声明
                .generatedAt(interpretation.generatedAt()) // 生成时间
                .build();
    }

    /**
     * 读取处方资源并校验当前用户对所属患者的访问权限和处方展示状态。
     *
     * @param prescriptionId 处方 ID
     * @return 已批准且可访问的处方资源
     * @throws CAuthException 处方不存在、未批准或无权访问时抛出
     */
    private PrescriptionResourceRecord prescriptionRequireApprovedResource(Long prescriptionId) {
        // 读取处方资源并校验当前用户对所属患者的访问权限和处方展示状态
        PrescriptionResourceRecord resource = prescriptionDataMapper.prescriptionSelectResource(prescriptionId);
        if (resource == null) {
            throw prescriptionNotFound("处方不存在");
        }
        // 解析并校验患者归属
        prescriptionResolveAccessiblePatient(CUserContext.getRequired().userId(), resource.patientId());
        if (!APPROVED.name().equals(resource.status())) {
            // 未批准处方对患者端不可见，统一按不存在处理，避免泄漏审核状态。
            throw prescriptionNotFound("处方不存在");
        }
        return resource;
    }

    /**
     * 解析本人或显式就诊人并校验当前用户有效归属。
     *
     * @param userId C端用户 ID
     * @param requestedPatientId 可选就诊人 ID
     * @return 当前用户可访问的就诊人 ID
     * @throws CAuthException 就诊人不存在或无权访问时抛出
     */
    private Long prescriptionResolveAccessiblePatient(Long userId, Long requestedPatientId) {
        // 解析本人或显式就诊人并校验当前用户有效归属
        Long patientId = requestedPatientId == null
                ? prescriptionDataMapper.prescriptionSelectSelfPatientId(userId)
                : requestedPatientId;
        //判断就诊人是否未被软删除
        if (patientId == null || !prescriptionDataMapper.prescriptionExistsActivePatient(patientId)) {
            throw prescriptionNotFound("就诊人不存在或已停用");
        }
        //判断当前用户是否拥有有效的就诊人关系
        if (!prescriptionDataMapper.prescriptionHasActivePatientRelation(userId, patientId)) {
            throw prescriptionForbidden("无权访问该就诊人");
        }
        return patientId;
    }

    /**
     * 将处方列表投影转换为响应项。
     *
     * @param record 处方列表投影
     * @return 处方列表响应项
     */
    private ConsultationPrescriptionPageVO.Item prescriptionToListItem(PrescriptionListRecord record) {
        return ConsultationPrescriptionPageVO.Item.builder()
                .id(record.id())
                .consultationId(record.consultationId())
                .doctorName(record.doctorName())
                .displayName(record.displayName())
                .status(APPROVED.name())
                .issuedAt(record.issuedAt()) // 已批准
                .build();
    }

    /**
     * 将处方药品投影转换为详情响应项。
     *
     * @param record 处方药品投影
     * @return 处方药品详情
     */
    private ConsultationPrescriptionDetailVO.Item prescriptionToDetailItem(PrescriptionItemRecord record) {
        return ConsultationPrescriptionDetailVO.Item.builder()
                .drugId(record.drugId())
                .drugName(record.drugName())
                .specification(record.specification())
                .dosage(record.dosage())
                .frequency(record.frequency())
                .usage(record.usage())
                .durationDays(record.durationDays())
                .build();
    }

    /**
     * 创建资源不存在异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 404 业务异常
     */
    private CAuthException prescriptionNotFound(String message) {
        return new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, message);
    }

    /**
     * 创建患者归属越权异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 403 业务异常
     */
    private CAuthException prescriptionForbidden(String message) {
        return new CAuthException(UNAUTHORIZED, HttpStatus.FORBIDDEN, message);
    }

    /**
     * 创建解读未生成异常。
     *
     * @return HTTP 409 业务异常
     */
    private CAuthException prescriptionInterpretationNotReady() {
        return new CAuthException(BUSINESS_STATUS_CONFLICT, HttpStatus.CONFLICT, "处方解读尚未生成");
    }

    /**
     * 创建参数范围异常。
     *
     * @param message 面向客户端的提示
     * @return HTTP 400 业务异常
     */
    private CAuthException prescriptionBadRequest(String message) {
        return new CAuthException(PARAMETER_OUT_OF_RANGE, HttpStatus.BAD_REQUEST, message);
    }
}
