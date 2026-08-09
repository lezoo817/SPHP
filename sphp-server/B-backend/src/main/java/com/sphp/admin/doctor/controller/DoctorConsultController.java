package com.sphp.admin.doctor.controller;

import com.sphp.admin.common.vo.PageResult;
import com.sphp.admin.doctor.dto.AllergyCreateRequest;
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
import com.sphp.admin.doctor.dto.OnlineConsultationDetailVO;
import com.sphp.admin.doctor.dto.OnlineConsultationItemVO;
import com.sphp.admin.doctor.dto.OnlineConsultationReplyRequest;
import com.sphp.admin.doctor.dto.OnlineConsultationReplyVO;
import com.sphp.admin.doctor.service.DoctorConsultService;
import com.sphp.admin.pharmacy.dto.DrugListVO;
import com.sphp.admin.pharmacy.service.DrugService;
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
 * 接诊台控制器（管理员视角）。
 *
 * <p>外部完整 URL 前缀为 {@code /api/b/doctor/...}。
 * 数据隔离边界：所有读操作在 Service 层基于当前用户的医院 / 科室 / 医生维度过滤；
 * 写操作（开始 / 结束接诊、保存病历、发送消息）必须先校验归属与状态机。
 * 包含待接诊队列、患者详情、开始/结束接诊、病历保存、消息查询与发送。
 */
@RestController
@RequestMapping("/b/doctor")
@Tag(name = "接诊台", description = "待接诊队列/患者详情/接诊/病历/消息")
@RequiredArgsConstructor
public class DoctorConsultController {

    /** 分页大小上限（与全局一致） */
    private static final int MAX_PAGE_SIZE = 100;

    private static int clampSize(int size) {
        return Math.clamp(size, 1, MAX_PAGE_SIZE);
    }

    private final DoctorConsultService doctorConsultService;
    private final DrugService drugService;

    /**
     * 查询当前医院可用于开方的药品。
     *
     * @param name 药品名称模糊检索条件
     * @param status 药品状态，通常传 ENABLED
     * @param page 页码
     * @param size 每页大小
     * @return 当前医院药品分页结果
     */
    @GetMapping("/drugs")
    @Operation(summary = "查询医生可用药品", description = "按当前用户所属医院查询药品，供接诊开方选择")
    public Result<PageResult<DrugListVO>> pageDoctorDrugs(
            @Parameter(description = "药品名称模糊检索") @RequestParam(required = false) String name,
            @Parameter(description = "药品状态：ENABLED / DISABLED") @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success("查询成功", drugService.page(name, status, page, clampSize(size)));
    }

    /**
     * 分页查询无挂号在线问诊。
     *
     * @param status 状态：PENDING / IN_PROGRESS / COMPLETED
     * @param page 页码
     * @param size 每页大小
     * @return 在线问诊分页结果
     */
    @GetMapping("/online-consultations")
    @Operation(summary = "在线问诊列表", description = "查询当前医生可见的无挂号在线问诊")
    public Result<PageResult<OnlineConsultationItemVO>> pageOnlineConsultations(
            @Parameter(description = "状态：PENDING / IN_PROGRESS / COMPLETED")
            @RequestParam(defaultValue = "PENDING") String status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int size) {
        return Result.success("查询成功", doctorConsultService.pageOnlineConsultations(status, page, clampSize(size)));
    }

    /**
     * 查询在线问诊详情。
     *
     * @param id 问诊记录 ID
     * @return 在线问诊详情
     */
    @GetMapping("/online-consultations/{id}")
    @Operation(summary = "在线问诊详情", description = "查询 AI 预问诊摘要、消息和处方")
    public Result<OnlineConsultationDetailVO> getOnlineConsultationDetail(@PathVariable Long id) {
        return Result.success("查询成功", doctorConsultService.getOnlineConsultationDetail(id));
    }

