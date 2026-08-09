package com.sphp.admin.prescription.service;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.prescription.dto.AuditRequest;
import com.sphp.admin.prescription.dto.PrescriptionDetailVO;
import com.sphp.admin.prescription.dto.PrescriptionListVO;
import com.sphp.admin.prescription.dto.PrescriptionSubmitRequest;
import com.sphp.admin.prescription.dto.PrescriptionSubmitVO;

import java.util.List;

/**
 * 处方管理服务接口。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
public interface PrescriptionService {

    /**
     * 提交处方。
     *
     * <p>含风险拦截：根据命中级别决定处方状态 —
     * ERROR 禁止提交（不入库），WARNING 处方生效，
     * AUDIT 进入待审核队列，无风险直接 APPROVED。
     *
     * @param request 处方提交请求（含问诊 ID 与药品明细）
     * @return 处方提交结果（含状态、是否需审核、风险警告）
     * @throws com.sphp.shared.exception.BusinessException 命中红线/无权限/资源不存在
     */
    PrescriptionSubmitVO submit(PrescriptionSubmitRequest request);

    /**
     * 由明细直接开方（医生提交、模板应用共用入口）。
     *
     * <p>内部执行：问诊校验（IN_PROGRESS/归属）→ 药品校验 → 风险拦截 → 建单。
     *
     * @param consultId 问诊记录 ID
     * @param items     处方明细
     * @return 处方提交结果
     * @throws com.sphp.shared.exception.BusinessException 问诊无效/越权/药品停用/红线
     */
    PrescriptionSubmitVO createFromItems(Long consultId, List<PrescriptionSubmitRequest.ItemDTO> items);

    /**
     * 分页查询处方列表。
     *
     * @param consultId 问诊 ID 过滤（可选）
     * @param patientId 患者 ID 过滤（可选）
     * @param status    状态多值过滤，逗号分隔（可选，如 "APPROVED,REJECTED"）
     * @param page      页码（从 1 开始）
     * @param size      每页大小
     * @return 处方分页结果（按当前用户数据权限过滤）
     */
    PageResult<PrescriptionListVO> page(Long consultId, Long patientId, String status, int page, int size);

    /**
     * 查询处方详情。
     *
     * @param id 处方 ID
     * @return 处方详情（含医生/患者/明细/风险规则快照）
     * @throws com.sphp.shared.exception.BusinessException 不存在或越权
     */
    PrescriptionDetailVO getDetail(Long id);

    /**
     * 分页查询待审核处方列表。
     *
     * <p>仅 ADMIN / DEPT_HEAD 可访问；DEPT_HEAD 自动收窄到本科室。
     *
     * @param page 页码（从 1 开始）
     * @param size 每页大小
     * @return 当前医院待审核处方分页
     * @throws com.sphp.shared.exception.BusinessException 无审核权限
     */
    PageResult<PrescriptionListVO> pendingAuditList(int page, int size);

    /**
     * 审核处方。
     *
     * <p>仅 ADMIN / DEPT_HEAD 可操作；REJECTED 时必须填写驳回原因。
     *
     * @param id      处方 ID
     * @param request 审核动作（APPROVED / REJECTED）与驳回原因
     * @throws com.sphp.shared.exception.BusinessException 无权限/状态非法/驳回缺原因
     */
    void audit(Long id, AuditRequest request);
}