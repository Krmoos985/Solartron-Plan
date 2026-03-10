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
                subTaskMustMaintainOrder(factory),        // HC5 同一母任务的子任务保序
                noOverlapWithExceptionTime(factory),      // HC3 停机时段不可重叠

                // ===== 中等约束 =====
                // TODO Phase 3: MC1, MC2

                // ===== 软约束 =====
                // TODO Phase 3: SC1-SC5
        };
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
}
