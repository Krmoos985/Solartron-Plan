package com.changyang.scheduling.domain;

import ai.timefold.solver.core.api.domain.solution.PlanningEntityCollectionProperty;
import ai.timefold.solver.core.api.domain.solution.PlanningScore;
import ai.timefold.solver.core.api.domain.solution.PlanningSolution;
import ai.timefold.solver.core.api.domain.valuerange.ValueRangeProvider;
import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;

import java.util.List;

@PlanningSolution
public class MotherRollSchedule {

    @PlanningEntityCollectionProperty
    private List<ProductionLine> productionLines;

    // 双重角色：既是 PlanningEntity（影子变量），也是 @PlanningListVariable 的值范围
    @PlanningEntityCollectionProperty
    @ValueRangeProvider
    private List<MotherRollOrder> orders;

    @PlanningScore
    private HardMediumSoftScore score;

    // 无参构造
    public MotherRollSchedule() {
    }

    public MotherRollSchedule(List<ProductionLine> productionLines, List<MotherRollOrder> orders) {
        this.productionLines = productionLines;
        this.orders = orders;
    }

    // --- Getters & Setters ---

    public List<ProductionLine> getProductionLines() {
        return productionLines;
    }

    public void setProductionLines(List<ProductionLine> productionLines) {
        this.productionLines = productionLines;
    }

    public List<MotherRollOrder> getOrders() {
        return orders;
    }

    public void setOrders(List<MotherRollOrder> orders) {
        this.orders = orders;
    }

    public HardMediumSoftScore getScore() {
        return score;
    }

    public void setScore(HardMediumSoftScore score) {
        this.score = score;
    }
}
