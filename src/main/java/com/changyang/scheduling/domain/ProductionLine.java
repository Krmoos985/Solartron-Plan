package com.changyang.scheduling.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * 产线 — Planning Entity
 * <p>
 * 拥有 @PlanningListVariable，Solver 决定将哪些 MotherRollOrder 放入该产线的列表中，以及顺序。
 * 对应 VRP 中的 Vehicle 角色。
 * </p>
 */
@PlanningEntity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ProductionLine {

    /** 产线 ID，如 "LINE_1", "LINE_2" */
    private String id;

    /** 产线名称，如 "一线", "二线" */
    private String name;

    /** 产线编码，用于约束匹配（如 QDJY 优先 LINE_2） */
    private String lineCode;

    /** 产线可用开始时间 */
    private LocalDateTime availableFrom;

    /** 日产能（吨/天） */
    private double dailyCapacity;

    /**
     * 产线上的有序任务列表 — Solver 的核心决策变量
     */
    @PlanningListVariable
    private List<MotherRollOrder> orders = new ArrayList<>();

    /**
     * 便捷构造：仅需 ID 和名称
     */
    public ProductionLine(String id, String name, String lineCode, LocalDateTime availableFrom) {
        this.id = id;
        this.name = name;
        this.lineCode = lineCode;
        this.availableFrom = availableFrom;
    }

    @Override
    public String toString() {
        return "ProductionLine{" + id + " '" + name + "'}";
    }
}
