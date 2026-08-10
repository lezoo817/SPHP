package com.sphp.admin.pharmacy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.admin.pharmacy.entity.PharmacyDrugStock;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/** 药房药品库存表 Mapper。
 *
 * @author lezoo17
 * @since 2026-08-10
 */
public interface PharmacyDrugStockMapper extends BaseMapper<PharmacyDrugStock> {

    /**
     * 汇总某药品在当前医院全部药房的可用库存（available_count 之和，含 0）。
     *
     * @param drugId     药品 ID
     * @param hospitalId 医院 ID
     * @return 可用库存总量，无库存记录时返回 0
     */
    @Select("SELECT COALESCE(SUM(s.available_count), 0) FROM pharmacy_drug_stock s " +
            "JOIN pharmacy p ON s.pharmacy_id = p.id " +
            "WHERE s.drug_id = #{drugId} AND p.hospital_id = #{hospitalId} AND p.deleted_at IS NULL")
    Integer sumAvailableByDrugId(@Param("drugId") Long drugId, @Param("hospitalId") Long hospitalId);

    /**
     * 按药品汇总当前医院全部药房的库存。
     *
     * @param hospitalId 医院 ID
     * @return 按药品汇总的库存列表
     */
    @Select("SELECT" +
            "  CAST(NULL AS BIGINT) AS pharmacy_id," +
            "  CAST(NULL AS VARCHAR) AS pharmacy_name," +
            "  d.id AS drug_id," +
            "  d.name AS drug_name," +
            "  d.specification," +
            "  COALESCE(SUM(s.available_count), 0) AS available_count," +
            "  COALESCE(SUM(s.locked_count), 0) AS locked_count," +
            "  COALESCE(SUM(s.safety_stock), 0) AS safety_stock," +
            "  MIN(s.unit_price_cent) AS unit_price_cent" +
            " FROM pharmacy_drug_stock s" +
            " JOIN drug d ON s.drug_id = d.id" +
            " JOIN pharmacy p ON s.pharmacy_id = p.id" +
            " WHERE p.hospital_id = #{hospitalId} AND p.deleted_at IS NULL AND d.deleted_at IS NULL" +
            " GROUP BY d.id, d.name, d.specification" +
            " ORDER BY d.id")
    List<Map<String, Object>> selectAggregatedByHospital(@Param("hospitalId") Long hospitalId);
}