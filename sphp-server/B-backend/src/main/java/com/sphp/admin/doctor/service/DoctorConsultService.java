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
     * 分页查询待接诊列表（系分 §5.5.1）。
     */
    PageResult<QueueItemVO> pageQueue(Long deptId, String status, int page, int size);

    /**
     * 查询患者详情（含过敏史、既往史、AI摘要、近期处方、历史就诊记录，系分 §5.5.2）。
     */
    PatientDetailVO getPatientDetail(Long consultId);

    /**
     * 开始接诊（系分 §5.5.3）。
     */
    ConsultStartVO startConsult(Long consultId);

    /**
     * 结束问诊（系分 §5.5.4）。
     */
    ConsultEndVO endConsult(Long consultId);

    /**
     * 保存病历记录（系分 §5.5.5）。
     */
    NoteSaveVO saveNote(Long consultId, String doctorNote);

    /**
     * 分页查询问诊消息历史（系分 §5.5.6）。
     */
    PageResult<MessageVO> pageMessages(Long consultationId, int page, int size);

    /**
     * 发送问诊消息（系分 §5.5.7）。
     */
    MessageVO sendMessage(Long consultationId, String content);

    /**
     * 分页查询当前医生的历史接诊记录。
     */
    PageResult<ConsultHistoryVO> pageHistory(int page, int size);

    /**
     * 查询历史接诊详情（含病历全文、关联处方）。
     */
    ConsultHistoryDetailVO getHistoryDetail(Long consultId);
}