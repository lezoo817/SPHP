package com.sphp.patient.consultation.controller;

import com.sphp.patient.consultation.handler.PrescriptionExceptionHandler;
import com.sphp.patient.consultation.service.PrescriptionService;
import com.sphp.patient.consultation.vo.ConsultationPrescriptionDetailVO;
import com.sphp.patient.consultation.vo.ConsultationPrescriptionPageVO;
import com.sphp.patient.consultation.vo.PrescriptionInterpretationVO;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.OffsetDateTime;
import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 处方控制器接口测试。
 */
class PrescriptionControllerTest {

    /**
     * 验证原处方列表路径保持响应字段兼容。
     *
     * @throws Exception MockMvc 执行失败时抛出
     */
    @Test
    void prescriptionListReturnsApprovedPrescriptionPage() throws Exception {
        PrescriptionService service = mock(PrescriptionService.class);
        when(service.prescriptionList(20001L, 1, 20)).thenReturn(ConsultationPrescriptionPageVO.builder()
                .pageNo(1).pageSize(20).total(1)
                .records(List.of(ConsultationPrescriptionPageVO.Item.builder().id(13001L)
                        .consultationId(11001L).doctorName("王医生").status("APPROVED")
                        .issuedAt(OffsetDateTime.now()).build()))
                .build());

        newMockMvc(service).perform(get("/c/v1/prescriptions").param("patientId", "20001")
                        .param("pageNo", "1").param("pageSize", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.records[0].status").value("APPROVED"))
                .andExpect(jsonPath("$.data.records[0].consultationId").value(11001));
    }

    /**
     * 验证原处方详情路径保持药品明细响应兼容。
     *
     * @throws Exception MockMvc 执行失败时抛出
     */
    @Test
    void prescriptionGetDetailReturnsItems() throws Exception {
        PrescriptionService service = mock(PrescriptionService.class);
        when(service.prescriptionGetDetail(13001L)).thenReturn(ConsultationPrescriptionDetailVO.builder()
                .id(13001L).status("APPROVED").doctorName("王医生")
                .doctor(ConsultationPrescriptionDetailVO.Doctor.builder().id(30001L).name("王医生").build())
                .items(List.of(ConsultationPrescriptionDetailVO.Item.builder().drugId(14001L)
                        .drugName("阿莫西林胶囊").specification("0.25g*24粒").dosage("0.5g")
                        .frequency("每日3次").usage("口服").durationDays((short) 5).build()))
                .build());

        newMockMvc(service).perform(get("/c/v1/prescriptions/13001"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("APPROVED"))
                .andExpect(jsonPath("$.data.items[0].drugName").value("阿莫西林胶囊"));
    }

    /**
     * 验证处方解读路由返回 READY 结果。
     *
     * @throws Exception MockMvc 执行失败时抛出
     */
    @Test
    void prescriptionGetInterpretationReturnsReadyContent() throws Exception {
        PrescriptionService service = mock(PrescriptionService.class);
        when(service.prescriptionGetInterpretation(13001L)).thenReturn(PrescriptionInterpretationVO.builder()
                .prescriptionId(13001L).content("请按医嘱服用")
                .disclaimer("AI建议仅供参考，不替代医生诊断").generatedAt(OffsetDateTime.now()).build());

        newMockMvc(service).perform(get("/c/v1/prescriptions/13001/interpretation"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.prescriptionId").value(13001L))
                .andExpect(jsonPath("$.data.content").value("请按医嘱服用"));
    }

    /**
     * 验证解读未生成转换为 B0202 与 HTTP 409。
     *
     * @throws Exception MockMvc 执行失败时抛出
     */
    @Test
    void prescriptionGetInterpretationReturnsConflictWhenNotReady() throws Exception {
        PrescriptionService service = mock(PrescriptionService.class);
        when(service.prescriptionGetInterpretation(13001L)).thenThrow(new CAuthException(
                ErrorCodeEnum.BUSINESS_STATUS_CONFLICT, HttpStatus.CONFLICT, "处方解读尚未生成"));

        newMockMvc(service).perform(get("/c/v1/prescriptions/13001/interpretation"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("B0202"));
    }

    /**
     * 创建处方控制器独立测试环境。
     *
     * @param service 处方服务模拟对象
     * @return MockMvc 测试对象
     */
    private MockMvc newMockMvc(PrescriptionService service) {
        return MockMvcBuilders.standaloneSetup(new PrescriptionController(service))
                .setControllerAdvice(new PrescriptionExceptionHandler())
                .build();
    }
}
