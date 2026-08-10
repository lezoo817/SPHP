package com.sphp.admin.patient.mapper;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.sphp.admin.patient.vo.PatientListVO;
import com.sphp.admin.patient.vo.PatientPrescriptionVO;
import com.sphp.admin.patient.vo.PatientVisitVO;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 患者管理数据查询 Mapper（复杂多表联查）。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
public interface PatientDataMapper {

    /**
     * 分页查询本院患者列表（通过 consult_record → doctor 关联医院）。
     *
     * @param page       MyBatis-Plus 分页对象
     * @param hospitalId 医院 ID，用于按本院数据隔离过滤
     * @param name       姓名模糊检索（可空）
     * @return 患者列表分页结果
     */
    @Select("<script>" +
            "SELECT p.id, p.name, p.gender, p.date_of_birth, MAX(cr.created_at)::date AS last_visit_date " +
            "FROM patient p " +
            "JOIN consult_record cr ON cr.patient_id = p.id AND cr.deleted_at IS NULL " +
            "JOIN doctor d ON cr.doctor_id = d.id AND d.deleted_at IS NULL " +
            "WHERE d.hospital_id = #{hospitalId} " +
            "  AND p.deleted_at IS NULL " +
            "<if test='name != null and name != \"\"'> " +
            "  AND p.name LIKE CONCAT('%', #{name}, '%') " +
            "</if> " +
            "GROUP BY p.id, p.name, p.gender, p.date_of_birth " +
            "ORDER BY last_visit_date DESC NULLS LAST" +
            "</script>")
    Page<PatientListVO> selectPatientPage(Page<PatientListVO> page,
                                           @Param("hospitalId") Long hospitalId,
                                           @Param("name") String name);

    /**
     * 分页查询患者就诊记录。
     *
     * @param page      分页对象
     * @param patientId 患者 ID
     * @return 就诊记录分页结果
     */
    @Select("<script>" +
            "SELECT cr.id AS consultId, cr.created_at::date AS visitDate, " +
            "       d.name AS doctorName, dp.name AS deptName, " +
            "       cr.doctor_note AS summary, " +
            "       cr.status, cr.created_at AS createdAt " +
            "FROM consult_record cr " +
            "JOIN doctor d ON cr.doctor_id = d.id AND d.deleted_at IS NULL " +
            "JOIN department dp ON d.dept_id = dp.id AND dp.deleted_at IS NULL " +
            "WHERE cr.patient_id = #{patientId} " +
            "  AND cr.deleted_at IS NULL " +
            "ORDER BY cr.created_at DESC" +
            "</script>")
    Page<PatientVisitVO> selectVisitPage(Page<PatientVisitVO> page,
                                          @Param("patientId") Long patientId);

    /**
     * 分页查询患者历史处方。
     *
     * @param page      分页对象
     * @param patientId 患者 ID
     * @return 历史处方分页结果
     */
    @Select("<script>" +
            "SELECT p.id, p.consult_id AS consultId, d.name AS doctorName, " +
            "       p.status, p.issued_at AS issuedAt, p.created_at AS createdAt, " +
            "       (SELECT COUNT(*) FROM prescription_item pi WHERE pi.prescription_id = p.id) AS itemCount " +
            "FROM prescription p " +
            "JOIN doctor d ON p.doctor_id = d.id AND d.deleted_at IS NULL " +
            "WHERE p.patient_id = #{patientId} " +
            "  AND p.deleted_at IS NULL " +
            "ORDER BY p.created_at DESC" +
            "</script>")
    Page<PatientPrescriptionVO> selectPrescriptionPage(Page<PatientPrescriptionVO> page,
                                                        @Param("patientId") Long patientId);

    /**
     * 判断指定患者是否在本院存在就诊关联（跨院数据隔离校验）。
     *
     * <p>镜像 {@link #selectPatientPage} 的医院隔离 join：经 consult_record → doctor.hospital_id
     * 判定患者是否属于当前医院，防止凭患者 ID 越权访问跨院档案（水平越权）。
     *
     * @param patientId  患者 ID
     * @param hospitalId 医院 ID（来自当前登录用户 DataScope）
     * @return 存在本院就诊关联时返回 true
     */
    @Select("SELECT EXISTS(" +
            "  SELECT 1 FROM consult_record cr " +
            "  JOIN doctor d ON cr.doctor_id = d.id AND d.deleted_at IS NULL " +
            "  WHERE cr.patient_id = #{patientId} " +
            "    AND d.hospital_id = #{hospitalId} " +
            "    AND cr.deleted_at IS NULL" +
            ") AS existed")
    boolean existsConsultInHospital(@Param("patientId") Long patientId, @Param("hospitalId") Long hospitalId);
}