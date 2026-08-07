package com.sphp.patient.consultation.service.impl;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.consultation.mapper.PrescriptionDataMapper;
import com.sphp.patient.consultation.mapper.PrescriptionDetailRecord;
import com.sphp.patient.consultation.mapper.PrescriptionInterpretationRecord;
import com.sphp.patient.consultation.mapper.PrescriptionItemRecord;
import com.sphp.patient.consultation.mapper.PrescriptionListRecord;
import com.sphp.patient.consultation.mapper.PrescriptionResourceRecord;
import com.sphp.patient.consultation.vo.ConsultationPrescriptionDetailVO;
import com.sphp.patient.consultation.vo.ConsultationPrescriptionPageVO;
import com.sphp.patient.consultation.vo.PrescriptionInterpretationVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 处方服务业务规则测试。
 */
class PrescriptionServiceImplTest {

    /**
     * 每个测试结束后清理当前用户上下文。
     */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
    }

    /**
     * 验证列表使用默认本人患者和默认分页值。
     */
    @Test
    void prescriptionListUsesAccessiblePatientAndDefaultPagination() {
        PrescriptionDataMapper mapper = authorizedMapper();
        when(mapper.prescriptionSelectApprovedList(20001L, 20, 0, null)).thenReturn(List.of(
                new PrescriptionListRecord(13001L, 11001L, "王医生", "阿莫西林胶囊", OffsetDateTime.now())));
        when(mapper.prescriptionCountApprovedList(20001L, null)).thenReturn(1L);
        PrescriptionServiceImpl service = new PrescriptionServiceImpl(mapper);
        setUserContext();

        ConsultationPrescriptionPageVO result = service.prescriptionList(null, null, null, null);

        assertEquals(1L, result.getTotal());
        assertEquals(20, result.getPageSize());
        assertEquals("阿莫西林胶囊", result.getRecords().getFirst().getDisplayName());
        assertEquals("APPROVED", result.getRecords().getFirst().getStatus());
    }

    /**
     * 验证详情由处方反查患者归属后返回药品明细。
     */
    @Test
    void prescriptionGetDetailReturnsApprovedItemsAfterOwnershipCheck() {
        PrescriptionDataMapper mapper = authorizedMapper();
        when(mapper.prescriptionSelectResource(13001L))
                .thenReturn(new PrescriptionResourceRecord(13001L, 20001L, "APPROVED"));
        when(mapper.prescriptionSelectApprovedDetail(13001L))
                .thenReturn(new PrescriptionDetailRecord(13001L, 30001L, "王医生", "主治医师"));
        when(mapper.prescriptionSelectItems(13001L)).thenReturn(List.of(
                new PrescriptionItemRecord(14001L, "阿莫西林胶囊", "0.25g*24粒", "0.5g",
                        "每日3次", "口服", (short) 5)));
        PrescriptionServiceImpl service = new PrescriptionServiceImpl(mapper);
        setUserContext();

        ConsultationPrescriptionDetailVO result = service.prescriptionGetDetail(13001L);

        assertEquals("王医生", result.getDoctorName());
        assertEquals("阿莫西林胶囊", result.getItems().getFirst().getDrugName());
    }

    /**
     * 验证 READY 解读在患者归属校验后返回内容。
     */
    @Test
    void prescriptionGetInterpretationReturnsReadyContent() {
        PrescriptionDataMapper mapper = authorizedMapper();
        when(mapper.prescriptionSelectResource(13001L))
                .thenReturn(new PrescriptionResourceRecord(13001L, 20001L, "APPROVED"));
        when(mapper.prescriptionSelectInterpretation(13001L)).thenReturn(new PrescriptionInterpretationRecord(
                13001L, "请按医嘱服用", "仅供参考", "READY", OffsetDateTime.now()));
        PrescriptionServiceImpl service = new PrescriptionServiceImpl(mapper);
        setUserContext();

        PrescriptionInterpretationVO result = service.prescriptionGetInterpretation(13001L);

        assertEquals(13001L, result.getPrescriptionId());
        assertEquals("请按医嘱服用", result.getContent());
    }

    /**
     * 验证 PENDING 或 FAILED 解读统一返回 B0202。
     */
    @Test
    void prescriptionGetInterpretationRejectsNotReadyStatus() {
        PrescriptionDataMapper mapper = authorizedMapper();
        when(mapper.prescriptionSelectResource(13001L))
                .thenReturn(new PrescriptionResourceRecord(13001L, 20001L, "APPROVED"));
        when(mapper.prescriptionSelectInterpretation(13001L)).thenReturn(new PrescriptionInterpretationRecord(
                13001L, null, "仅供参考", "PENDING", null));
        PrescriptionServiceImpl service = new PrescriptionServiceImpl(mapper);
        setUserContext();

        CAuthException exception = assertThrows(CAuthException.class,
                () -> service.prescriptionGetInterpretation(13001L));

        assertEquals("B0202", exception.getCode());
    }

    /**
     * 验证其他账号患者的处方资源拒绝访问。
     */
    @Test
    void prescriptionGetInterpretationRejectsForeignPatient() {
        PrescriptionDataMapper mapper = mock(PrescriptionDataMapper.class);
        when(mapper.prescriptionSelectResource(13001L))
                .thenReturn(new PrescriptionResourceRecord(13001L, 20002L, "APPROVED"));
        when(mapper.prescriptionExistsActivePatient(20002L)).thenReturn(true);
        when(mapper.prescriptionHasActivePatientRelation(10001L, 20002L)).thenReturn(false);
        PrescriptionServiceImpl service = new PrescriptionServiceImpl(mapper);
        setUserContext();

        CAuthException exception = assertThrows(CAuthException.class,
                () -> service.prescriptionGetInterpretation(13001L));

        assertEquals("A0301", exception.getCode());
    }

    /**
     * 创建当前用户拥有本人患者关系的 Mapper 模拟对象。
     *
     * @return 已授权 Mapper 模拟对象
     */
    private PrescriptionDataMapper authorizedMapper() {
        PrescriptionDataMapper mapper = mock(PrescriptionDataMapper.class);
        when(mapper.prescriptionSelectSelfPatientId(10001L)).thenReturn(20001L);
        when(mapper.prescriptionExistsActivePatient(20001L)).thenReturn(true);
        when(mapper.prescriptionHasActivePatientRelation(10001L, 20001L)).thenReturn(true);
        return mapper;
    }

    /**
     * 设置当前 C端用户上下文。
     */
    private void setUserContext() {
        CUserContext.set(new CUserPrincipal(10001L, "patient", OffsetDateTime.now().plusHours(1), "session"));
    }
}
