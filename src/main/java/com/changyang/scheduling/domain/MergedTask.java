package com.changyang.scheduling.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 合并后的生产任务（DTO）
 * <p>
 * 经过 Solver 排程后，同产线上连续的同一母任务的子任务，
 * 会被 TaskMerger 重新合并为此类的实例，供下游系统或前端展示。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MergedTask {

    /** 原始母任务 ID */
    private String originalTaskId;

    /** 所属产线 ID */
    private String lineId;

    /** 计划开始时间（合并块的首个子任务开始时间） */
    private LocalDateTime plannedStart;

    /** 计划结束时间（合并块的末个子任务结束时间） */
    private LocalDateTime plannedEnd;

    /** 该合并块包含的子任务数量（即覆盖的天数天跨度） */
    private int daysCovered;

    /** 母任务最初需要排产的总天数 */
    private int totalDays;

}
