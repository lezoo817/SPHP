package com.sphp.admin.prescription.service;

import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sphp.admin.doctor.entity.ConsultRecord;
import com.sphp.admin.doctor.entity.PatientAllergy;
import com.sphp.admin.doctor.entity.PatientMedicalHistory;
import com.sphp.admin.doctor.mapper.BPatientAllergyMapper;
import com.sphp.admin.doctor.mapper.BPatientMedicalHistoryMapper;
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
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 处方风险拦截器。
 *
 * <p>统一处理过敏/禁忌/重复用药/高危药品四类规则：
 * <ul>
 *   <li>ERROR（红线）：过敏强匹配、禁忌强匹配 → 抛 ERR_RISK_REDLINE，不入库</li>
 *   <li>WARNING（提示）：重复用药 → 处方进入待审核队列（status=SUBMITTED）</li>
 *   <li>AUDIT（审核）：高危药品 → 处方进入待审核队列（status=SUBMITTED）</li>
 *   <li>无风险 → APPROVED</li>
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
    private static final String RISK_LEVEL_WARNING = "WARNING";
    private static final String RISK_LEVEL_AUDIT = "AUDIT";

    /** 重复用药规则名 */
    private static final String RULE_DUPLICATE = "重复用药检测";
    /** 高危药品规则名 */
    private static final String RULE_HIGH_RISK = "高危药物联用";

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
    private final ObjectMapper objectMapper;

    /**
     * 执行风险拦截。
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
        List<RiskWarningVO> warnings = new ArrayList<>();
        Long patientId = consult.getPatientId();

        // 1. 过敏（ERROR）：patient_allergy + ai_summary.allergies
        Set<String> allergens = loadAllergens(consult);
        for (PrescriptionSubmitRequest.ItemDTO item : items) {
            Drug drug = drugMap.get(item.getDrugId());
            if (drug == null) continue;
            String hit = matchAllergen(drug, allergens);
            if (hit != null) {
                log.warn("红线拦截(过敏)：患者{}对{}过敏，处方含{}", patientId, hit, drug.getName());
                throw new BusinessException(ERR_RISK_REDLINE,
                        "红线规则拦截：患者对「" + hit + "」过敏，处方含「" + drug.getName() + "」，禁止提交");
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
                String hit = matchContraindication(drug, histories);
                if (hit != null) {
                    log.warn("红线拦截(禁忌)：患者{}既往史含{}，与{}禁忌冲突", patientId, hit, drug.getName());
                    throw new BusinessException(ERR_RISK_REDLINE,
                            "红线规则拦截：患者既往史含「" + hit + "」，与药品「" + drug.getName() + "」禁忌症冲突，禁止提交");
                }
            }
        }

        // 3. 重复用药（WARNING）：成分键相同或互相包含；命中即进审核队列
        boolean duplicateFound = false;
        for (int i = 0; i < items.size(); i++) {
            Drug drugA = drugMap.get(items.get(i).getDrugId());
            if (drugA == null) continue;
            for (int j = i + 1; j < items.size(); j++) {
                Drug drugB = drugMap.get(items.get(j).getDrugId());
                if (drugB == null) continue;
                if (sameIngredient(drugA.getName(), drugB.getName())) {
                    duplicateFound = true;
                    warnings.add(RiskWarningVO.builder()
                            .level(RISK_LEVEL_WARNING)
                            .rule(RULE_DUPLICATE)
                            .message("处方中存在可能重复用药：「" + drugA.getName() + "」与「" + drugB.getName()
                                    + "」可能属同一成分，请确认是否需联合使用")
                            .build());
                }
            }
        }

        // 4. 高危药品（AUDIT）：特管/高危关键词命中即进审核队列
        boolean highRisk = false;
        for (PrescriptionSubmitRequest.ItemDTO item : items) {
            Drug drug = drugMap.get(item.getDrugId());
            if (drug == null) continue;
            if (isHighRiskDrug(drug.getName())) {
                highRisk = true;
                break;
            }
        }
        if (highRisk) {
            warnings.add(RiskWarningVO.builder()
                    .level(RISK_LEVEL_AUDIT)
                    .rule(RULE_HIGH_RISK)
                    .message("处方命中审核级规则，需人工审核")
                    .build());
        }

        // 命中重复用药或高危任一规则 → 进入待审核队列（status=SUBMITTED）
        boolean auditRequired = duplicateFound || highRisk;
        return new RiskCheckResult(warnings, auditRequired);
    }

    /** 合并 patient_allergy 与 ai_summary.allergies 的过敏原集合 */
    private Set<String> loadAllergens(ConsultRecord consult) {
        Set<String> allergens = new LinkedHashSet<>();
        patientAllergyMapper.selectList(
                        Wrappers.<PatientAllergy>lambdaQuery()
                                .eq(PatientAllergy::getPatientId, consult.getPatientId())
                                .isNull(PatientAllergy::getDeletedAt))
                .forEach(a -> {
                    if (StringUtils.hasText(a.getAllergen())) {
                        allergens.add(a.getAllergen().trim());
                    }
                });
        for (String item : parseAiAllergies(consult.getAiSummary())) {
            String core = ALLERGY_SUFFIX.matcher(item).replaceAll("");
            if (StringUtils.hasText(core)) {
                allergens.add(core.trim());
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

    /** 过敏匹配：drug.name 或 drug.contraindication 包含任一过敏原，返回命中的过敏原 */
    private String matchAllergen(Drug drug, Set<String> allergens) {
        String name = drug.getName() != null ? drug.getName().toLowerCase() : "";
        String contraindication = drug.getContraindication() != null ? drug.getContraindication().toLowerCase() : "";
        for (String allergen : allergens) {
            String key = allergen.toLowerCase();
            if (name.contains(key) || contraindication.contains(key)) {
                return allergen;
            }
        }
        return null;
    }

    /** 禁忌匹配：从 drug.contraindication 提取关键词，任一 history.content 包含即命中，返回命中关键词 */
    private String matchContraindication(Drug drug, List<PatientMedicalHistory> histories) {
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
                    return keyword;
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
