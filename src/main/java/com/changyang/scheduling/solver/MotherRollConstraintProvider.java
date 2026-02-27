package com.changyang.scheduling.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import com.changyang.scheduling.domain.MotherRollOrder;

import java.time.Duration;
import java.util.Set;

public class MotherRollConstraintProvider implements ConstraintProvider {

    // 白名单型号对：相邻生产可获得奖励（抵消换型惩罚）
    private static final Set<String> PREFERRED_ADJACENT_PAIRS = Set.of(
            "T10ESY|T61ESYH", "T61ESYH|T10ESY",
            "T29DJY|T29DJX", "T29DJX|T29DJY",
            "T42DJX|T9EST", "T9EST|T42DJX",
            "T29DJX|T42DJX", "T42DJX|T29DJX",
            "T29QDJY|T29DJY", "T29DJY|T29QDJY",
            "T24DJX|T24DJY", "T24DJY|T24DJX");

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[] {
                // Hard
                productLineMustBeCompatible(factory),
                thicknessMonotonicity(factory),
                // Medium
                prioritizeByInventorySupplyDays(factory),
                // Soft
                respectExpectedStartTime(factory),
                sameFormulaAndProductCodeAdjacent(factory),
                preferredAdjacentPairs(factory),
                qdjyPreferLine2(factory),
                esyhPreferLine4(factory)
        };
    }

    // ===== Hard 约束 =====

    /**
     * 订单必须在兼容的产线上生产
     */
    Constraint productLineMustBeCompatible(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getAssignedLine() != null
                        && !order.isCompatibleWith(order.getAssignedLine()))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("产线兼容性");
    }

    /**
     * 厚度单调性：同一产线内，厚度方向不能反转。
     * 通过三元组(prev→curr→next)检测方向反转。
     */
    Constraint thicknessMonotonicity(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(curr -> curr.getPreviousOrder() != null && curr.getAssignedLine() != null)
                .join(MotherRollOrder.class,
                        Joiners.equal(curr -> curr, next -> next.getPreviousOrder()))
                .filter((curr, next) -> sameLine(curr, next) && isDirectionReversed(curr, next))
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("厚度单调性");
    }

    // ===== Medium 约束 =====

    /**
     * 库存天数短的订单应排序靠前。
     * 对同一产线上的每一对"逆序"订单施加惩罚。
     */
    Constraint prioritizeByInventorySupplyDays(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getPreviousOrder() != null && order.getAssignedLine() != null)
                .filter(order -> {
                    double prevDays = order.getPreviousOrder().getInventorySupplyDays();
                    double currDays = order.getInventorySupplyDays();
                    // 如果前一个的库存天数反而比当前多很多，说明优先级错乱
                    return prevDays > currDays;
                })
                .penalize(HardMediumSoftScore.ONE_MEDIUM,
                        order -> (int) Math.round(
                                order.getPreviousOrder().getInventorySupplyDays()
                                        - order.getInventorySupplyDays()))
                .asConstraint("库存天数优先级");
    }

    // ===== Soft 约束 =====

    /**
     * 尊重期望开始时间：实际开始晚于期望开始时间，按分钟数惩罚
     */
    Constraint respectExpectedStartTime(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getStartTime() != null
                        && order.getExpectedStartTime() != null
                        && order.getStartTime().isAfter(order.getExpectedStartTime()))
                .penalize(HardMediumSoftScore.ONE_SOFT,
                        order -> (int) Duration.between(
                                order.getExpectedStartTime(),
                                order.getStartTime()).toMinutes())
                .asConstraint("期望开始时间偏差");
    }

    /**
     * 换型惩罚：相邻订单如果型号或配方不同，惩罚 -10
     */
    Constraint sameFormulaAndProductCodeAdjacent(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getPreviousOrder() != null && order.getAssignedLine() != null)
                .filter(order -> {
                    MotherRollOrder prev = order.getPreviousOrder();
                    return !order.getProductCode().equals(prev.getProductCode())
                            || !order.getFormulaCode().equals(prev.getFormulaCode());
                })
                .penalize(HardMediumSoftScore.ofSoft(10))
                .asConstraint("换型惩罚");
    }

    /**
     * 白名单型号对相邻奖励 +10，与换型惩罚抵消
     */
    Constraint preferredAdjacentPairs(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getPreviousOrder() != null && order.getAssignedLine() != null)
                .filter(order -> {
                    String pairKey = order.getPreviousOrder().getProductCode()
                            + "|" + order.getProductCode();
                    return PREFERRED_ADJACENT_PAIRS.contains(pairKey);
                })
                .reward(HardMediumSoftScore.ofSoft(10))
                .asConstraint("优选型号对奖励");
    }

    /**
     * QDJY 型号优先在 LINE_2 生产
     */
    Constraint qdjyPreferLine2(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getAssignedLine() != null
                        && order.getProductCode() != null
                        && order.getProductCode().contains("QDJY")
                        && !"LINE_2".equals(order.getAssignedLine().getLineCode()))
                .penalize(HardMediumSoftScore.ofSoft(5))
                .asConstraint("QDJY优先二线");
    }

    /**
     * ESYH 型号优先在 LINE_4 生产
     */
    Constraint esyhPreferLine4(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getAssignedLine() != null
                        && order.getProductCode() != null
                        && order.getProductCode().contains("ESYH")
                        && !"LINE_4".equals(order.getAssignedLine().getLineCode()))
                .penalize(HardMediumSoftScore.ofSoft(5))
                .asConstraint("ESYH优先四线");
    }

    // ===== 辅助方法 =====

    /**
     * 判断两个订单是否在同一条产线上
     */
    private boolean sameLine(MotherRollOrder a, MotherRollOrder b) {
        return a.getAssignedLine() != null
                && b.getAssignedLine() != null
                && a.getAssignedLine().getId().equals(b.getAssignedLine().getId());
    }

    /**
     * 检测厚度方向是否反转。
     * prev→curr 的方向与 curr→next 的方向不同，则反转。
     * curr 是中间节点，next 是 join 进来的后继节点。
     */
    private boolean isDirectionReversed(MotherRollOrder curr, MotherRollOrder next) {
        MotherRollOrder prev = curr.getPreviousOrder();
        double dir1 = curr.getThickness() - prev.getThickness();
        double dir2 = next.getThickness() - curr.getThickness();
        // 方向为零（相等）不算反转
        if (dir1 == 0 || dir2 == 0) {
            return false;
        }
        // 一正一负表示方向反转
        return (dir1 > 0 && dir2 < 0) || (dir1 < 0 && dir2 > 0);
    }
}
