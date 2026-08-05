package com.sphp.admin.pharmacy.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.admin.pharmacy.entity.PharmacyDrugStock;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 药房药品库存表 Mapper。 */
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
}
