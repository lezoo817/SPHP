package com.sphp.admin.doctor.service;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.doctor.dto.AllergyCreateRequest;
import com.sphp.admin.doctor.dto.ConsultEndVO;
import com.sphp.admin.doctor.dto.ConsultStartVO;
import com.sphp.admin.doctor.dto.MessageVO;
import com.sphp.admin.doctor.dto.NoteSaveVO;
import com.sphp.admin.doctor.dto.PatientDetailVO;
import com.sphp.admin.doctor.dto.ConsultHistoryDetailVO;
import com.sphp.admin.doctor.dto.ConsultHistoryVO;
import com.sphp.admin.doctor.dto.QueueItemVO;
import com.sphp.admin.doctor.dto.OnlineConsultationDetailVO;
import com.sphp.admin.doctor.dto.OnlineConsultationItemVO;
import com.sphp.admin.doctor.dto.OnlineConsultationMessageSendRequest;
import com.sphp.admin.doctor.dto.OnlineConsultationMessagePageVO;

/**
 * 接诊台服务接口。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
public interface DoctorConsultService {

    /**
     * 分页查询无挂号在线问诊。
     *
     * @param status 状态筛选
     * @param page 页码
     * @param size 每页大小
     * @return 在线问诊分页结果
     */
    PageResult<OnlineConsultationItemVO> pageOnlineConsultations(String status, int page, int size);

    /**
     * 查询无挂号在线问诊详情。
     *
     * @param consultId 问诊记录 ID
     * @return 在线问诊详情
     */
    OnlineConsultationDetailVO getOnlineConsultationDetail(Long consultId);

    /**
     * 开始在线问诊。
     *
     * @param consultId 问诊记录 ID
     * @return 状态变更结果
     */
    ConsultStartVO startOnlineConsult(Long consultId);

    /**
     * 发送在线问诊医生文字消息。
     *
     * @param consultId 问诊记录 ID
     * @param request 消息内容和客户端幂等标识
     * @return 已保存消息
     */
    MessageVO sendOnlineConsultationMessage(Long consultId, OnlineConsultationMessageSendRequest request);

    /**
     * 结束在线问诊。
     *
     * @param consultId 问诊记录 ID
     * @return 结束结果
     */
    ConsultEndVO endOnlineConsult(Long consultId);

    /**
     * 游标查询在线问诊消息。
     *
     * @param consultId 问诊记录 ID
     * @param afterId 向后补拉游标
     * @param beforeId 向前加载游标
     * @param size 每页数量
     * @return 消息游标分页结果
     */
    OnlineConsultationMessagePageVO pageOnlineConsultationMessages(Long consultId, Long afterId, Long beforeId, int size);

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
     * 接诊台补录患者过敏史。
     *
     * <p>共享患者档案，任一医生在接诊范围内即可补录；先校验问诊归属
     * （复用 {@code getConsultInScope} 的医院/医生/科室隔离），再写入
     * patient_allergy。保存后该过敏原立即参与处方风险拦截。
     *
     * @param consultId 问诊记录 ID
     * @param request   过敏原/反应/严重程度
     * @return 新增过敏史记录 ID
     * @throws com.sphp.shared.exception.BusinessException 问诊不存在/越权/参数非法
     */
    Long addPatientAllergy(Long consultId, AllergyCreateRequest request);

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
