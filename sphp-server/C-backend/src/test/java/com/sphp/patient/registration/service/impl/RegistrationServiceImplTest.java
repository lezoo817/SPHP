package com.sphp.patient.registration.service.impl;

import com.sphp.patient.registration.mapper.RegistrationResourceMapper;
import com.sphp.patient.registration.mapper.HospitalRecord;
import com.sphp.patient.registration.mapper.DepartmentRecord;
import com.sphp.patient.registration.mapper.DepartmentLinkRecord;
import com.sphp.patient.registration.mapper.DoctorRecord;
import com.sphp.patient.registration.mapper.DoctorLinkRecord;
import com.sphp.patient.registration.mapper.SlotRecord;
import com.sphp.patient.registration.config.RegistrationProperties;
import com.sphp.patient.registration.vo.HospitalListVO;
import com.sphp.patient.registration.vo.DepartmentListVO;
import com.sphp.patient.registration.vo.DoctorPageVO;
import com.sphp.patient.registration.vo.AppointmentSlotVO;
import com.sphp.patient.auth.exception.CAuthException;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
        RegistrationServiceImpl registrationService = newRegistrationService(resourceMapper,
                mock(StringRedisTemplate.class), 7);
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

    /**
     * 验证查询科室前校验医院可用，并按关键词返回启用科室。
     */
    @Test
    void listDepartmentsValidatesHospitalAndReturnsDepartmentFields() {
        RegistrationResourceMapper resourceMapper = mock(RegistrationResourceMapper.class);
        RegistrationServiceImpl registrationService = newRegistrationService(resourceMapper,
                mock(StringRedisTemplate.class), 7);
        when(resourceMapper.selectAvailableHospital(101L)).thenReturn(
                new HospitalRecord(101L, "智愈先锋第一医院", "三级甲等", "北京市东城区示例路1号", "010-12345678"));
        when(resourceMapper.selectAvailableDepartments(101L, "呼吸")).thenReturn(List.of(
                new DepartmentRecord(301L, "呼吸内科", "呼吸系统疾病诊疗")
        ));

        List<DepartmentListVO> result = registrationService.listDepartments(101L, "呼吸");

        assertEquals(1, result.size());
        assertEquals(301L, result.getFirst().getId());
        assertEquals("呼吸内科", result.getFirst().getName());
        assertEquals("呼吸系统疾病诊疗", result.getFirst().getDescription());
    }

    /**
     * 验证医生查询校验医院与科室归属后，按指定日期和分页条件返回可预约余量。
     */
    @Test
    void listDoctorsValidatesResourceChainAndReturnsPagedDoctors() {
        RegistrationResourceMapper resourceMapper = mock(RegistrationResourceMapper.class);
        RegistrationServiceImpl registrationService = newRegistrationService(resourceMapper,
                mock(StringRedisTemplate.class), 7);
        LocalDate date = LocalDate.of(2026, 8, 3);
        when(resourceMapper.selectAvailableHospital(101L)).thenReturn(
                new HospitalRecord(101L, "智慧先锋第一医院", "三级甲等", "北京市东城区示例路1号", "010-12345678"));
        when(resourceMapper.selectAvailableDepartmentLink(301L)).thenReturn(new DepartmentLinkRecord(301L, 101L));
        when(resourceMapper.selectAvailableDoctors(101L, 301L, date, 2, 2)).thenReturn(List.of(
                new DoctorRecord(501L, "张医生", "主任医师", "呼吸内科", 5000, 8L)
        ));
        when(resourceMapper.countAvailableDoctors(101L, 301L)).thenReturn(3L);

        DoctorPageVO result = registrationService.listDoctors(101L, 301L, date, 2, 2);

        assertEquals(2, result.getPageNo());
        assertEquals(2, result.getPageSize());
        assertEquals(3L, result.getTotal());
        assertEquals(501L, result.getRecords().getFirst().getId());
        assertEquals(8L, result.getRecords().getFirst().getAvailableCount());
    }

    /**
     * 验证科室不属于所选医院时拒绝查询，防止跨医院读取医生资源。
     */
    @Test
    void listDoctorsRejectsDepartmentFromAnotherHospital() {
        RegistrationResourceMapper resourceMapper = mock(RegistrationResourceMapper.class);
        RegistrationServiceImpl registrationService = newRegistrationService(resourceMapper,
                mock(StringRedisTemplate.class), 7);
        when(resourceMapper.selectAvailableHospital(101L)).thenReturn(
                new HospitalRecord(101L, "智慧先锋第一医院", "三级甲等", "北京市东城区示例路1号", "010-12345678"));
        when(resourceMapper.selectAvailableDepartmentLink(301L)).thenReturn(new DepartmentLinkRecord(301L, 102L));

        CAuthException exception = assertThrows(CAuthException.class,
                () -> registrationService.listDoctors(101L, 301L, LocalDate.of(2026, 8, 3), 1, 20));

        assertEquals("A0301", exception.getCode());
    }

    /**
     * 验证已发布排班优先采用 Redis 实时余量，并将时段时间转换为东八区偏移时间。
     */
    @Test
    void listDoctorSlotsUsesRedisRemainingCountAndBuildsShanghaiOffsetTime() {
        RegistrationResourceMapper resourceMapper = mock(RegistrationResourceMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> valueOperations = mock(ValueOperations.class);
        LocalDate date = LocalDate.now(com.sphp.patient.common.constant.RegistrationConstant.BUSINESS_ZONE_ID);
        when(resourceMapper.selectAvailableDoctorLink(501L)).thenReturn(new DoctorLinkRecord(501L, 101L));
        when(resourceMapper.hasPublishedSchedule(501L, date)).thenReturn(true);
        when(resourceMapper.selectPublishedSlots(501L, date)).thenReturn(List.of(
                new SlotRecord(1001L, LocalTime.of(8, 0), LocalTime.of(8, 30), 5000, 5L, "PUBLISHED")
        ));
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get("cend:slot:remain:1001")).thenReturn("3");
        RegistrationServiceImpl registrationService = newRegistrationService(resourceMapper, redisTemplate, 7);

        List<AppointmentSlotVO> result = registrationService.listDoctorSlots(101L, 501L, date);

        assertEquals(1, result.size());
        assertEquals(3L, result.getFirst().getAvailableCount());
        assertEquals("+08:00", result.getFirst().getStartTime().getOffset().toString());
        assertEquals(LocalTime.of(8, 0), result.getFirst().getStartTime().toLocalTime());
    }

    /**
     * 验证 Redis 查询异常时回退 PostgreSQL 号源快照，避免缓存故障导致已发布时段不可见。
     */
    @Test
    void listDoctorSlotsFallsBackToSnapshotWhenRedisFails() {
        RegistrationResourceMapper resourceMapper = mock(RegistrationResourceMapper.class);
        StringRedisTemplate redisTemplate = mock(StringRedisTemplate.class);
        LocalDate date = LocalDate.now(com.sphp.patient.common.constant.RegistrationConstant.BUSINESS_ZONE_ID);
        when(resourceMapper.selectAvailableDoctorLink(501L)).thenReturn(new DoctorLinkRecord(501L, 101L));
        when(resourceMapper.hasPublishedSchedule(501L, date)).thenReturn(true);
        when(resourceMapper.selectPublishedSlots(501L, date)).thenReturn(List.of(
                new SlotRecord(1001L, LocalTime.of(8, 0), LocalTime.of(8, 30), 5000, 5L, "PUBLISHED")
        ));
        when(redisTemplate.opsForValue()).thenThrow(new RuntimeException("Redis 不可用"));
        RegistrationServiceImpl registrationService = newRegistrationService(resourceMapper, redisTemplate, 7);

        List<AppointmentSlotVO> result = registrationService.listDoctorSlots(101L, 501L, date);

        assertEquals(5L, result.getFirst().getAvailableCount());
    }

    /**
     * 验证超出放号窗口的日期按参数超范围错误拒绝。
     */
    @Test
    void listDoctorSlotsRejectsDateOutsideReleaseWindow() {
        RegistrationResourceMapper resourceMapper = mock(RegistrationResourceMapper.class);
        RegistrationServiceImpl registrationService = newRegistrationService(resourceMapper,
                mock(StringRedisTemplate.class), 7);
        LocalDate invalidDate = LocalDate.now(com.sphp.patient.common.constant.RegistrationConstant.BUSINESS_ZONE_ID)
                .plusDays(7);

        CAuthException exception = assertThrows(CAuthException.class,
                () -> registrationService.listDoctorSlots(101L, 501L, invalidDate));

        assertEquals("A0420", exception.getCode());
    }

    /**
     * 创建带放号窗口配置的挂号查询服务，隔离各测试中的 Redis 依赖。
     *
     * @param resourceMapper 挂号资源 Mapper
     * @param redisTemplate Redis 操作模板
     * @param slotReleaseDays 放号天数
     * @return 挂号资源查询服务
     */
    private RegistrationServiceImpl newRegistrationService(RegistrationResourceMapper resourceMapper,
                                                            StringRedisTemplate redisTemplate, int slotReleaseDays) {
        RegistrationProperties properties = new RegistrationProperties();
        properties.setSlotReleaseDays(slotReleaseDays);
        return new RegistrationServiceImpl(resourceMapper, redisTemplate, properties);
    }
}
