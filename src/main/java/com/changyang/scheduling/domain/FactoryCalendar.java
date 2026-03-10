package com.changyang.scheduling.domain;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

/**
 * 工厂日历 — Problem Fact
 * <p>
 * 定义工作日、休息日，提供跳过非工作时间的时间推算方法。
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FactoryCalendar {

    /** 休息日集合 */
    private Set<LocalDate> holidays;

    /**
     * 跳过非工作时间，推算实际的结束时间或开始时间
     * （当前为简化实现：暂不跳过节假日，按 7x24 小时计算，预留扩展口）
     *
     * @param time         起始时间
     * @param addMinutes   需要增加的分钟数
     * @return 推算后的时间
     */
    public LocalDateTime skipNonWorkingTime(LocalDateTime time, int addMinutes) {
        if (time == null) {
            return null;
        }
        // TODO: 结合 holidays 和班次时间进行跨日历推导计算
        return time.plusMinutes(addMinutes);
    }
}
