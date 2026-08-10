package com.sphp.patient.common.constant;

import java.time.LocalTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

public class CHealthConstant {

    // 从处方频次中提取每日次数的首个阿拉伯数字
    public static final Pattern FREQUENCY_NUMBER_PATTERN = Pattern.compile("(\\d+)");

    // C 端用药提醒统一以中国标准时间计算
    public static final ZoneId SHANGHAI_ZONE_ID = ZoneId.of("Asia/Shanghai");

    // 每日一次的默认提醒时刻
    public static final List<LocalTime> ONCE_DAILY = List.of(LocalTime.of(8, 0));

    // 每日两次的默认提醒时刻
    public static final List<LocalTime> TWICE_DAILY = List.of(LocalTime.of(8, 0), LocalTime.of(20, 0));

    // 每日三次的默认提醒时刻
    public static final List<LocalTime> THREE_TIMES_DAILY = List.of(LocalTime.of(8, 0), LocalTime.of(14, 0), LocalTime.of(20, 0));
    // 每日四次的默认提醒时刻

    public static final List<LocalTime> FOUR_TIMES_DAILY = List.of(LocalTime.of(8, 0), LocalTime.of(12, 0), LocalTime.of(16, 0), LocalTime.of(20, 0));
    // 支持的每日服药次数与日间提醒时刻映射

    public static final Map<Integer, List<LocalTime>> REMINDER_TIME_MAPPING = Map.of(
            1, ONCE_DAILY,
            2, TWICE_DAILY,
            3, THREE_TIMES_DAILY,
            4, FOUR_TIMES_DAILY
    );

}
