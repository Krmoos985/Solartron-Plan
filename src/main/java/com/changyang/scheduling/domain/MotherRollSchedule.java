package com.changyang.scheduling.domain;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.constraintweight.ConstraintConfigurationProvider;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import com.changyang.scheduling.solver.SchedulingConstraintConfiguration;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.List;

/**
 * 母卷排程方案 — Planning Solution
 * <p>
 * 聚合了所有产线（Planning Entity）和所有订单（Planning Value），
 * 以及评分结果。Solver 在此基础上求解最优分配方案。
 * </p>
 */
@PlanningSolution
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MotherRollSchedule {

    /**
     * 所有产线（Planning Entity，拥有 @PlanningListVariable）
     */
    @PlanningEntityCollectionProperty
    private List<ProductionLine> productionLines;

    /**
     * 所有母卷订单
     * <p>
     * 双重角色：
     * - @PlanningEntityCollectionProperty：因为 MotherRollOrder 拥有影子变量
     * - @ValueRangeProvider：作为 @PlanningListVariable 的值范围
     * </p>
     */
    @PlanningEntityCollectionProperty
    @ValueRangeProvider
    private List<MotherRollOrder> orders;

    /**
     * 排程评分（Hard / Medium / Soft 三级）
     */
    @PlanningScore
    private HardMediumSoftScore score;

    @ConstraintConfigurationProvider
    private SchedulingConstraintConfiguration constraintConfiguration = SchedulingConstraintConfiguration.defaults();

    // ==================== 问题事实 (Problem Facts) ====================

    @ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty
    private List<com.changyang.scheduling.domain.ExceptionTime> exceptionTimes;

    @ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty
    private List<com.changyang.scheduling.domain.FilterChangePlan> filterChangePlans;

    @ai.timefold.solver.core.api.domain.solution.ProblemFactCollectionProperty
    private java.util.List<com.changyang.scheduling.domain.ChangeoverEntry> changeoverEntries;

    @ai.timefold.solver.core.api.domain.solution.ProblemFactProperty
    private com.changyang.scheduling.domain.FactoryCalendar factoryCalendar;

    /**
     * 基础便捷构造
     */
    public MotherRollSchedule(List<ProductionLine> productionLines, List<MotherRollOrder> orders) {
        this.productionLines = productionLines;
        this.orders = orders;
        this.constraintConfiguration = SchedulingConstraintConfiguration.defaults();
        this.exceptionTimes = new java.util.ArrayList<>();
        this.filterChangePlans = new java.util.ArrayList<>();
        this.changeoverEntries = new java.util.ArrayList<>();
        this.factoryCalendar = new com.changyang.scheduling.domain.FactoryCalendar();
    }
}
