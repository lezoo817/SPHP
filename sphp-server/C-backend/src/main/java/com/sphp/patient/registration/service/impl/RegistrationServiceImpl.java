package com.sphp.patient.registration.service.impl;

import com.sphp.patient.common.constant.RegistrationConstant;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.registration.mapper.DepartmentLinkRecord;
import com.sphp.patient.registration.mapper.DepartmentRecord;
import com.sphp.patient.registration.mapper.DoctorRecord;
import com.sphp.patient.registration.mapper.DoctorLinkRecord;
import com.sphp.patient.registration.mapper.HospitalRecord;
import com.sphp.patient.registration.mapper.RegistrationResourceMapper;
import com.sphp.patient.registration.mapper.SlotRecord;
import com.sphp.patient.registration.service.RegistrationService;
import com.sphp.patient.registration.config.RegistrationProperties;
import com.sphp.patient.registration.vo.AppointmentSlotVO;
import com.sphp.patient.registration.vo.DepartmentListVO;
import com.sphp.patient.registration.vo.DoctorListItemVO;
import com.sphp.patient.registration.vo.DoctorPageVO;
import com.sphp.patient.registration.vo.HospitalListVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import static com.sphp.patient.common.constant.RegistrationConstant.*;
import static com.sphp.shared.common.enums.ErrorCodeEnum.*;

/**
 * C端挂号资源查询服务实现。
 */
