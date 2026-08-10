package com.sphp.patient.registration.controller;

import com.sphp.patient.common.constant.RegistrationConstant;
import com.sphp.patient.registration.service.RegistrationService;
import com.sphp.patient.registration.vo.DepartmentListVO;
import com.sphp.patient.registration.vo.DoctorPageVO;
import com.sphp.patient.registration.vo.AppointmentSlotVO;
import com.sphp.patient.registration.vo.HospitalListVO;
import com.sphp.shared.result.Result;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Positive;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.validation.annotation.Validated;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;
import java.util.List;

import static com.sphp.patient.common.constant.RegistrationConstant.DEFAULT_PAGE_NO;
import static com.sphp.patient.common.constant.RegistrationConstant.DEFAULT_PAGE_SIZE;

/**
 * C端挂号资源查询接口。
 */
@RestController
@Validated
@RequestMapping("/c/v1")
@Tag(name = "C端挂号资源", description = "查询医院、科室、医生与可预约时段")
@RequiredArgsConstructor
public class RegistrationController {

    private final RegistrationService registrationService;

    /**
     * 查询全部可供 C端选择的医院。
     *
     * @return 启用医院列表
     */
    @GetMapping("/hospitals")
    @Operation(summary = "查询可用医院")
    public Result<List<HospitalListVO>> listHospitals() {
        return Result.success("查询成功", registrationService.listHospitals());
    }

    /**
     * 查询指定医院下可供 C 端选择的启用科室。
     *
     * @param hospitalId 医院 ID
     * @param keyword 可选科室名称关键字
     * @return 启用科室列表
     */
    @GetMapping("/departments")
    @Operation(summary = "查询可用科室")
    public Result<List<DepartmentListVO>> listDepartments(
            @RequestParam @Positive(message = "hospitalId 必须为正数") Long hospitalId,
            @RequestParam(required = false) String keyword) {
        return Result.success("查询成功", registrationService.listDepartments(hospitalId, keyword));
    }

    /**
     * 分页查询指定医院和科室下的可用医生及其指定日期号源余量。
     *
     * @param hospitalId 医院 ID
     * @param departmentId 科室 ID
     * @param date 出诊日期，未传时按当天查询
     * @param pageNo 页码，未传时使用默认值
     * @param pageSize 页大小，未传时使用默认值
     * @return 医生分页数据
     */
    @GetMapping("/doctors")
    @Operation(summary = "查询可用医生")
    public Result<DoctorPageVO> listDoctors(
            @RequestParam @Positive(message = "hospitalId 必须为正数") Long hospitalId,
            @RequestParam @Positive(message = "departmentId 必须为正数") Long departmentId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) @Positive(message = "pageNo 必须为正数") Integer pageNo,
            @RequestParam(required = false) @Positive(message = "pageSize 必须为正数")
            @Max(value = 100, message = "pageSize 不能超过 100") Integer pageSize) {
        // 在接口层补齐默认分页值，确保下游查询参数稳定且方便前端重放请求。
        int resolvedPageNo = pageNo == null ? DEFAULT_PAGE_NO : pageNo;
        int resolvedPageSize = pageSize == null ? DEFAULT_PAGE_SIZE : pageSize;
        return Result.success("查询成功",
                registrationService.listDoctors(hospitalId, departmentId, date, resolvedPageNo, resolvedPageSize));
    }

    /**
     * 查询医生在指定医院和日期下已发布排班的全部可预约时段。
     *
     * @param doctorId 医生 ID
     * @param hospitalId 医院 ID
     * @param date 排班日期
     * @return 可预约时段列表
     */
    @GetMapping("/doctors/{doctorId}/slots")
    @Operation(summary = "查询医生可预约时段")
    public Result<List<AppointmentSlotVO>> listDoctorSlots(
            @PathVariable @Positive(message = "doctorId 必须为正数") Long doctorId,
            @RequestParam @Positive(message = "hospitalId 必须为正数") Long hospitalId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return Result.success("查询成功", registrationService.listDoctorSlots(hospitalId, doctorId, date));
    }
}