    /**
     * 开始编辑在线问诊回复。
     *
     * @param id 问诊记录 ID
     * @return 状态变更结果
     */
    @PostMapping("/online-consultations/{id}/start")
    @Operation(summary = "开始回复在线问诊", description = "将 PENDING 状态切换为 IN_PROGRESS")
    public Result<ConsultStartVO> startOnlineConsult(@PathVariable Long id) {
        return Result.success("开始回复", doctorConsultService.startOnlineConsult(id));
    }

    /**
     * 提交医生一次性回复并完成在线问诊。
     *
     * @param id 问诊记录 ID
     * @param request 回复内容
     * @return 回复结果
     */
    @PostMapping("/online-consultations/{id}/reply")
    @Operation(summary = "回复在线问诊", description = "写入一条医生消息并完成在线问诊")
    public Result<OnlineConsultationReplyVO> replyOnlineConsult(
            @PathVariable Long id, @Valid @RequestBody OnlineConsultationReplyRequest request) {
        return Result.success("回复已发送", doctorConsultService.replyOnlineConsult(id, request));
    }

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

    @GetMapping("/queue/{id}")
    @Operation(summary = "患者详情", description = "查询患者基本信息、过敏史、既往史、AI摘要、近期处方、历史就诊记录")
    public Result<PatientDetailVO> getPatientDetail(@PathVariable Long id) {
        return Result.success("查询成功", doctorConsultService.getPatientDetail(id));
    }

    @PostMapping("/consult/{id}/allergy")
    @Operation(summary = "补录患者过敏史", description = "接诊台补充患者过敏原；保存后立即参与处方风险拦截（需问诊归属校验）")
    public Result<Long> addPatientAllergy(@PathVariable Long id,
                                          @Valid @RequestBody AllergyCreateRequest request) {
        return Result.success("过敏史已保存", doctorConsultService.addPatientAllergy(id, request));
    }

    @PostMapping("/consult/{id}/start")
    @Operation(summary = "开始接诊", description = "将问诊状态从 PENDING 改为 IN_PROGRESS；校验该医生当前无其他 IN_PROGRESS 接诊")
    public Result<ConsultStartVO> startConsult(@PathVariable Long id) {
        return Result.success("开始接诊", doctorConsultService.startConsult(id));
    }

    @PostMapping("/consult/{id}/end")
    @Operation(summary = "结束问诊", description = "将问诊状态从 IN_PROGRESS 改为 COMPLETED；校验无未签名的处方草稿")
    public Result<ConsultEndVO> endConsult(@PathVariable Long id) {
        return Result.success("结束问诊", doctorConsultService.endConsult(id));
    }

    @PutMapping("/consult/{id}/note")
    @Operation(summary = "保存病历", description = "保存医生病历文本（手动 / Agent 双入口共用）")
    public Result<NoteSaveVO> saveNote(@PathVariable Long id,
                                        @Valid @RequestBody NoteSaveRequest request) {
        return Result.success("保存成功", doctorConsultService.saveNote(id, request.getDoctorNote()));
    }

    @GetMapping("/consult/{consultationId}/messages")
    @Operation(summary = "查询问诊消息历史", description = "分页查询问诊消息（按创建时间升序）")
    public Result<PageResult<MessageVO>> pageMessages(
            @PathVariable Long consultationId,
            @Parameter(description = "页码，默认1") @RequestParam(defaultValue = "1") int page,
            @Parameter(description = "每页大小，默认50") @RequestParam(defaultValue = "50") int size) {
        return Result.success("查询成功",
                doctorConsultService.pageMessages(consultationId, page, clampSize(size)));
    }

    @PostMapping("/consult/{consultationId}/message")
    @Operation(summary = "发送问诊消息", description = "医生发送问诊消息（仅 IN_PROGRESS 状态可发送）")
    public Result<MessageVO> sendMessage(@PathVariable Long consultationId,
                                          @Valid @RequestBody MessageSendRequest request) {
        return Result.success("发送成功", doctorConsultService.sendMessage(consultationId, request.getContent()));
    }

    @GetMapping("/consult/history")
    @Operation(summary = "接诊历史", description = "分页查询本医院的接诊历史（跨医生，按当前用户所属医院过滤）")
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
