package com.sphp.patient.health.dto;
import jakarta.validation.constraints.NotNull;
import lombok.*;
import com.sphp.patient.common.enums.ProposalMedicationActionEnum;
/** 更新用药计划请求。 */
@Getter
@Setter
public class ProposalMedicationUpdateRequest {

    /** 用药计划 ID */
    @NotNull
    private ProposalMedicationActionEnum action;

}
