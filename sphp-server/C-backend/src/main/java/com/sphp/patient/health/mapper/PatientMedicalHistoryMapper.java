package com.sphp.patient.health.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.health.entity.PatientMedicalHistory;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.OffsetDateTime;

/**
 * 患者既往史数据访问接口。
 */
@Mapper
public interface PatientMedicalHistoryMapper extends BaseMapper<PatientMedicalHistory> {

    /**
     * 按患者归属条件软删除未删除的既往史。
     *
     * @param historyId 既往史 ID
     * @param patientId 所属就诊人 ID
     * @param deletedAt 删除时间
     * @return 实际更新行数
     */
    int softDeleteActive(@Param("historyId") Long historyId, @Param("patientId") Long patientId,
                         @Param("deletedAt") OffsetDateTime deletedAt);
}
