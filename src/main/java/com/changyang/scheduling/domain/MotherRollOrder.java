package com.changyang.scheduling.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.CascadingUpdateShadowVariable;
import ai.timefold.solver.core.api.domain.variable.IndexShadowVariable;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PreviousElementShadowVariable;

import java.time.LocalDateTime;
import java.util.List;

@PlanningEntity
public class MotherRollOrder {

    // ===== 问题数据（Problem Facts）=====
    private String id;
    private String productCode; // 型号，如 "T10ESY", "QDJY", "T61ESYH"
    private String formulaCode; // 配方编码
    private double thickness; // 厚度，用于轮转约束
    private int quantity;
    private double currentInventory;
    private double monthlyShipment;
    private LocalDateTime expectedStartTime;
    private List<String> compatibleLines; // 兼容产线 lineCode 列表
    private double productionDurationHours;

    // ===== 影子变量 1：所在产线 =====
    @InverseRelationShadowVariable(sourceVariableName = "orders")
    private ProductionLine assignedLine;

    // ===== 影子变量 2：在产线中的位置索引（0-based） =====
    @IndexShadowVariable(sourceVariableName = "orders")
    private Integer sequenceIndex;

    // ===== 影子变量 3：前一个生产订单（队列首个为 null）=====
    @PreviousElementShadowVariable(sourceVariableName = "orders")
    private MotherRollOrder previousOrder;

    // ===== 影子变量 4+5：级联计算开始/结束时间 =====
    @CascadingUpdateShadowVariable(targetMethodName = "updateStartAndEndTime")
    private LocalDateTime startTime;

    @CascadingUpdateShadowVariable(targetMethodName = "updateStartAndEndTime")
    private LocalDateTime endTime;

    // 无参构造（Timefold 必需）
    public MotherRollOrder() {
    }

    public MotherRollOrder(String id, String productCode, String formulaCode,
            double thickness, int quantity,
            double currentInventory, double monthlyShipment,
            LocalDateTime expectedStartTime,
            List<String> compatibleLines,
            double productionDurationHours) {
        this.id = id;
        this.productCode = productCode;
        this.formulaCode = formulaCode;
        this.thickness = thickness;
        this.quantity = quantity;
        this.currentInventory = currentInventory;
        this.monthlyShipment = monthlyShipment;
        this.expectedStartTime = expectedStartTime;
        this.compatibleLines = compatibleLines;
        this.productionDurationHours = productionDurationHours;
    }

    // ===== 级联更新回调 =====
    public void updateStartAndEndTime() {
        if (assignedLine == null) {
            startTime = null;
            endTime = null;
            return;
        }
        startTime = (previousOrder == null)
                ? assignedLine.getAvailableFrom()
                : previousOrder.getEndTime();
        if (startTime != null) {
            endTime = startTime.plusMinutes((long) (productionDurationHours * 60));
        }
    }

    // ===== 业务方法 =====

    /**
     * 计算库存可供应天数 = 现有库存量 / 月均出货量 * 30
     */
    public double getInventorySupplyDays() {
        if (monthlyShipment <= 0) {
            return Double.MAX_VALUE;
        }
        return (currentInventory / monthlyShipment) * 30.0;
    }

    /**
     * 判断该订单是否与指定产线兼容
     */
    public boolean isCompatibleWith(ProductionLine line) {
        return compatibleLines != null && compatibleLines.contains(line.getLineCode());
    }

    // ===== Getters & Setters =====

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getProductCode() {
        return productCode;
    }

    public void setProductCode(String productCode) {
        this.productCode = productCode;
    }

    public String getFormulaCode() {
        return formulaCode;
    }

    public void setFormulaCode(String formulaCode) {
        this.formulaCode = formulaCode;
    }

    public double getThickness() {
        return thickness;
    }

    public void setThickness(double thickness) {
        this.thickness = thickness;
    }

    public int getQuantity() {
        return quantity;
    }

    public void setQuantity(int quantity) {
        this.quantity = quantity;
    }

    public double getCurrentInventory() {
        return currentInventory;
    }

    public void setCurrentInventory(double currentInventory) {
        this.currentInventory = currentInventory;
    }

    public double getMonthlyShipment() {
        return monthlyShipment;
    }

    public void setMonthlyShipment(double monthlyShipment) {
        this.monthlyShipment = monthlyShipment;
    }

    public LocalDateTime getExpectedStartTime() {
        return expectedStartTime;
    }

    public void setExpectedStartTime(LocalDateTime expectedStartTime) {
        this.expectedStartTime = expectedStartTime;
    }

    public List<String> getCompatibleLines() {
        return compatibleLines;
    }

    public void setCompatibleLines(List<String> compatibleLines) {
        this.compatibleLines = compatibleLines;
    }

    public double getProductionDurationHours() {
        return productionDurationHours;
    }

    public void setProductionDurationHours(double productionDurationHours) {
        this.productionDurationHours = productionDurationHours;
    }

    public ProductionLine getAssignedLine() {
        return assignedLine;
    }

    public void setAssignedLine(ProductionLine assignedLine) {
        this.assignedLine = assignedLine;
    }

    public Integer getSequenceIndex() {
        return sequenceIndex;
    }

    public void setSequenceIndex(Integer sequenceIndex) {
        this.sequenceIndex = sequenceIndex;
    }

    public MotherRollOrder getPreviousOrder() {
        return previousOrder;
    }

    public void setPreviousOrder(MotherRollOrder previousOrder) {
        this.previousOrder = previousOrder;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    @Override
    public String toString() {
        return "MotherRollOrder{" + id + " " + productCode + " t=" + thickness + "}";
    }
}
