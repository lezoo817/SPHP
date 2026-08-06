package com.sphp.patient.health.support;

import com.sphp.patient.auth.exception.CAuthException;
import com.sphp.shared.common.enums.ErrorCodeEnum;
import org.springframework.http.HttpStatus;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static com.sphp.shared.common.enums.ErrorCodeEnum.BUSINESS_STATUS_CONFLICT;
import static com.sphp.shared.common.enums.ErrorCodeEnum.PARAMETER_OUT_OF_RANGE;

/**
 * 用药频次与每日提醒时刻转换工具。
 */
public final class ProposalMedicationReminderSupport {

    // 从处方频次中提取每日次数的首个阿拉伯数字
    private static final Pattern FREQUENCY_NUMBER_PATTERN = Pattern.compile("(\\d+)");

    // C 端用药提醒统一以中国标准时间计算
    private static final ZoneId SHANGHAI_ZONE_ID = ZoneId.of("Asia/Shanghai");

    // 每日一次的默认提醒时刻
    private static final List<LocalTime> ONCE_DAILY = List.of(LocalTime.of(8, 0));

    // 每日两次的默认提醒时刻
    private static final List<LocalTime> TWICE_DAILY = List.of(LocalTime.of(8, 0), LocalTime.of(20, 0));

    // 每日三次的默认提醒时刻

    private static final List<LocalTime> THREE_TIMES_DAILY = List.of(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0));
    // 每日四次的默认提醒时刻

    private static final List<LocalTime> FOUR_TIMES_DAILY = List.of(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(16, 0), LocalTime.of(20, 0));
    // 支持的每日服药次数与日间提醒时刻映射

    private static final Map<Integer, List<LocalTime>> REMINDER_TIME_MAPPING = Map.of(
            1, ONCE_DAILY,
            2, TWICE_DAILY,
            3, THREE_TIMES_DAILY,
            4, FOUR_TIMES_DAILY
    );

    private ProposalMedicationReminderSupport() {
    }

    /**
     * 根据处方频次解析固定的每日提醒时刻。
     *
     * @param frequency 处方频次文本
     * @return 按时间升序排列的每日提醒时刻
     * @throws CAuthException 频次无法解析或不在每日一至四次范围时抛出
     */
    public static List<LocalTime> proposalResolveReminderTimes(String frequency) {
        Matcher matcher = frequency == null ? FREQUENCY_NUMBER_PATTERN.matcher("")
                : FREQUENCY_NUMBER_PATTERN.matcher(frequency);
        if (!matcher.find()) {
            throw unsupportedFrequency();
        }
        try {
            List<LocalTime> times = REMINDER_TIME_MAPPING.get(Integer.parseInt(matcher.group(1)));
            if (times == null) {
                throw unsupportedFrequency();
            }
            return times;
        } catch (NumberFormatException exception) {
            throw unsupportedFrequency();
        }
    }

    /**
     * 将固定时刻序列化为 JSONB 数组文本。
     *
     * @param times 每日提醒时刻
     * @return JSON 数组文本
     */
    public static String proposalSerializeReminderTimes(List<LocalTime> times) {
        return times.stream().map(LocalTime::toString).map(value -> "\"" + value + "\"")
                .collect(java.util.stream.Collectors.joining(",", "[", "]"));
    }

    /**
     * 解析已保存的提醒时刻 JSONB 文本。
     *
     * @param reminderTimesJson 每日提醒时刻 JSON 数组文本
     * @return 按时间升序排列的每日提醒时刻
     * @throws CAuthException 存量提醒快照格式非法时抛出
     */
    public static List<LocalTime> proposalParseReminderTimes(String reminderTimesJson) {
        if (reminderTimesJson == null || reminderTimesJson.isBlank()) {
            throw new CAuthException(BUSINESS_STATUS_CONFLICT, HttpStatus.CONFLICT,
                    "用药提醒时刻不存在，请重新开启提醒");
        }
        try {
            String normalized = reminderTimesJson.trim();
            if (!normalized.startsWith("[") || !normalized.endsWith("]")) {
                throw new IllegalArgumentException("提醒时刻不是数组");
            }
            String content = normalized.substring(1, normalized.length() - 1).trim();
            if (content.isEmpty()) {
                throw new IllegalArgumentException("提醒时刻为空");
            }
            return java.util.Arrays.stream(content.split(","))
                    .map(value -> value.trim().replace("\"", ""))
                    .map(LocalTime::parse)
                    .sorted()
                    .toList();
        } catch (RuntimeException exception) {
            throw new CAuthException(BUSINESS_STATUS_CONFLICT, HttpStatus.CONFLICT,
                    "用药提醒时刻异常，请重新开启提醒");
        }
    }

    /**
     * 计算参考时间之后的下一次每日提醒时间。
     *
     * @param times 每日提醒时刻
     * @param reference 计算参考时间
     * @return 下一次提醒时间
     */
    public static OffsetDateTime proposalCalculateNextReminderAt(List<LocalTime> times, OffsetDateTime reference) {
        ZonedDateTime zonedReference = reference.atZoneSameInstant(SHANGHAI_ZONE_ID);
        LocalDate date = zonedReference.toLocalDate();
        LocalTime currentTime = zonedReference.toLocalTime();
        for (LocalTime time : times) {
            if (time.isAfter(currentTime)) {
                return ZonedDateTime.of(date, time, SHANGHAI_ZONE_ID).toOffsetDateTime();
            }
        }
        return ZonedDateTime.of(date.plusDays(1), times.getFirst(), SHANGHAI_ZONE_ID).toOffsetDateTime();
    }

    /**
     * 创建不支持处方频次的参数异常。
     *
     * @return HTTP 400 业务异常
     */
    private static CAuthException unsupportedFrequency() {
        return new CAuthException(PARAMETER_OUT_OF_RANGE, HttpStatus.BAD_REQUEST,
                "处方频次仅支持每日1至4次，暂无法开启提醒");
    }
}
