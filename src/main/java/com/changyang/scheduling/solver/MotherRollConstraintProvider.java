package com.changyang.scheduling.solver;

import ai.timefold.solver.core.api.score.buildin.hardmediumsoft.HardMediumSoftScore;
import ai.timefold.solver.core.api.score.stream.Constraint;
import ai.timefold.solver.core.api.score.stream.ConstraintCollectors;
import ai.timefold.solver.core.api.score.stream.ConstraintFactory;
import ai.timefold.solver.core.api.score.stream.ConstraintProvider;
import ai.timefold.solver.core.api.score.stream.Joiners;
import com.changyang.scheduling.domain.ExceptionTime;
import com.changyang.scheduling.domain.FilterChangePlan;
import com.changyang.scheduling.domain.MotherRollOrder;

import java.time.temporal.ChronoUnit;
import java.util.Map;

/**
 * 母卷排程约束提供者
 * <p>
 * 定义所有硬约束（HC1-HC5）、中等约束（MC1-MC2）和软约束（SC1-SC5）。
 * </p>
 */
public class MotherRollConstraintProvider implements ConstraintProvider {

    @Override
    public Constraint[] defineConstraints(ConstraintFactory factory) {
        return new Constraint[]{
                productLineMustBeCompatible(factory),
                urgentInventoryMustBePrioritized(factory),
                noOverlapWithExceptionTime(factory),
                thicknessSinglePeak(factory),
                filterChangePreferredOrder(factory),
                highInventoryShouldNotBeAdvanced(factory),
                minimizeChangeoverTime(factory),
                prioritizeByInventorySupplyDays(factory),
                respectExpectedStartTime(factory),
                preferredLineMatch(factory)
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
                .penalizeConfigurable()
                .asConstraint(SchedulingConstraintIds.HC1);
    }

    // ===== HC2：库存<10天强制优先（只看第一天子任务） =====

    /**
     * HC2：如果一个订单库存可维持天数 < 10 天，该任务的第一天子任务必须排在
     * 非紧急任务（首日子任务）前面。后续天数的子任务不需要强制最前。
     *
     * 修正点（Codex Review P3）：增加 isFirstDayTask() 过滤，
     * 只有 dayIndex=1 或未拆分的任务才参与紧急度比较。
     */
    Constraint urgentInventoryMustBePrioritized(ConstraintFactory factory) {
        return factory.forEachUniquePair(MotherRollOrder.class,
                        Joiners.equal(order ->
                                order.getAssignedLine() == null ? null : order.getAssignedLine().getId()))
                .filter((o1, o2) -> {
                    if (o1.getSequenceIndex() == null || o2.getSequenceIndex() == null) return false;

                    // 只看首日子任务（dayIndex=1 或未拆分的任务）
                    if (!o1.isFirstDayTask() || !o2.isFirstDayTask()) return false;

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
                .penalizeConfigurable()
                .asConstraint(SchedulingConstraintIds.HC2);
    }

    // ===== HC3：停机冲突 =====

    /**
     * HC3：生产任务的时间段不能与例外停机时间（ExceptionTime）重叠。
     * 重叠分钟数即为惩罚值。
     */
    Constraint noOverlapWithExceptionTime(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getStartTime() != null && order.getEndTime() != null)
                .join(ExceptionTime.class,
                        Joiners.equal(
                                order -> order.getAssignedLine().getId(),
                                ExceptionTime::getLineId))
                .filter((order, exception) -> 
                        order.getStartTime().isBefore(exception.getEndTime()) && 
                        order.getEndTime().isAfter(exception.getStartTime()))
                .penalizeConfigurable((order, exception) -> {
                    java.time.LocalDateTime overlapStart = order.getStartTime().isAfter(exception.getStartTime()) ? order.getStartTime() : exception.getStartTime();
                    java.time.LocalDateTime overlapEnd = order.getEndTime().isBefore(exception.getEndTime()) ? order.getEndTime() : exception.getEndTime();
                    return (int) ChronoUnit.MINUTES.between(overlapStart, overlapEnd);
                })
                .asConstraint(SchedulingConstraintIds.HC3);
    }

    // ===== HC4：厚度轮转约束（单峰波浪） =====

    /**
     * HC4：同一产线上的厚度变化必须遵循"由薄到厚，再由厚到薄"的单峰模式。
     * 允许1次方向反转（薄→厚→薄 合法），不允许多次反转（薄→厚→薄→厚 违规）。
     *
     * 修正点（Codex Review P2）：原实现为严格单调（0次反转），把合法的单峰也判违规。
     *
     * 实现方式：找到所有"方向反转点"（prevPrev→prev 方向与 prev→current 方向不同），
     * 按产线分组统计反转点数量。第1次反转合法（形成单峰），第2次及以后的每次反转惩罚1分。
     */
    Constraint thicknessSinglePeak(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(order -> {
                    if (order.getPreviousOrder() == null) return false;
                    if (order.getPreviousOrder().getPreviousOrder() == null) return false;

                    int prevPrevT = order.getPreviousOrder().getPreviousOrder().getThickness();
                    int prevT = order.getPreviousOrder().getThickness();
                    int curT = order.getThickness();

                    int dir1 = Integer.compare(prevT, prevPrevT);
                    int dir2 = Integer.compare(curT, prevT);

                    // 平段不算方向变化
                    if (dir1 == 0 || dir2 == 0) return false;
                    // 方向不同才是反转点
                    return dir1 != dir2;
                })
                .groupBy(order -> order.getAssignedLine() == null ? null : order.getAssignedLine().getId(),
                        ConstraintCollectors.count())
                .filter((lineId, reversalCount) -> reversalCount > 1)
                .penalizeConfigurable((lineId, reversalCount) -> reversalCount - 1)
                .asConstraint(SchedulingConstraintIds.HC4);
    }

    // ===== HC5：同产线子任务保序 =====

    /**
     * HC5：如果同一个母任务被拆分为多个子任务，
     * 它们在同一个产线上必须按照 dayIndex 的顺序排列。
     */
    Constraint subTaskMustMaintainOrder(ConstraintFactory factory) {
        return factory.forEachUniquePair(MotherRollOrder.class,
                        Joiners.equal(MotherRollOrder::getParentTaskId))
                .filter((o1, o2) -> {
                    if (o1.getAssignedLine() == null || o2.getAssignedLine() == null) return false;
                    if (!o1.getAssignedLine().getId().equals(o2.getAssignedLine().getId())) return false;
                    if (o1.getSequenceIndex() == null || o2.getSequenceIndex() == null) return false;
                    
                    if (o1.getSequenceIndex() < o2.getSequenceIndex()) {
                        return o1.getDayIndex() >= o2.getDayIndex();
                    } else if (o2.getSequenceIndex() < o1.getSequenceIndex()) {
                        return o2.getDayIndex() >= o1.getDayIndex();
                    }
                    return false;
                })
                .penalize(HardMediumSoftScore.ONE_HARD)
                .asConstraint("HC5-子任务保序");
    }

    // ===== MC1：过滤器后20天优先顺序 =====

    /**
     * 过滤器后优先级映射：使用 "productCode_thickness" 组合键。
     * 修正点（Codex Review P4）：原来用 T19EST 等 key 无法匹配实际 productCode。
     * 对照 plan.md 第128-132行的完整优先级序列重建。
     */
    private static final Map<String, Integer> FILTER_PREFERRED_ORDER = Map.ofEntries(
            Map.entry("EST_19", 1),   // T19EST 厚度188 高光
            Map.entry("FDX_4", 2),    // T4FDX
            Map.entry("FDY_4", 2),    // T4FDY（同 rank）
            Map.entry("FDX_5", 3),    // T5FDX
            Map.entry("FDX_7", 4),    // T7FDX
            Map.entry("DJX_24", 5),   // T24DJX
            Map.entry("EST_9", 6)     // T9EST
    );

    /** 构建 MC1 的查找 key */
    private static String filterKey(MotherRollOrder order) {
        return order.getProductCode() + "_" + order.getThickness();
    }

    /**
     * MC1：换过滤器后20天内，优先生产特定的型号（按预设字典排序）。
     * 如果处于20天保护期内出现非预期倒装，每跨越一级扣除 1 Medium。
     */
    Constraint filterChangePreferredOrder(ConstraintFactory factory) {
        return factory.forEachUniquePair(MotherRollOrder.class,
                        Joiners.equal(order ->
                                order.getAssignedLine() == null ? null : order.getAssignedLine().getId()))
                .filter((o1, o2) -> {
                    if (o1.getSequenceIndex() == null || o2.getSequenceIndex() == null) return false;
                    Integer rank1 = FILTER_PREFERRED_ORDER.get(filterKey(o1));
                    Integer rank2 = FILTER_PREFERRED_ORDER.get(filterKey(o2));
                    if (rank1 == null || rank2 == null) return false;
                    
                    if (o1.getSequenceIndex() < o2.getSequenceIndex()) {
                        return rank1 > rank2;
                    } else if (o2.getSequenceIndex() < o1.getSequenceIndex()) {
                        return rank2 > rank1;
                    }
                    return false;
                })
                .join(FilterChangePlan.class,
                        Joiners.equal((o1, o2) -> o1.getAssignedLine().getId(),
                                FilterChangePlan::getLineId))
                .filter((o1, o2, filter) -> {
                    if (o1.getStartTime() == null || o2.getStartTime() == null) return false;
                    long d1 = ChronoUnit.DAYS.between(filter.getChangeTime(), o1.getStartTime());
                    long d2 = ChronoUnit.DAYS.between(filter.getChangeTime(), o2.getStartTime());
                    return d1 >= 0 && d1 <= 20 && d2 >= 0 && d2 <= 20;
                })
                .penalizeConfigurable((o1, o2, filter) -> {
                    int rank1 = FILTER_PREFERRED_ORDER.get(filterKey(o1));
                    int rank2 = FILTER_PREFERRED_ORDER.get(filterKey(o2));
                    return Math.abs(rank1 - rank2);
                })
                .asConstraint(SchedulingConstraintIds.MC1);
    }

    // ===== MC2：库存>30天不前移 =====

    /**
     * MC2：库存>30天的订单不应提前生产。
     * 如果实际开始时间早于预期开始时间，按照提前的小时数惩罚。
     */
    Constraint highInventoryShouldNotBeAdvanced(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getStartTime() != null 
                        && order.getExpectedStartTime() != null
                        && order.getInventorySupplyDays() > 30.0
                        && order.getStartTime().isBefore(order.getExpectedStartTime()))
                .penalizeConfigurable(order -> 
                        (int) ChronoUnit.HOURS.between(order.getStartTime(), order.getExpectedStartTime()))
                .asConstraint(SchedulingConstraintIds.MC2);
    }

    // ===== SC1：换型时间最小化 =====

    /**
     * SC1：每次换型时，惩罚等同于换型分钟数。
     */
    Constraint minimizeChangeoverTime(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getPreviousOrder() != null && order.getChangeoverMinutes() != null && order.getChangeoverMinutes() > 0)
                .penalizeConfigurable(MotherRollOrder::getChangeoverMinutes)
                .asConstraint(SchedulingConstraintIds.SC1);
    }

