package com.sphp.patient.registration.service.impl;

import com.sphp.patient.registration.mapper.RegistrationResourceMapper;
import com.sphp.patient.registration.mapper.HospitalRecord;
import com.sphp.patient.registration.vo.HospitalListVO;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * 挂号资源查询服务单元测试。
 */
class RegistrationServiceImplTest {

    /**
     * 验证仅映射可用医院的前端展示字段。
     */
    @Test
    void listHospitalsReturnsAvailableHospitalFields() {
        RegistrationResourceMapper resourceMapper = mock(RegistrationResourceMapper.class);
        RegistrationServiceImpl registrationService = new RegistrationServiceImpl(resourceMapper);
        when(resourceMapper.selectAvailableHospitals()).thenReturn(List.of(
                new HospitalRecord(101L, "智愈先锋第一医院", "三级甲等", "北京市东城区示例路1号", "010-12345678")
        ));

        List<HospitalListVO> result = registrationService.listHospitals();

        assertEquals(1, result.size());
        assertEquals(101L, result.getFirst().getHospitalId());
        assertEquals("智愈先锋第一医院", result.getFirst().getName());
        assertEquals("三级甲等", result.getFirst().getLevel());
        assertEquals("010-12345678", result.getFirst().getContact());
    }
}
