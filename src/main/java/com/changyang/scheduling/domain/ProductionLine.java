package com.changyang.scheduling.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.PlanningListVariable;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@PlanningEntity
public class ProductionLine {

    private String id; // "LINE_1", "LINE_2"
    private String name; // "一线", "二线"
    private String lineCode; // 用于匹配兼容产线
    private LocalDateTime availableFrom; // 产线可用开始时间

    @PlanningListVariable
    private List<MotherRollOrder> orders = new ArrayList<>();

    // 无参构造（Timefold 必需）
    public ProductionLine() {
    }

    public ProductionLine(String id, String name, String lineCode, LocalDateTime availableFrom) {
        this.id = id;
        this.name = name;
        this.lineCode = lineCode;
        this.availableFrom = availableFrom;
    }

    // --- Getters & Setters ---

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getLineCode() {
        return lineCode;
    }

    public void setLineCode(String lineCode) {
        this.lineCode = lineCode;
    }

    public LocalDateTime getAvailableFrom() {
        return availableFrom;
    }

    public void setAvailableFrom(LocalDateTime availableFrom) {
        this.availableFrom = availableFrom;
    }

    public List<MotherRollOrder> getOrders() {
        return orders;
    }

    public void setOrders(List<MotherRollOrder> orders) {
        this.orders = orders;
    }

    @Override
    public String toString() {
        return "ProductionLine{" + id + " - " + name + "}";
    }
}
