package com.changyang.scheduling.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import com.changyang.scheduling.domain.MotherRollOrder;

/**
 * 母卷排程约束提供者
 * <p>
 * 定义所有硬约束（HC1-HC5）、中等约束（MC1-MC2）和软约束（SC1-SC5）。
 * Phase 3 将逐一实现每个约束方法。
 * </p>
 */
public class MotherRollConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                // ===== 硬约束 =====
                productLineMustBeCompatible(factory),     // HC1 产品-产线兼容
                urgentInventoryMustBePrioritized(factory),// HC2 库存<10天强制优先
                noOverlapWithExceptionTime(factory),      // HC3 停机时段不可重叠
                thicknessMonotonicity(factory),           // HC4 厚度轮转约束（单调递增或递减）
                subTaskMustMaintainOrder(factory),        // HC5 同一母任务的子任务保序

                // ===== 中等约束 =====
                filterChangePreferredOrder(factory),      // MC1 过滤器后20天优先顺序
                highInventoryShouldNotBeAdvanced(factory),// MC2 库存>30天不前移

                // ===== 软约束 =====
                minimizeChangeoverTime(factory),          // SC1 换型时间最小化
                prioritizeByInventorySupplyDays(factory), // SC2 库存天数优先级
                respectExpectedStartTime(factory),        // SC3 期望时间偏差
                preferredLineMatch(factory),              // SC4特定产线偏好
                continuousProduction(factory)             // SC5 连续生产
        };
    }

    // （保留原有的 HC1-HC5, MC1-MC2 方法，此处由于篇幅仅用新方法覆盖需要替换的部分，所以不能替换掉完整的 defineConstraints 下方所有代码。这要求我分两次或者精确定位。）

    // ===== HC2：库存<10天强制优先 =====

    /**
     * HC2：如果一个订单库存可维持天数 < 10 天，它必须排在那些 >= 10 天的订单前面。
     * 检测方式：同产线上，排在前面的不紧急，排在后面的紧急，即为倒置违规。
     */
    Constraint urgentInventoryMustBePrioritized(ConstraintFactory factory) {
        return factory.forEachUniquePair(MotherRollOrder.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(order -> 
                                order.getAssignedLine() == null ? null : order.getAssignedLine().getId()))
                .filter((o1, o2) -> {
                    if (o1.getSequenceIndex() == null || o2.getSequenceIndex() == null) return false;

                    boolean o1Urgent = o1.getInventorySupplyDays() < 10.0;
                    boolean o2Urgent = o2.getInventorySupplyDays() < 10.0;
                    
                    if (o1Urgent == o2Urgent) return false;

                    if (o1.getSequenceIndex() < o2.getSequenceIndex()) {
                        return !o1Urgent && o2Urgent; // o1 先，且 o1 不紧急、o2 紧急
                    } else if (o2.getSequenceIndex() < o1.getSequenceIndex()) {
                        return !o2Urgent && o1Urgent; // o2 先，且 o2 不紧急、o1 紧急
                    }
                    return false;
                })
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("HC2-紧急库存优先");
    }

    // ===== HC4：厚度轮转约束 =====

    /**
     * HC4：同一产线内的厚度变化必须严格单调（一直增加或一直减少，允许平级）。
     * 巧妙解法：分别提取出所有向上的厚度台阶（UP）与向下的厚度台阶（DOWN）。
     * 如果在同一产线上同时存在 UP 台阶和 DOWN 台阶，说明单调性被打破（出现了 V 形或 ^ 形折返）。
     * UP 数量与 DOWN 数量的笛卡尔积即为惩罚数，提供了完美的演化梯度。
     */
    Constraint thicknessMonotonicity(ConstraintFactory factory) {
        var upSteps = factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getPreviousOrder() != null 
                        && order.getPreviousOrder().getThickness() < order.getThickness());

        var downSteps = factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getPreviousOrder() != null 
                        && order.getPreviousOrder().getThickness() > order.getThickness());

        return upSteps.join(downSteps,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(order -> 
                                order.getAssignedLine() == null ? null : order.getAssignedLine().getId()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("HC4-厚度单调流向");
    }

    // ===== MC1：过滤器后20天优先顺序 =====

    private static final java.util.Map<String, Integer> FILTER_PREFERRED_ORDER = java.util.Map.of(
            "T19EST", 1,
            "T4FDX", 2,
            "T4FDY", 2,
            "T5FDX", 3,
            "T7FDX", 4
    );

    /**
     * MC1：换过滤器后20天内，优先生产特定的型号（按预设字典排序）。
     * 如果处于20天保护期内出现非预期倒装，每跨越一级扣除 1 Medium。
     */
    Constraint filterChangePreferredOrder(ConstraintFactory factory) {
        return factory.forEachUniquePair(MotherRollOrder.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(order -> 
                                order.getAssignedLine() == null ? null : order.getAssignedLine().getId()))
                .filter((o1, o2) -> {
                    if (o1.getSequenceIndex() == null || o2.getSequenceIndex() == null) return false;
                    Integer rank1 = FILTER_PREFERRED_ORDER.get(o1.getProductCode());
                    Integer rank2 = FILTER_PREFERRED_ORDER.get(o2.getProductCode());
                    if (rank1 == null || rank2 == null) return false;
                    
                    if (o1.getSequenceIndex() < o2.getSequenceIndex()) {
                        return rank1 > rank2; // rank 数值越小代表越优先，若先执行的任务 rank 更大即为倒置
                    } else if (o2.getSequenceIndex() < o1.getSequenceIndex()) {
                        return rank2 > rank1; // o2 在前，它却更大，违规
                    }
                    return false;
                })
                .join(com.changyang.scheduling.domain.FilterChangePlan.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal((o1, o2) -> o1.getAssignedLine().getId(), 
                                com.changyang.scheduling.domain.FilterChangePlan::getLineId))
                .filter((o1, o2, filter) -> {
                    if (o1.getStartTime() == null || o2.getStartTime() == null) return false;
                    long d1 = java.time.temporal.ChronoUnit.DAYS.between(filter.getChangeTime(), o1.getStartTime());
                    long d2 = java.time.temporal.ChronoUnit.DAYS.between(filter.getChangeTime(), o2.getStartTime());
                    return d1 >= 0 && d1 <= 20 && d2 >= 0 && d2 <= 20;
                })
                .penalize(HardMediumSoftScore.ONE_MEDIUM, (o1, o2, filter) -> {
                    int rank1 = FILTER_PREFERRED_ORDER.get(o1.getProductCode());
                    int rank2 = FILTER_PREFERRED_ORDER.get(o2.getProductCode());
                    return Math.abs(rank1 - rank2);
                })
                .asConstraint("MC1-过滤器后20天优先顺序");
    }

    // ===== MC2：库存>30天不前移 =====

    /**
     * MC2：库存>30天的订单不应提前生产。
     * 如果实际开始时间早于预期开始时间，按照提前的小时数处于中等程度惩罚。
     */
    Constraint highInventoryShouldNotBeAdvanced(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getStartTime() != null 
                        && order.getExpectedStartTime() != null
                        && order.getInventorySupplyDays() > 30.0
                        && order.getStartTime().isBefore(order.getExpectedStartTime()))
                .penalize(HardMediumSoftScore.ONE_MEDIUM, order -> 
                        (int) java.time.temporal.ChronoUnit.HOURS.between(order.getStartTime(), order.getExpectedStartTime()))
                .asConstraint("MC2-高库存不前移");
    }

    // ===== HC1：产品-产线兼容性 =====

    /**
     * HC1：订单必须分配到兼容产线上。
     * 如果订单的 compatibleLines 不包含所分配产线的 lineCode，则违反硬约束。
     */
    Constraint productLineMustBeCompatible(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getAssignedLine() != null
                        && !order.isCompatibleWith(order.getAssignedLine()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("HC1-产品产线兼容");
    }

    // ===== HC5：同产线子任务保序 =====

    /**
     * HC5：如果同一个母任务被拆分为多个子任务，
     * 它们在同一个产线上必须按照 dayIndex 的顺序排列。
     * 即排在前面的子任务的 dayIndex 必须严格小于排在后面的。
     */
    Constraint subTaskMustMaintainOrder(ConstraintFactory factory) {
        return factory.forEachUniquePair(MotherRollOrder.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(MotherRollOrder::getParentTaskId))
                .filter((o1, o2) -> {
                    if (o1.getAssignedLine() == null || o2.getAssignedLine() == null) return false;
                    if (!o1.getAssignedLine().getId().equals(o2.getAssignedLine().getId())) return false;
                    if (o1.getSequenceIndex() == null || o2.getSequenceIndex() == null) return false;
                    
                    if (o1.getSequenceIndex() < o2.getSequenceIndex()) {
                        return o1.getDayIndex() >= o2.getDayIndex(); // o1 排在前面，但 dayIndex >= o2，说明倒序
                    } else if (o2.getSequenceIndex() < o1.getSequenceIndex()) {
                        return o2.getDayIndex() >= o1.getDayIndex(); // o2 排在前面，但 dayIndex >= o1，说明倒序
                    }
                    return false;
                })
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("HC5-子任务保序");
    }

    // ===== HC3：停机冲突 =====

    /**
     * HC3：生产任务的时间段不能与例外停机时间（ExceptionTime）重叠。
     * 重叠分钟数即为惩罚值。
     */
    Constraint noOverlapWithExceptionTime(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getStartTime() != null && order.getEndTime() != null)
                .join(com.changyang.scheduling.domain.ExceptionTime.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(
                                order -> order.getAssignedLine().getId(),
                                com.changyang.scheduling.domain.ExceptionTime::getLineId))
                .filter((order, exception) -> 
                        order.getStartTime().isBefore(exception.getEndTime()) && 
                        order.getEndTime().isAfter(exception.getStartTime())) // 时间有重叠片段
                .penalize(HardMediumSoftScore.ONE_HARD, (order, exception) -> {
                    java.time.LocalDateTime overlapStart = order.getStartTime().isAfter(exception.getStartTime()) ? order.getStartTime() : exception.getStartTime();
                    java.time.LocalDateTime overlapEnd = order.getEndTime().isBefore(exception.getEndTime()) ? order.getEndTime() : exception.getEndTime();
                    return (int) java.time.temporal.ChronoUnit.MINUTES.between(overlapStart, overlapEnd);
                })
                .asConstraint("HC3-停机冲突");
    }

    // ===== SC1：换型时间最小化 =====

    /**
     * SC1：每次换型时，惩罚等同于换型分钟数。
     * （通过降维简化：直接读取 MotherRollOrder 影子变量上的 changeoverMinutes）
     */
    Constraint minimizeChangeoverTime(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getPreviousOrder() != null && order.getChangeoverMinutes() != null && order.getChangeoverMinutes() > 0)
                .penalize(HardMediumSoftScore.ONE_SOFT, MotherRollOrder::getChangeoverMinutes)
                .asConstraint("SC1-换型最小化");
    }

    // ===== SC2：库存天数优先级 =====

    /**
     * SC2：同型号优先排库存天数低的，倒置将惩罚天数差值。
     */
    Constraint prioritizeByInventorySupplyDays(ConstraintFactory factory) {
        return factory.forEachUniquePair(MotherRollOrder.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(order -> 
                                order.getAssignedLine() == null ? null : order.getAssignedLine().getId()))
                .filter((o1, o2) -> {
                    if (o1.getSequenceIndex() == null || o2.getSequenceIndex() == null) return false;
                    if (o1.getSequenceIndex() < o2.getSequenceIndex()) {
                        return o1.getInventorySupplyDays() > o2.getInventorySupplyDays();
                    } else if (o2.getSequenceIndex() < o1.getSequenceIndex()) {
                        return o2.getInventorySupplyDays() > o1.getInventorySupplyDays();
                    }
                    return false;
                })
                .penalize(HardMediumSoftScore.ofSoft(2), (o1, o2) -> {
                    double diff = Math.abs(o1.getInventorySupplyDays() - o2.getInventorySupplyDays());
                    return (int) diff;
                })
                .asConstraint("SC2-库存天数优先级");
    }

    // ===== SC3：期望生产时间偏差 =====

    /**
     * SC3：实际生产时间不要晚于期望时间，按晚的小时数惩罚。
     */
    Constraint respectExpectedStartTime(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getStartTime() != null && order.getExpectedStartTime() != null
                        && order.getStartTime().isAfter(order.getExpectedStartTime())) // 晚于期望时间
                .penalize(HardMediumSoftScore.ONE_SOFT, order -> 
                        (int) java.time.temporal.ChronoUnit.HOURS.between(order.getExpectedStartTime(), order.getStartTime()))
                .asConstraint("SC3-期望时间偏差");
    }

    // ===== SC4：特定产线偏好 =====
    
    /**
     * SC4：特定型号如果有偏好的产线代码（preferredLineCode），不在该线则惩罚。
     */
    Constraint preferredLineMatch(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getAssignedLine() != null 
                        && order.getPreferredLineCode() != null 
                        && !order.getAssignedLine().getLineCode().equals(order.getPreferredLineCode()))
                .penalize(HardMediumSoftScore.ONE_SOFT)
                .asConstraint("SC4-特定产线偏好");
    }

    // ===== SC5：拆分子任务连续生产 =====

    /**
     * SC5：从同一个订单拆分出的不同 dayIndex 子任务，尽力做到连续。
     * 同一条产线上被插队惩罚 10，跨产线生产惩罚 1000。
     */
    Constraint continuousProduction(ConstraintFactory factory) {
        return factory.forEachUniquePair(MotherRollOrder.class,
                        ai.timefold.solver.core.api.score.stream.Joiners.equal(MotherRollOrder::getParentTaskId))
                .filter((o1, o2) -> {
                    // 只检查逻辑上应该是相邻的两天任务，比如 day 1 和 day 2。避免多重惩罚。
                    if (o1.getParentTaskId() == null || Math.abs(o1.getDayIndex() - o2.getDayIndex()) != 1) return false;
                    if (o1.getAssignedLine() == null || o2.getAssignedLine() == null) return false;
                    
                    // 如果不在同一条线，直接违规
                    if (!o1.getAssignedLine().getId().equals(o2.getAssignedLine().getId())) return true;
                    
                    // 如果在同一条线，但 sequenceIndex 不连续，也即被人插队了，也违规
                    if (o1.getSequenceIndex() == null || o2.getSequenceIndex() == null) return false;
                    return Math.abs(o1.getSequenceIndex() - o2.getSequenceIndex()) > 1;
                })
                .penalize(HardMediumSoftScore.ONE_SOFT, (o1, o2) -> {
                    if (!o1.getAssignedLine().getId().equals(o2.getAssignedLine().getId())) {
                        return 1000; // 跨产线
                    }
                    return 10; // 同线插队
                })
                .asConstraint("SC5-拆分子任务连续生产");
    }
}
