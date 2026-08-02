package com.sphp.patient.health.dto;
import lombok.*; import java.time.OffsetDateTime;
/** 确认随访计划请求。 */ @Getter @Setter public class ProposalFollowUpConfirmRequest { private OffsetDateTime remindAt; }
