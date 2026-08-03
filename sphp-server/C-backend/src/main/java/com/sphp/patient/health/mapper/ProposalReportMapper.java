package com.sphp.patient.health.mapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.sphp.patient.health.entity.ProposalPatientReport;
import org.apache.ibatis.annotations.Mapper;

/**
 * 健康报告数据访问接口。
 */
@Mapper
public interface ProposalReportMapper extends BaseMapper<ProposalPatientReport>{}
