package com.sphp.admin.prescription.service;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.prescription.dto.AuditRequest;
import com.sphp.admin.prescription.dto.PrescriptionDetailVO;
import com.sphp.admin.prescription.dto.PrescriptionListVO;
import com.sphp.admin.prescription.dto.PrescriptionSubmitRequest;
import com.sphp.admin.prescription.dto.PrescriptionSubmitVO;

/**
 * 处方管理服务接口。
 */
public interface PrescriptionService {

    /**
     * 提交处方（系分 §5.6.1）。
     *
     * <p>含风险拦截：根据命中级别决定处方状态 —
     * ERROR 禁止提交（不入库），WARNING 处方生效，
     * AUDIT 进入待审核队列，无风险直接 APPROVED。
     */
    PrescriptionSubmitVO submit(PrescriptionSubmitRequest request);

    /**
     * 分页查询处方列表（系分 §5.6.2）。
     */
    PageResult<PrescriptionListVO> page(Long consultId, Long patientId, String status, int page, int size);

    /**
     * 查询处方详情（系分 §5.6.3）。
     */
    PrescriptionDetailVO getDetail(Long id);

    /**
     * 分页查询待审核处方列表（系分 §5.6.4）。
     */
    PageResult<PrescriptionListVO> pendingAuditList(int page, int size);

    /**
     * 审核处方（系分 §5.6.5）。
     */
    void audit(Long id, AuditRequest request);
}