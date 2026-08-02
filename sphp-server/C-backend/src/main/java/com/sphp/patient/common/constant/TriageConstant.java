package com.sphp.patient.common.constant;

/**
 * C端导诊模块公共常量。
 */
public final class TriageConstant {

    /** 导诊结果的非诊断性免责声明。 */
    public static final String DISCLAIMER = "本结果仅供健康咨询参考，不替代医生诊断。";

    /** 导诊评估写接口的幂等路径。 */
    public static final String ASSESSMENT_PATH = "/c/v1/triage/assessments";

    /**
     * 禁止实例化常量类。
     */
    private TriageConstant() {
    }
}
