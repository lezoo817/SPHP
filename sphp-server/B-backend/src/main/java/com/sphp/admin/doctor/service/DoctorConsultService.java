package com.sphp.admin.doctor.service;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.doctor.dto.ConsultEndVO;
import com.sphp.admin.doctor.dto.ConsultStartVO;
import com.sphp.admin.doctor.dto.MessageVO;
import com.sphp.admin.doctor.dto.NoteSaveVO;
import com.sphp.admin.doctor.dto.PatientDetailVO;
import com.sphp.admin.doctor.dto.ConsultHistoryDetailVO;
import com.sphp.admin.doctor.dto.ConsultHistoryVO;
import com.sphp.admin.doctor.dto.QueueItemVO;

/**
 * 接诊台服务接口。
 */
public interface DoctorConsultService {

    /**
     * 分页查询待接诊列表。
     *
     * @param deptId  科室过滤（仅 ADMIN 生效）
     * @param status  问诊状态（PENDING / IN_PROGRESS），为空默认 PENDING
     * @param page    页码（从 1 开始）
     * @param size    每页大小
     * @return 待接诊列表分页结果
     */
    PageResult<QueueItemVO> pageQueue(Long deptId, String status, int page, int size);

    /**
     * 查询患者详情（含过敏史、既往史、AI 摘要、近期处方、历史就诊记录）。
     *
     * @param consultId 问诊记录 ID
     * @return 患者详情聚合 VO
     */
    PatientDetailVO getPatientDetail(Long consultId);

    /**
     * 开始接诊。
     *
     * <p>校验链路：问诊存在 + 状态为 PENDING + 当前医生无未结束接诊 + 当前时间在号源时段内。
     *
     * @param consultId 问诊记录 ID
     * @return 开始接诊结果（含状态与开始时间）
     */
    ConsultStartVO startConsult(Long consultId);

    /**
     * 结束问诊。
     *
     * <p>校验链路：问诊存在 + 状态为 IN_PROGRESS + 无未签名的 DRAFT 处方。
     *
     * @param consultId 问诊记录 ID
     * @return 结束问诊结果（含状态与结束时间）
     */
    ConsultEndVO endConsult(Long consultId);

    /**
     * 保存病历记录。
     *
     * @param consultId  问诊记录 ID
     * @param doctorNote 病历文本（结构化 JSON，含主诉/现病史/查体/诊断/治疗方案）
     * @return 保存结果（含更新时间）
     */
    NoteSaveVO saveNote(Long consultId, String doctorNote);

    /**
     * 分页查询问诊消息历史（按创建时间升序）。
     *
     * @param consultationId 问诊记录 ID
     * @param page           页码（从 1 开始）
     * @param size           每页大小
     * @return 消息列表分页结果
     */
    PageResult<MessageVO> pageMessages(Long consultationId, int page, int size);

    /**
     * 发送问诊消息（仅 IN_PROGRESS 状态可发送）。
     *
     * @param consultationId 问诊记录 ID
     * @param content        消息内容
     * @return 已发送消息 VO
     */
    MessageVO sendMessage(Long consultationId, String content);

    /**
     * 分页查询本医院接诊历史。
     *
     * <p>按当前用户所属医院过滤（ADMIN/DEPT_HEAD/DOCTOR 均可见本医院全部接诊历史，
     * 便于跨医生协同查看患者在他处的就诊记录）。
     *
     * @param page 页码（从 1 开始）
     * @param size 每页大小
     * @return 历史接诊分页结果（不含 PENDING）
     */
    PageResult<ConsultHistoryVO> pageHistory(int page, int size);

    /**
     * 查询历史接诊详情（含病历全文、关联处方）。
     *
     * @param consultId 问诊记录 ID
     * @return 历史接诊详情 VO
     */
    ConsultHistoryDetailVO getHistoryDetail(Long consultId);
}
