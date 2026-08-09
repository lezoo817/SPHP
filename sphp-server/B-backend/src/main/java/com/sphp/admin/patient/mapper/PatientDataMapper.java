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
     */
    @Select("<script>" +
            "SELECT cr.id AS consultId, cr.created_at::date AS visitDate, " +
            "       d.name AS doctorName, dp.name AS deptName, " +
            "       COALESCE(cr.doctor_note, cr.ai_summary) AS summary, " +
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
}