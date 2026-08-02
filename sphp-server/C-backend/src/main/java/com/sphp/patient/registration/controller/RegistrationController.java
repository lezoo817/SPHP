package com.sphp.patient.registration.controller;

import com.sphp.patient.registration.service.RegistrationService;
import com.sphp.patient.registration.vo.HospitalListVO;
import com.sphp.shared.result.Result;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * C端挂号资源查询接口。
 */
@RestController
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
}
