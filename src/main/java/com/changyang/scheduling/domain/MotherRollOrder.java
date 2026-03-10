package com.changyang.scheduling.domain;

import ai.timefold.solver.core.api.domain.entity.PlanningEntity;
import ai.timefold.solver.core.api.domain.variable.CascadingUpdateShadowVariable;
import ai.timefold.solver.core.api.domain.variable.IndexShadowVariable;
import ai.timefold.solver.core.api.domain.variable.InverseRelationShadowVariable;
import ai.timefold.solver.core.api.domain.variable.PreviousElementShadowVariable;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import ai.timefold.solver.core.api.domain.lookup.PlanningId;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

/**
 * 母卷订单 — Planning Value + Planning Entity（因为拥有影子变量）
 * <p>
 * 既是 @PlanningListVariable 的值（被放入产线列表），
 * 又是 @PlanningEntity（拥有影子变量 assignedLine、sequenceIndex 等）。
 * </p>
 */
@PlanningEntity
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class MotherRollOrder {

    // ==================== 问题数据（Problem Facts） ====================

    /** 订单ID */
    @PlanningId
    private String id;

    /** 物料编码 */
    private String materialCode;

    /** 型号，如 "T10ESY", "QDJY", "T61ESYH" */
    private String productCode;

    /** 配方编码 */
    private String formulaCode;

    /** 厚度（微米），用于轮转约束 */
    private int thickness;

    /** 生产数量 */
    private double quantity;

    /** 生产时长（小时） */
    private double productionDurationHours;

    /** 当前库存量 */
    private double currentInventory;

    /** 月度发货量 */
    private double monthlyShipment;

    /** 期望开始生产时间 */
    private LocalDateTime expectedStartTime;

    /** 兼容产线编码列表 */
    private Set<String> compatibleLines;

    /** 优先产线编码 */
    private String preferredLineCode;

    // ==================== 拆分相关（日颗粒度） ====================

    /** 归属的母任务 ID，未拆分的为 null */
    private String parentTaskId;

    /** 第几天子任务（1, 2, 3...） */
    private int dayIndex;

    /** 母任务总天数 */
    private int totalDays;

    /** 是否为拆分产生的子任务 */
    private boolean split;

    // ==================== 锁定（Pinning） ====================

    /** 是否已锁定（已下达/冻结的任务不参与重排） */
    private boolean pinned;

    // ==================== 影子变量 ====================

    /** 影子变量 1：当前分配到的产线（由 Timefold 自动维护） */
    @InverseRelationShadowVariable(sourceVariableName = "orders")
    private ProductionLine assignedLine;

    /** 影子变量 2：在产线列表中的索引位置（0-based） */
    @IndexShadowVariable(sourceVariableName = "orders")
    private Integer sequenceIndex;

    /** 影子变量 3：前一个订单（队首为 null） */
    @PreviousElementShadowVariable(sourceVariableName = "orders")
    private MotherRollOrder previousOrder;

    /** 影子变量 4：计划开始时间（级联计算） */
    @CascadingUpdateShadowVariable(targetMethodName = "updateStartAndEndTime")
    private LocalDateTime startTime;

    /** 影子变量 5：计划结束时间（级联计算） */
    @CascadingUpdateShadowVariable(targetMethodName = "updateStartAndEndTime")
    private LocalDateTime endTime;

    /** 换型时间（分钟），在级联更新中计算 */
    @CascadingUpdateShadowVariable(targetMethodName = "updateStartAndEndTime")
    private Integer changeoverMinutes;

    // ==================== 级联更新回调 ====================

    /**
     * 当 assignedLine / previousOrder 变化时，Timefold 自动调用此方法。
     * 从变化点向后级联传播，重新计算 startTime、endTime、changeoverMinutes。
     * <p>
     * 注意：当前为简化版实现，未跳过非工作时间、未查询换型矩阵。
     * Phase 1 完善后将注入 ChangeoverService 和 FactoryCalendar。
     * </p>
     */
    public void updateStartAndEndTime() {
        if (assignedLine == null) {
            startTime = null;
            endTime = null;
            changeoverMinutes = null;
            return;
        }

        // 1. 换型时间计算
        // 尝试从 ChangeoverService 获取，若其尚未初始化（如普通单测），则降级到简单计算
        if (com.changyang.scheduling.service.ChangeoverService.getInstance() != null) {
            changeoverMinutes = com.changyang.scheduling.service.ChangeoverService.getInstance().calcChangeover(previousOrder, this);
        } else {
            changeoverMinutes = calcSimpleChangeover(previousOrder);
        }

        // 2. 计划开始时间
        if (previousOrder == null) {
            startTime = assignedLine.getAvailableFrom();
        } else {
            LocalDateTime prevEnd = previousOrder.getEndTime();
            startTime = (prevEnd != null) ? prevEnd.plusMinutes(changeoverMinutes) : null;
        }

        // 3. 计算结束时间
        // TODO Phase 1 进阶：注入 FactoryCalendar 跳过非工作时间（目前采用 7x24 简单 plus）
        if (startTime != null) {
            endTime = startTime.plusMinutes((long) (productionDurationHours * 60));
        } else {
            endTime = null;
        }
    }

    /**
     * 降级时的简化计算
     */
    private int calcSimpleChangeover(MotherRollOrder prev) {
        if (prev == null) return 0;
        if (prev.getFormulaCode().equals(this.formulaCode)
                && prev.getProductCode().equals(this.productCode)
                && prev.getThickness() == this.thickness) {
            return 0; // 同配方同型号同厚度
        }
        return 30; // 默认 30 分钟
    }

    // ==================== 业务方法 ====================

    /**
     * 计算库存可供应天数
     */
    public double getInventorySupplyDays() {
        if (monthlyShipment <= 0) return Double.MAX_VALUE;
        return (currentInventory / monthlyShipment) * 30.0;
    }

    /**
     * 判断是否兼容指定产线
     */
    public boolean isCompatibleWith(ProductionLine line) {
        return compatibleLines != null && compatibleLines.contains(line.getLineCode());
    }

    /**
     * 判断是否为第一天子任务（用于 HC2 判断）
     */
    public boolean isFirstDayTask() {
        return !split || dayIndex == 1;
    }

    @Override
    public String toString() {
        return "MotherRollOrder{" + id +
                " " + productCode +
                " t=" + thickness +
                (split ? " day=" + dayIndex + "/" + totalDays : "") +
                "}";
    }
}
