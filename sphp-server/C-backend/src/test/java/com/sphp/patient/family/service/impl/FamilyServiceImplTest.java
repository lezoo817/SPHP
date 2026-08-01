package com.sphp.patient.family.service.impl;

import com.sphp.patient.auth.support.context.CUserContext;
import com.sphp.patient.auth.support.context.CUserPrincipal;
import com.sphp.patient.family.mapper.FamilyMemberMapper;
import com.sphp.patient.family.mapper.FamilyMemberRecord;
import com.sphp.patient.family.vo.FamilyMemberListVO;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 家庭成员服务单元测试。
 */
class FamilyServiceImplTest {

    /**
     * 每个测试结束后清理当前用户上下文。
     */
    @AfterEach
    void clearContext() {
        CUserContext.clear();
    }

    /**
     * 验证查询仅映射有效成员资料，并对手机号进行脱敏。
     */
    @Test
    void listFamilyMembersReturnsMaskedPhoneAndRelationName() {
        FamilyMemberMapper familyMemberMapper = mock(FamilyMemberMapper.class);
        FamilyServiceImpl familyService = new FamilyServiceImpl(familyMemberMapper);
        CUserContext.set(new CUserPrincipal(10001L, "patient_zhangsan",
                OffsetDateTime.now().plusHours(1), "session-hash"));
        when(familyMemberMapper.selectActiveMembers(10001L)).thenReturn(List.of(
                record(20001L, "张三", "SELF", true, "13800138000", LocalDate.of(1990, 5, 20)),
                record(20002L, "张小明", "CHILD", false, "13800138001", LocalDate.of(2018, 6, 1))
        ));

        List<FamilyMemberListVO> result = familyService.listFamilyMembers();

        assertEquals(2, result.size());
        assertEquals("本人", result.getFirst().getRelationName());
        assertEquals("138****8000", result.getFirst().getPhone());
        assertEquals("子女", result.get(1).getRelationName());
        assertFalse(result.get(1).getIsDefault());
    }

    /**
     * 创建家庭成员联表查询记录。
     *
     * @param patientId 就诊人 ID
     * @param name 姓名
     * @param relation 关系编码
     * @param isDefault 是否默认
     * @param phone 手机号
     * @param birthday 出生日期
     * @return 查询记录
     */
    private FamilyMemberRecord record(Long patientId, String name, String relation, boolean isDefault,
                                      String phone, LocalDate birthday) {
        FamilyMemberRecord record = new FamilyMemberRecord();
        record.setPatientId(patientId);
        record.setName(name);
        record.setRelationship(relation);
        record.setIsDefault(isDefault);
        record.setGender("MALE");
        record.setPhone(phone);
        record.setBirthday(birthday);
        return record;
    }
}
