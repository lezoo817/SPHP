package com.sphp.patient.triage.mapper;

import com.sphp.patient.triage.entity.TriageAssessment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 导诊患者归属、医院规则与评估记录的数据访问接口。
 */
@Mapper
public interface TriageDataMapper {

    /**
     * 查询当前用户有效的本人就诊人 ID。
     *
     * @param userId C端用户 ID
     * @return 本人就诊人 ID，不存在时返回 null
     */
    Long triageSelectSelfPatientId(@Param("userId") Long userId);

    /**
     * 判断就诊人是否未被软删除。
     *
     * @param patientId 就诊人 ID
     * @return 存在时返回 true
     */
    boolean triageExistsActivePatient(@Param("patientId") Long patientId);

    /**
     * 判断当前用户是否拥有有效的就诊人关系。
     *
     * @param userId C端用户 ID
     * @param patientId 就诊人 ID
     * @return 存在有效关系时返回 true
     */
    boolean triageHasActivePatientRelation(@Param("userId") Long userId, @Param("patientId") Long patientId);

    /**
     * 判断医院是否启用且未被软删除。
     *
     * @param hospitalId 医院 ID
     * @return 可用时返回 true
     */
    boolean triageExistsEnabledHospital(@Param("hospitalId") Long hospitalId);

    /**
     * 查询医院中命中症状关键词的有效导诊规则。
     *
     * @param hospitalId 医院 ID
     * @param symptom 症状描述
     * @return 已按紧急程度和优先级排序的规则投影
     */
    List<TriageDepartmentRuleRecord> triageSelectMatchedRules(@Param("hospitalId") Long hospitalId,
                                                               @Param("symptom") String symptom);

    /**
     * 写入导诊评估和推荐快照。
     *
     * @param assessment 导诊评估实体
     * @return 受影响行数
     */
    int triageInsertAssessment(@Param("assessment") TriageAssessment assessment);
}
