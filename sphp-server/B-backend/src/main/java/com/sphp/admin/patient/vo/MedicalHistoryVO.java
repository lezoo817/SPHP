package com.sphp.admin.patient.vo;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

/**
 * 既往史 VO。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "既往史")
public class MedicalHistoryVO {

    @Schema(description = "既往史ID")
    private Long id;

    @Schema(description = "病史内容")
    private String content;

    @Schema(description = "发生日期")
    private LocalDate occurredAt;
}