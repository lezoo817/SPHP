package com.sphp.admin.prescription.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sphp.admin.doctor.entity.ConsultRecord;
import com.sphp.admin.doctor.entity.PatientAllergy;
import com.sphp.admin.doctor.entity.PatientMedicalHistory;
import com.sphp.admin.doctor.mapper.BPatientAllergyMapper;
import com.sphp.admin.doctor.mapper.BPatientMedicalHistoryMapper;
import com.sphp.admin.patient.entity.MedicationPlan;
import com.sphp.admin.patient.mapper.MedicationPlanMapper;
import com.sphp.admin.prescription.dto.PrescriptionSubmitRequest;
import com.sphp.admin.prescription.dto.RiskWarningVO;
import com.sphp.admin.prescription.entity.Drug;
import com.sphp.shared.exception.BusinessException;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * 处方风险拦截器。
 *
 * <p>统一处理过敏/禁忌/重复用药/高危药品四类规则，对外提供两个入口：
 * <ul>
 *   <li>{@link #precheck}：开方过程实时预检，只读不落库，返回带 {@code drugId}/{@code source}
 *       的风险清单，**不抛异常**，供前端按药品行内联展示。</li>
 *   <li>{@link #intercept}：提交时拦截，命中 ERROR（过敏/禁忌强匹配）抛 {@link #ERR_RISK_REDLINE}
 *       不入库；重复用药/高危药品 → 处方进入待审核队列（status=SUBMITTED）；无风险 → APPROVED。</li>
 * </ul>
 *
 * <p>过敏原数据源：patient_allergy 表 + consult_record.ai_summary.allergies。
 *
 * @author lezoo17
 * @since 2026-08-09
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PrescriptionRiskChecker {

    /** 红线规则拦截（3004：过敏/禁忌强匹配，禁止提交） */
    private static final String ERR_RISK_REDLINE = "3004";

    /** 风险级别 */
    private static final String RISK_LEVEL_ERROR = "ERROR";
    private static final String RISK_LEVEL_WARNING = "WARNING";
    private static final String RISK_LEVEL_AUDIT = "AUDIT";

    /** 过敏规则名 */
    private static final String RULE_ALLERGY = "过敏拦截";
    /** 禁忌规则名 */
    private static final String RULE_CONTRAINDICATION = "禁忌拦截";
    /** 重复用药规则名 */
    private static final String RULE_DUPLICATE = "重复用药检测";
    /** 高危药品规则名 */
    private static final String RULE_HIGH_RISK = "高危药物联用";

    /** 用药计划状态：进行中（计入当前用药比对） */
    private static final String MEDICATION_STATUS_ACTIVE = "ACTIVE";
    /** 用药计划状态：暂停中（仍计入当前用药比对，等待医生恢复） */
    private static final String MEDICATION_STATUS_PAUSED = "PAUSED";

    /** ai_summary.allergies 解析尾缀：命中即去除，取核心过敏原词 */
    private static final Pattern ALLERGY_SUFFIX = Pattern.compile("(过敏|史|药物|类)$");

    /** 剂型后缀词：用于从药品名提取成分键（长词在前，避免被短词截断） */
    private static final List<String> DOSAGE_FORM_WORDS = List.of(
            "泡腾片", "咀嚼片", "分散片", "肠溶片", "缓释片", "控释片", "糖衣片", "薄膜衣片",
            "缓释胶囊", "肠溶胶囊", "软胶囊", "硬胶囊", "胶囊", "片",
            "口服溶液", "口服液", "注射液", "滴丸", "软膏", "乳膏", "栓剂", "喷雾剂",
            "气雾剂", "颗粒", "散剂", "混悬液", "凝胶", "贴剂", "滴眼液");

    /** 特管/高危药品关键词名单（AUDIT），后续可扩充 */
    private static final List<String> HIGH_RISK_KEYWORDS = List.of(
            "麻醉", "精神", "吗啡", "芬太尼", "阿片", "可待因", "哌替啶",
            "地西泮", "氯硝西泮", "艾司唑仑", "曲马多", "唑吡坦", "抗凝", "华法林", "氯吡格雷");

    /** 禁忌关键词动词：分隔"关键词 + 动作"，仅取动作前的短语 */
    private static final List<String> CONTRAINDICATION_VERBS = List.of("禁用", "慎用", "不宜", "忌用");
    /** 禁忌关键词尾缀：人群限定词，需去除 */
    private static final String CONTRAINDICATION_PEOPLE_SUFFIX_REGEX = "(者|人群|患者)$";
    /** 禁忌关键词分隔符 */
    private static final String CONTRAINDICATION_DELIMITER_REGEX = "[、，,；;]";

    private final BPatientAllergyMapper patientAllergyMapper;
    private final BPatientMedicalHistoryMapper patientMedicalHistoryMapper;
    private final MedicationPlanMapper medicationPlanMapper;
    private final ObjectMapper objectMapper;

    /**
     * 处方风险预检（开方过程实时调用，只读不落库）。
     *
     * <p>对当前明细跑全部四类规则，返回带 {@code drugId}/{@code source} 的命中清单，
     * **不抛异常**。过敏/禁忌命中标 ERROR（提交时仍会被 {@link #intercept} 拦截），
     * 重复用药命中 drugId 为空（跨药品规则，前端置顶汇总）。
     *
     * @param consult 问诊记录（取 patientId 与 ai_summary）
     * @param drugMap 药品 ID → 药品
     * @param items   处方明细
     * @return 命中风险清单（可能为空）
     */
    public List<RiskWarningVO> precheck(ConsultRecord consult,
                                        Map<Long, Drug> drugMap,
                                        List<PrescriptionSubmitRequest.ItemDTO> items) {
        List<RiskWarningVO> warnings = new ArrayList<>();
        Long patientId = consult.getPatientId();

        // 1. 过敏（ERROR）：patient_allergy + ai_summary.allergies
        List<Allergen> allergens = loadAllergens(consult);
        if (!allergens.isEmpty()) {
            for (PrescriptionSubmitRequest.ItemDTO item : items) {
                Drug drug = drugMap.get(item.getDrugId());
                if (drug == null) continue;
                Allergen hit = matchAllergen(drug, allergens);
                if (hit != null) {
                    warnings.add(RiskWarningVO.builder()
                            .level(RISK_LEVEL_ERROR)
                            .rule(RULE_ALLERGY)
                            .drugId(drug.getId())
                            .drugName(drug.getName())
                            .source(hit.source())
                            .message("患者对「" + hit.word() + "」过敏，与「" + drug.getName() + "」冲突，禁止提交")
                            .build());
                }
            }
        }

        // 2. 禁忌（ERROR）：patient_medical_history × drug.contraindication
        List<PatientMedicalHistory> histories = patientMedicalHistoryMapper.selectList(
                Wrappers.<PatientMedicalHistory>lambdaQuery()
                        .eq(PatientMedicalHistory::getPatientId, patientId)
                        .isNull(PatientMedicalHistory::getDeletedAt));
        if (!histories.isEmpty()) {
            for (PrescriptionSubmitRequest.ItemDTO item : items) {
                Drug drug = drugMap.get(item.getDrugId());
                if (drug == null) continue;
                ContraHit hit = matchContraindication(drug, histories);
                if (hit != null) {
                    warnings.add(RiskWarningVO.builder()
                            .level(RISK_LEVEL_ERROR)
                            .rule(RULE_CONTRAINDICATION)
                            .drugId(drug.getId())
                            .drugName(drug.getName())
                            .source("既往史「" + hit.historyContent() + "」")
                            .message("患者既往史含「" + hit.keyword() + "」，与「" + drug.getName()
                                    + "」禁忌症冲突，禁止提交")
                            .build());
                }
            }
        }

        // 3. 重复用药（WARNING，drugId 为空 → 前端置顶汇总）：本张处方内 + 与患者当前用药两路比对
        // 3.1 本张处方内两两比对：成分键相同或互相包含
        for (int i = 0; i < items.size(); i++) {
            Drug drugA = drugMap.get(items.get(i).getDrugId());
            if (drugA == null) continue;
            for (int j = i + 1; j < items.size(); j++) {
                Drug drugB = drugMap.get(items.get(j).getDrugId());
                if (drugB == null) continue;
                if (sameIngredient(drugA.getName(), drugB.getName())) {
                    warnings.add(RiskWarningVO.builder()
                            .level(RISK_LEVEL_WARNING)
                            .rule(RULE_DUPLICATE)
                            .message("处方中存在可能重复用药：「" + drugA.getName() + "」与「" + drugB.getName()
                                    + "」可能属同一成分，请确认是否需联合使用")
                            .build());
                }
            }
        }

        // 3.2 与患者当前用药（medication_plan，ACTIVE/PAUSED）比对
        List<MedicationPlan> currentPlans = medicationPlanMapper.selectList(
                Wrappers.<MedicationPlan>lambdaQuery()
                        .eq(MedicationPlan::getPatientId, patientId)
                        .in(MedicationPlan::getStatus,
                                MEDICATION_STATUS_ACTIVE, MEDICATION_STATUS_PAUSED)
                        .isNull(MedicationPlan::getDeletedAt));
        for (PrescriptionSubmitRequest.ItemDTO item : items) {
            Drug drug = drugMap.get(item.getDrugId());
            if (drug == null) continue;
            for (MedicationPlan plan : currentPlans) {
                if (!StringUtils.hasText(plan.getDrugNameSnapshot())) continue;
                if (sameIngredient(drug.getName(), plan.getDrugNameSnapshot())) {
                    warnings.add(RiskWarningVO.builder()
                            .level(RISK_LEVEL_WARNING)
                            .rule(RULE_DUPLICATE)
                            .message("处方药品「" + drug.getName() + "」与患者当前用药「"
                                    + plan.getDrugNameSnapshot() + "」可能属同一成分，请确认是否需联合使用")
                            .build());
                }
            }
        }

        // 4. 高危药品（AUDIT）：特管/高危关键词命中即进审核队列
        for (PrescriptionSubmitRequest.ItemDTO item : items) {
            Drug drug = drugMap.get(item.getDrugId());
            if (drug == null) continue;
            if (isHighRiskDrug(drug.getName())) {
                warnings.add(RiskWarningVO.builder()
                        .level(RISK_LEVEL_AUDIT)
                        .rule(RULE_HIGH_RISK)
                        .drugId(drug.getId())
                        .drugName(drug.getName())
                        .message("「" + drug.getName() + "」命中审核级高危规则，提交后需人工审核")
                        .build());
            }
        }

        return warnings;
    }

    /**
     * 执行风险拦截（提交时调用）。
     *
     * <p>内部复用 {@link #precheck}：首个 ERROR（过敏/禁忌）命中即抛 ERR_RISK_REDLINE 不入库；
     * 命中重复用药（WARNING）或高危药品（AUDIT）任一规则 → 进入待审核队列。
     *
     * @param consult 问诊记录（取 patientId 与 ai_summary）
     * @param drugMap 药品 ID → 药品
     * @param items   处方明细
     * @return 风险结果（warnings + auditRequired）
     * @throws BusinessException 命中 ERROR 红线时抛 ERR_RISK_REDLINE
     */
    public RiskCheckResult intercept(ConsultRecord consult,
                                     Map<Long, Drug> drugMap,
                                     List<PrescriptionSubmitRequest.ItemDTO> items) {
        List<RiskWarningVO> warnings = precheck(consult, drugMap, items);

        // 红线：过敏/禁忌 ERROR → 抛错，不入库
        for (RiskWarningVO warning : warnings) {
            if (RISK_LEVEL_ERROR.equals(warning.getLevel())) {
                log.warn("红线拦截：{}", warning.getMessage());
                throw new BusinessException(ERR_RISK_REDLINE, warning.getMessage());
            }
        }

        // 命中重复用药（WARNING）或高危药品（AUDIT）任一规则 → 进入待审核队列（status=SUBMITTED）
        boolean auditRequired = warnings.stream().anyMatch(w -> RISK_LEVEL_WARNING.equals(w.getLevel())
                || RISK_LEVEL_AUDIT.equals(w.getLevel()));

        // 仅保留非红线警告落库（ERROR 已抛错，此处兜底过滤，保证快照不含红线级）
        List<RiskWarningVO> persisted = warnings.stream()
                .filter(w -> !RISK_LEVEL_ERROR.equals(w.getLevel()))
                .toList();
        return new RiskCheckResult(persisted, auditRequired);
    }

    /** 过敏原来源记录：word 为参与匹配的核心词，source 为来源描述（前端展示用） */
    private record Allergen(String word, String source) {}

    /** 禁忌命中记录：keyword 为命中禁忌关键词，historyContent 为包含该词的既往史原文 */
    private record ContraHit(String keyword, String historyContent) {}

    /** 合并 patient_allergy 与 ai_summary.allergies 的过敏原来源列表 */
    private List<Allergen> loadAllergens(ConsultRecord consult) {
        List<Allergen> allergens = new ArrayList<>();
        patientAllergyMapper.selectList(
                        Wrappers.<PatientAllergy>lambdaQuery()
                                .eq(PatientAllergy::getPatientId, consult.getPatientId())
                                .isNull(PatientAllergy::getDeletedAt))
                .forEach(a -> {
                    if (StringUtils.hasText(a.getAllergen())) {
                        allergens.add(new Allergen(a.getAllergen().trim(), "患者过敏史"));
                    }
                });
        for (String item : parseAiAllergies(consult.getAiSummary())) {
            String core = ALLERGY_SUFFIX.matcher(item).replaceAll("");
            if (StringUtils.hasText(core)) {
                allergens.add(new Allergen(core.trim(), "AI预问诊"));
            }
        }
        return allergens;
    }

    /**
     * 解析 ai_summary.allergies（jsonb 字符串数组）；解析失败返回空列表。
     *
     * @param aiSummaryJson 问诊摘要 JSON 字符串
     * @return 过敏原列表；摘要为空或解析失败返回空列表
     * @implNote TODO(lezoo17) 2026-08-09: AI 模块尚未向 ai_summary.allergies 写入数据，
     *           此来源当前恒为空，过敏拦截仅依赖 patient_allergy；待 AI 侧接通后移除本说明。
     */
    private List<String> parseAiAllergies(String aiSummaryJson) {
        if (!StringUtils.hasText(aiSummaryJson)) {
            return List.of();
        }
        try {
            Map<String, Object> summary = objectMapper.readValue(aiSummaryJson, new TypeReference<>() {});
            Object allergies = summary.get("allergies");
            if (allergies instanceof List<?> list) {
                return list.stream().filter(String.class::isInstance).map(String.class::cast).toList();
            }
            return List.of();
        } catch (Exception e) {
            log.warn("解析 ai_summary 失败: {}", e.getMessage());
            return List.of();
        }
    }

    /** 过敏匹配：drug.name 或 drug.contraindication 包含任一过敏原，返回命中的过敏原记录 */
    private Allergen matchAllergen(Drug drug, List<Allergen> allergens) {
        String name = drug.getName() != null ? drug.getName().toLowerCase() : "";
        String contraindication = drug.getContraindication() != null ? drug.getContraindication().toLowerCase() : "";
        for (Allergen allergen : allergens) {
            String key = allergen.word().toLowerCase();
            if (name.contains(key) || contraindication.contains(key)) {
                return allergen;
            }
        }
        return null;
    }

    /** 禁忌匹配：从 drug.contraindication 提取关键词，任一 history.content 包含即命中，返回命中详情 */
    private ContraHit matchContraindication(Drug drug, List<PatientMedicalHistory> histories) {
        if (!StringUtils.hasText(drug.getContraindication())) {
            return null;
        }
        List<String> keywords = extractContraindicationKeywords(drug.getContraindication());
        if (keywords.isEmpty()) {
            return null;
        }
        for (PatientMedicalHistory history : histories) {
            if (!StringUtils.hasText(history.getContent())) continue;
            String content = history.getContent();
            for (String keyword : keywords) {
                if (content.contains(keyword)) {
                    return new ContraHit(keyword, content);
                }
            }
        }
        return null;
    }

    /**
     * 提取禁忌关键词：按分隔符切分，取"禁用|慎用|不宜|忌用"前的短语，去尾部"者|人群|患者"。
     * 示例："肝功能不全者禁用，孕妇慎用" → ["肝功能不全", "孕妇"]。
     *
     * @param contraindication 药品禁忌症原文
     * @return 关键词列表（去重、按原文顺序）
     */
    List<String> extractContraindicationKeywords(String contraindication) {
        List<String> keywords = new ArrayList<>();
        for (String segment : contraindication.split(CONTRAINDICATION_DELIMITER_REGEX)) {
            String trimmed = segment.trim();
            if (!StringUtils.hasText(trimmed)) continue;
            int verbIdx = -1;
            for (String verb : CONTRAINDICATION_VERBS) {
                int idx = trimmed.indexOf(verb);
                if (idx >= 0 && (verbIdx < 0 || idx < verbIdx)) {
                    verbIdx = idx;
                }
            }
            String phrase = verbIdx >= 0 ? trimmed.substring(0, verbIdx) : trimmed;
            phrase = phrase.replaceFirst(CONTRAINDICATION_PEOPLE_SUFFIX_REGEX, "").trim();
            if (StringUtils.hasText(phrase)) {
                keywords.add(phrase);
            }
        }
        return keywords;
    }

    /** 重复用药：成分键相同，或一方长度≥3 且包含另一方 */
    boolean sameIngredient(String nameA, String nameB) {
        if (!StringUtils.hasText(nameA) || !StringUtils.hasText(nameB)) {
            return false;
        }
        String keyA = ingredientKey(nameA);
        String keyB = ingredientKey(nameB);
        if (keyA.equals(keyB)) {
            return true;
        }
        return (keyA.length() >= 3 && keyA.contains(keyB))
                || (keyB.length() >= 3 && keyB.contains(keyA));
    }

    /** 成分键：去除剂型后缀词后剩余串；去除后为空则返回原名 */
    String ingredientKey(String name) {
        String result = name;
        for (String word : DOSAGE_FORM_WORDS) {
            result = result.replace(word, "");
        }
        String trimmed = result.trim();
        return StringUtils.hasText(trimmed) ? trimmed : name;
    }

    /** 高危药品：特管关键词命中 */
    private boolean isHighRiskDrug(String drugName) {
        if (!StringUtils.hasText(drugName)) {
            return false;
        }
        String lower = drugName.toLowerCase();
        for (String keyword : HIGH_RISK_KEYWORDS) {
            if (lower.contains(keyword.toLowerCase())) {
                return true;
            }
        }
        return false;
    }

    /** 风险拦截结果 */
    @Data
    @RequiredArgsConstructor
    public static class RiskCheckResult {
        private final List<RiskWarningVO> warnings;
        private final boolean auditRequired;
    }
}