@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    // 资源查询
    private final RegistrationResourceMapper resourceMapper;
    // 缓存
    private final StringRedisTemplate redisTemplate;
    // 挂号配置
    private final RegistrationProperties registrationProperties;


    /**
     * 查询全部可供 C端选择的医院。
     *
     * @return 启用医院列表
     */
    @Override
    public List<HospitalListVO> listHospitals() {
        return resourceMapper.selectAvailableHospitals().stream()
                .map(this::toHospitalListVO)
                .toList();
    }

    /**
     * 查询指定可用医院下的启用科室。
     *
     * @param hospitalId 医院 ID
     * @param keyword 可选科室名称关键字
     * @return 可选科室列表
     * @throws CAuthException 医院不存在或已停用时抛出
     */
    @Override
    public List<DepartmentListVO> listDepartments(Long hospitalId, String keyword) {
        // 先确认医院可用，避免向客户端暴露停用医院下的科室数据。
        if (resourceMapper.selectAvailableHospital(hospitalId) == null) {
            throw new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "医院不存在或已停用");
        }
        // 空白关键字不参与筛选，保证与未传关键字的查询语义一致。
        String normalizedKeyword = keyword == null || keyword.isBlank() ? null : keyword.trim();
        return resourceMapper.selectAvailableDepartments(hospitalId, normalizedKeyword).stream()
                .map(this::toDepartmentListVO)
                .toList();
    }

    /**
     * 分页查询指定医院和科室下的可用医生，并统计指定日期的可预约号源。
     *
     * @param hospitalId 医院 ID
     * @param departmentId 科室 ID
     * @param date 出诊日期，未传时使用当前业务日期
     * @param pageNo 页码，未传时使用默认页码
     * @param pageSize 页大小，未传时使用默认页大小
     * @return 医生分页数据
     * @throws CAuthException 资源不可用或医院链路不匹配时抛出
     */
    @Override
    public DoctorPageVO listDoctors(Long hospitalId, Long departmentId, LocalDate date, Integer pageNo, Integer pageSize) {
        // 医院、科室分别校验，确保停用资源不会出现在 C 端挂号入口。
        validateHospitalAndDepartment(hospitalId, departmentId);
        LocalDate queryDate = date == null ? LocalDate.now(BUSINESS_ZONE_ID) : date;
        int resolvedPageNo = pageNo == null ?DEFAULT_PAGE_NO : pageNo;
        int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        long offset = (long) (resolvedPageNo - 1) * resolvedPageSize;

        // 余量只汇总指定日期已发布排班的 AVAILABLE 快照，零余量医生仍需供前端展示。
        List<DoctorListItemVO> records = resourceMapper.selectAvailableDoctors(
                        hospitalId, departmentId, queryDate, resolvedPageSize, offset)
                .stream()
                .map(this::toDoctorListItemVO)
                .toList();
        long total = resourceMapper.countAvailableDoctors(hospitalId, departmentId);
        return DoctorPageVO.builder()
                .pageNo(resolvedPageNo)
                .pageSize(resolvedPageSize)
                .total(total)
                .records(records)
                .build();
    }

    /**
     * 查询指定医院医生在指定日期已发布排班下的全部可预约时段。
     *
     * @param hospitalId 医院 ID
     * @param doctorId 医生 ID
     * @param date 排班日期
     * @return 可预约时段列表，包含零余量时段
     * @throws CAuthException 日期超范围、资源不可用或医院链路不匹配时抛出
     */
    @Override
    public List<AppointmentSlotVO> listDoctorSlots(Long hospitalId, Long doctorId, LocalDate date) {
        // 日期超范围
        validateSlotDate(date);
        // 医院、医生分别校验，确保停用资源不会出现在 C 端挂号入口。
        validateDoctorHospitalLink(hospitalId, doctorId);
        // 没有发布排班时不返回草稿或取消排班的时段，避免误导预约入口。
        if (!resourceMapper.hasPublishedSchedule(doctorId, date)) {
            throw new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "医生当日暂无可预约排班");
        }
        return resourceMapper.selectPublishedSlots(doctorId, date).stream()
                .map(slot -> toAppointmentSlotVO(slot, date))
                .toList();
    }

    /**
     * 转换医院查询记录，避免向 C端泄漏后台管理字段。
     *
     * @param record 医院查询记录
     * @return 可选医院响应对象
     */
    private HospitalListVO toHospitalListVO(HospitalRecord record) {
        return HospitalListVO.builder()
                .hospitalId(record.hospitalId())
                .name(record.name())
                .level(record.level())
                .address(record.address())
                .contact(record.contact())
                .build();
    }

    /**
     * 转换科室查询记录，限制返回字段为 C 端选择挂号资源所需信息。
     *
     * @param record 科室查询记录
     * @return 可选科室响应对象
     */
    private DepartmentListVO toDepartmentListVO(DepartmentRecord record) {
        return DepartmentListVO.builder()
                .id(record.id())
                .name(record.name())
                .location(record.location())
                .build();
    }

    /**
     * 校验医院和科室可用性及所属医院，阻断跨医院资源查询。
     *
     * @param hospitalId 医院 ID
     * @param departmentId 科室 ID
     * @throws CAuthException 资源不存在、已停用或医院链路不匹配时抛出
     */
    private void validateHospitalAndDepartment(Long hospitalId, Long departmentId) {

        if (resourceMapper.selectAvailableHospital(hospitalId) == null) {
            throw new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "医院不存在或已停用");
        }
        DepartmentLinkRecord department = resourceMapper.selectAvailableDepartmentLink(departmentId);
        if (department == null) {
            throw new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "科室不存在或已停用");
        }
        // 科室必须隶属于请求医院，避免客户端用有效 ID 跨医院访问。
        if (!hospitalId.equals(department.hospitalId())) {
            throw new CAuthException(UNAUTHORIZED, HttpStatus.FORBIDDEN, "科室不属于当前医院");
        }
    }

    /**
     * 校验查询日期在当天至可放号天数范围内，防止读取未发布或历史排班。
     *
     * @param date 排班日期
     * @throws CAuthException 日期为空或超出放号窗口时抛出
     */
    private void validateSlotDate(LocalDate date) {
        LocalDate today = LocalDate.now(BUSINESS_ZONE_ID);
        // 可预约窗口
        int releaseDays = registrationProperties.getSlotReleaseDays();
        // 可预约窗口最大值
        LocalDate latestDate = today.plusDays(Math.max(releaseDays, 1) - 1L);
        //如果日期无效，则抛出参数错误。
        if (date == null || date.isBefore(today) || date.isAfter(latestDate)) {
            throw new CAuthException(PARAMETER_OUT_OF_RANGE, HttpStatus.BAD_REQUEST,
                    "预约日期仅支持当天至未来" + Math.max(releaseDays, 1) + "天");
        }
    }

    /**
     * 校验医生可用状态及所属医院，避免跨医院查看排班时段。
     *
     * @param hospitalId 医院 ID
     * @param doctorId 医生 ID
     * @throws CAuthException 医生不可用或医院链路不匹配时抛出
     */
    private void validateDoctorHospitalLink(Long hospitalId, Long doctorId) {
        DoctorLinkRecord doctor = resourceMapper.selectAvailableDoctorLink(doctorId);
        if (doctor == null) {
            throw new CAuthException(INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "医生不存在或已停用");
        }
        // 医生 ID 有效也必须匹配当前医院，防止跨院获取排班详情。
        if (!hospitalId.equals(doctor.hospitalId())) {
            throw new CAuthException(UNAUTHORIZED, HttpStatus.FORBIDDEN, "医生不属于当前医院");
        }
    }

    /**
     * 转换医生查询记录，只返回 C 端挂号选择所需的资料和余量。
     *
     * @param record 医生查询记录
     * @return 医生列表响应项
     */
    private DoctorListItemVO toDoctorListItemVO(DoctorRecord record) {
        return DoctorListItemVO.builder()
                .id(record.id())
                .name(record.name())
                .title(record.title())
                .specialty(record.specialty())
                .registrationFeeCent(record.registrationFeeCent())
                .availableCount(record.availableCount())
                .build();
    }

    /**
     * 转换已发布时段并解析实时余量，Redis 不可用时回退 PostgreSQL 快照。
     *
     * @param record 时段查询记录
     * @param date 排班日期
     * @return 可预约时段响应对象
     */
    private AppointmentSlotVO toAppointmentSlotVO(SlotRecord record, LocalDate date) {
        return AppointmentSlotVO.builder()
                .slotId(record.slotId())
                .startTime(toBusinessOffsetDateTime(date, record.startTime()))
                .endTime(toBusinessOffsetDateTime(date, record.endTime()))
                .feeCent(record.feeCent())
                .availableCount(resolveAvailableCount(record))
                .scheduleStatus(record.scheduleStatus())
                .build();
    }

    /**
     * 读取 Redis 中的实时号源余量，异常或非法缓存值时使用数据库快照兜底。
     *
     * @param record 时段查询记录
     * @return 实时余量或 PostgreSQL 快照余量
     */
    private long resolveAvailableCount(SlotRecord record) {
        long snapshotCount = record.availableCount() == null ? 0L : record.availableCount();
        try {
            String value = redisTemplate.opsForValue().get(RegistrationConstant.SLOT_REMAIN_KEY_PREFIX + record.slotId());
            if (value == null) {
                return snapshotCount;
            }
            long remainingCount = Long.parseLong(value);
            // 负数属于损坏缓存，不向客户端输出异常余量。
            return remainingCount >= 0 ? remainingCount : snapshotCount;
        } catch (RuntimeException exception) {
            // Redis 仅保存实时余量缓存，读取失败时 PostgreSQL 快照仍是可用事实来源。
            return snapshotCount;
        }
    }

    /**
     * 按业务时区组合日期和时段时间，避免服务部署时区影响前端展示。
     *
     * @param date 排班日期
     * @param time 时段时间
     * @return 东八区偏移时间
     */
    private OffsetDateTime toBusinessOffsetDateTime(LocalDate date, java.time.LocalTime time) {
        return date.atTime(time).atZone(BUSINESS_ZONE_ID).toOffsetDateTime();
    }
}
