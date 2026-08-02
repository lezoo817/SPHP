package com.sphp.patient.registration.service.impl;

import com.sphp.patient.common.constant.RegistrationConstant;
import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.patient.registration.mapper.DepartmentLinkRecord;
import com.sphp.patient.registration.mapper.DepartmentRecord;
import com.sphp.patient.registration.mapper.DoctorRecord;
import com.sphp.patient.registration.mapper.HospitalRecord;
import com.sphp.patient.registration.mapper.RegistrationResourceMapper;
import com.sphp.patient.registration.service.RegistrationService;
import com.sphp.patient.registration.vo.DepartmentListVO;
import com.sphp.patient.registration.vo.DoctorListItemVO;
import com.sphp.patient.registration.vo.DoctorPageVO;
import com.sphp.patient.registration.vo.HospitalListVO;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

/**
 * C端挂号资源查询服务实现。
 */
@Service
@RequiredArgsConstructor
public class RegistrationServiceImpl implements RegistrationService {

    private final RegistrationResourceMapper resourceMapper;

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
            throw new CAuthException(ErrorCodeEnum.INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "医院不存在或已停用");
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
        LocalDate queryDate = date == null ? LocalDate.now(RegistrationConstant.BUSINESS_ZONE_ID) : date;
        int resolvedPageNo = pageNo == null ? RegistrationConstant.DEFAULT_PAGE_NO : pageNo;
        int resolvedPageSize = pageSize == null ? RegistrationConstant.DEFAULT_PAGE_SIZE : pageSize;
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
                .description(record.description())
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
            throw new CAuthException(ErrorCodeEnum.INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "医院不存在或已停用");
        }
        DepartmentLinkRecord department = resourceMapper.selectAvailableDepartmentLink(departmentId);
        if (department == null) {
            throw new CAuthException(ErrorCodeEnum.INVALID_USER_INPUT, HttpStatus.NOT_FOUND, "科室不存在或已停用");
        }
        // 科室必须隶属于请求医院，避免客户端用有效 ID 跨医院访问。
        if (!hospitalId.equals(department.hospitalId())) {
            throw new CAuthException(ErrorCodeEnum.UNAUTHORIZED, HttpStatus.FORBIDDEN, "科室不属于当前医院");
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
}
