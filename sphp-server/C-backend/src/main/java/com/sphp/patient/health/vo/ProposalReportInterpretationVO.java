package com.sphp.patient.health.vo;
import lombok.*;
import java.util.List;

/** 报告解读响应。 */
@Getter
@Setter
@NoArgsConstructor
public class ProposalReportInterpretationVO {
    // 报告 ID
    private Long reportId;
    // 指标解释列表
    private List<Explanation> indicatorExplanations;
    // 建议部门 ID
    private Long suggestedDepartmentId;
    // 免责声明
    private String disclaimer;

    // 报告解释
    @Getter
    @Setter
    @NoArgsConstructor
    public static class Explanation {
        // 指标名称
        private String indicator;
        // 解释内容
        private String explanation;
    }
}