    // ===== SC2：库存天数优先级 =====

    /**
     * SC2：同型号优先排库存天数低的，倒置将惩罚天数差值。
     */
    Constraint prioritizeByInventorySupplyDays(ConstraintFactory factory) {
        return factory.forEachUniquePair(MotherRollOrder.class,
                        Joiners.equal(order ->
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
                .penalizeConfigurable((o1, o2) -> {
                    double diff = Math.abs(o1.getInventorySupplyDays() - o2.getInventorySupplyDays());
                    return (int) diff;
                })
                .asConstraint(SchedulingConstraintIds.SC2);
    }

    // ===== SC3：期望生产时间偏差 =====

    /**
     * SC3：实际生产时间不要晚于期望时间，按晚的小时数惩罚。
     */
    Constraint respectExpectedStartTime(ConstraintFactory factory) {
        return factory.forEach(MotherRollOrder.class)
                .filter(order -> order.getStartTime() != null && order.getExpectedStartTime() != null
                        && order.getStartTime().isAfter(order.getExpectedStartTime()))
                .penalizeConfigurable(order -> 
                        (int) ChronoUnit.HOURS.between(order.getExpectedStartTime(), order.getStartTime()))
                .asConstraint(SchedulingConstraintIds.SC3);
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
                .penalizeConfigurable()
                .asConstraint(SchedulingConstraintIds.SC4);
    }

    // ===== SC5：拆分子任务连续生产 =====

    /**
     * SC5：从同一个订单拆分出的不同 dayIndex 子任务，尽力做到连续。
     * 同一条产线上被插队惩罚 10，跨产线生产惩罚 1000。
     */
    Constraint continuousProduction(ConstraintFactory factory) {
        return factory.forEachUniquePair(MotherRollOrder.class,
                        Joiners.equal(MotherRollOrder::getParentTaskId))
                .filter((o1, o2) -> {
                    if (o1.getParentTaskId() == null || Math.abs(o1.getDayIndex() - o2.getDayIndex()) != 1) return false;
                    if (o1.getAssignedLine() == null || o2.getAssignedLine() == null) return false;
                    
                    if (!o1.getAssignedLine().getId().equals(o2.getAssignedLine().getId())) return true;
                    
                    if (o1.getSequenceIndex() == null || o2.getSequenceIndex() == null) return false;
                    return Math.abs(o1.getSequenceIndex() - o2.getSequenceIndex()) > 1;
                })
                .penalize(HardMediumSoftScore.ONE_SOFT, (o1, o2) -> {
                    if (!o1.getAssignedLine().getId().equals(o2.getAssignedLine().getId())) {
                        return 1000;
                    }
                    return 10;
                })
                .asConstraint("SC5-拆分子任务连续生产");
    }
}
