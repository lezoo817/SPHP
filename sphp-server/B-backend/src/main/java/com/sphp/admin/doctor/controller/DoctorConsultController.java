package com.sphp.admin.doctor.controller;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.doctor.dto.ConsultEndVO;
import com.sphp.admin.doctor.dto.ConsultHistoryDetailVO;
import com.sphp.admin.doctor.dto.ConsultHistoryVO;
import com.sphp.admin.doctor.dto.ConsultStartVO;
import com.sphp.admin.doctor.dto.MessageSendRequest;
import com.sphp.admin.doctor.dto.MessageVO;
import com.sphp.admin.doctor.dto.NoteSaveRequest;
import com.sphp.admin.doctor.dto.NoteSaveVO;
import com.sphp.admin.doctor.dto.PatientDetailVO;
import com.sphp.admin.doctor.dto.QueueItemVO;
import com.sphp.admin.doctor.service.DoctorConsultService;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 接诊台控制器（系分 §5.5）。
 *
 * <p>外部完整 URL 前缀为 {@code /api/b/doctor/...}。
 * 包含待接诊队列、患者详情、开始/结束接诊、病历保存、消息查询与发送。
 */
@RestController
@RequestMapping("/b/doctor")
@Tag(name = "4-接诊台", description = "待接诊队列/患者详情/接诊/病历/消息")
@RequiredArgsConstructor
public class DoctorConsultController {

    private static final int MAX_PAGE_SIZE = 100;

    private static int clampSize(int size) {
        return Math.max(1, Math.min(size, MAX_PAGE_SIZE));
    }

    private final DoctorConsultService doctorConsultService;

    // ==================== 5.5.1 待接诊列表 ====================

    @GetMapping("/queue")
    @Operation(summary = "待接诊列表", description = "分页查询待接诊队列（按当前用户数据权限过滤）")
    public Result<PageResult<QueueItemVO>> pageQueue(
            @Parameter(description = "科室过滤") @RequestParam(required = false) Long deptId,
            @Parameter(description = "状态：PENDING / IN_PROGRESS，默认 PENDING") @RequestParam(defaultValue = "PENDING") String status,
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") int size) {
        return Result.success("查询成功",
                doctorConsultService.pageQueue(deptId, status, page, clampSize(size)));
    }

    // ==================== 5.5.2 患者详情 ====================

    @GetMapping("/queue/{id}")
    @Operation(summary = "患者详情", description = "查询患者基本信息、过敏史、既往史、AI摘要、近期处方、历史就诊记录")
    public Result<PatientDetailVO> getPatientDetail(@PathVariable Long id) {
        return Result.success("查询成功", doctorConsultService.getPatientDetail(id));
    }

    // ==================== 5.5.3 开始接诊 ====================

    @PostMapping("/consult/{id}/start")
    @Operation(summary = "开始接诊", description = "将问诊状态从 PENDING 改为 IN_PROGRESS；校验该医生当前无其他 IN_PROGRESS 接诊")
    public Result<ConsultStartVO> startConsult(@PathVariable Long id) {
        return Result.success("开始接诊", doctorConsultService.startConsult(id));
    }

    // ==================== 5.5.4 结束问诊 ====================

    @PostMapping("/consult/{id}/end")
    @Operation(summary = "结束问诊", description = "将问诊状态从 IN_PROGRESS 改为 COMPLETED；校验无未签名的处方草稿")
    public Result<ConsultEndVO> endConsult(@PathVariable Long id) {
        return Result.success("结束问诊", doctorConsultService.endConsult(id));
    }

    // ==================== 5.5.5 保存病历 ====================

    @PutMapping("/consult/{id}/note")
    @Operation(summary = "保存病历", description = "保存医生病历文本（手动 / Agent 双入口共用）")
    public Result<NoteSaveVO> saveNote(@PathVariable Long id,
                                        @Valid @RequestBody NoteSaveRequest request) {
        return Result.success("保存成功", doctorConsultService.saveNote(id, request.getDoctorNote()));
    }

    // ==================== 5.5.6 查询消息历史 ====================

    @GetMapping("/consult/{consultationId}/messages")
    @Operation(summary = "查询问诊消息历史", description = "分页查询问诊消息（按创建时间升序）")
    public Result<PageResult<MessageVO>> pageMessages(
            @PathVariable Long consultationId,
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认50") @RequestParam(defaultValue = "50") int size) {
        return Result.success("查询成功",
                doctorConsultService.pageMessages(consultationId, page, clampSize(size)));
    }

    // ==================== 5.5.7 发送消息 ====================

    @PostMapping("/consult/{consultationId}/message")
    @Operation(summary = "发送问诊消息", description = "医生发送问诊消息（仅 IN_PROGRESS 状态可发送）")
    public Result<MessageVO> sendMessage(@PathVariable Long consultationId,
                                          @Valid @RequestBody MessageSendRequest request) {
        return Result.success("发送成功", doctorConsultService.sendMessage(consultationId, request.getContent()));
    }

    // ==================== 接诊历史 ====================

    @GetMapping("/consult/history")
    @Operation(summary = "接诊历史", description = "分页查询当前医生的历史接诊记录（不含 PENDING）")
    public Result<PageResult<ConsultHistoryVO>> history(
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认10") @RequestParam(defaultValue = "10") int size) {
        return Result.success("查询成功",
                doctorConsultService.pageHistory(page, clampSize(size)));
    }

    @GetMapping("/consult/{id}/history-detail")
    @Operation(summary = "历史接诊详情", description = "查询历史接诊的病历全文和关联处方")
    public Result<ConsultHistoryDetailVO> getHistoryDetail(@PathVariable Long id) {
        return Result.success("查询成功", doctorConsultService.getHistoryDetail(id));
    }
}