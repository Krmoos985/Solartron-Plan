package com.changyang.scheduling.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * 例外停机时间 — Problem Fact
 * <p>
 * 产线遭遇设备故障、保养等情况导致不可用的时间段。
 * 排程任务的时间不得与此重叠（对应 HC3 约束）。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExceptionTime {

    /** 发生停机的产线 ID */
    private String lineId;

    /** 停机开始时间 */
    private LocalDateTime startTime;

    /** 停机结束时间 */
    private LocalDateTime endTime;

}
