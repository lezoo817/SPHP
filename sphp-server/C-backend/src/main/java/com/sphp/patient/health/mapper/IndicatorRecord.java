package com.sphp.patient.health.mapper;


/**
 * 指标查询记录。
 * @param name 指标名称
 * @param value 指标值
 * @param unit  单位
 * @param referenceRange 参考范围
 */
public record IndicatorRecord(
        String name,
        String value,
        String unit,
        String referenceRange){}
