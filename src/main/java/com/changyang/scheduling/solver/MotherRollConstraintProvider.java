package com.changyang.scheduling.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import com.changyang.scheduling.domain.MotherRollOrder;
import com.changyang.scheduling.domain.ProductionLine;

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
}
