package com.changyang.scheduling.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 过滤器更换计划 — Problem Fact
 * <p>
 * 记录过滤器更换时间及带来的停机时间。
 * 更换后 20 天内，有特定的型号优先排产规则（MC1 约束）。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FilterChangePlan {

    /** 产线 ID */
    private String lineId;

    /** 更换开始时间 */
    private LocalDateTime changeTime;

    /** 更换导致的停机分钟数 */
    private int downtimeMinutes;

    /**
     * 规则有效窗口结束时间 (changeTime + 20天)
     */
    public LocalDateTime getPriorityWindowEnd() {
        if (changeTime == null) return null;
        return changeTime.plusDays(20);
    }
}
