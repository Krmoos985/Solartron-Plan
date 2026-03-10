package com.changyang.scheduling.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.changyang.scheduling.domain.ExceptionTime;
import com.changyang.scheduling.domain.MotherRollOrder;
import com.changyang.scheduling.domain.MotherRollSchedule;
import com.changyang.scheduling.domain.ProductionLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.Set;

class MotherRollConstraintProviderTest {

    private ConstraintVerifier<MotherRollConstraintProvider, MotherRollSchedule> constraintVerifier;

    @BeforeEach
    void setUp() {
        constraintVerifier = ConstraintVerifier.build(new MotherRollConstraintProvider(), MotherRollSchedule.class, MotherRollOrder.class, ProductionLine.class);
    }

    @Test
    void hc1ProductLineMustBeCompatible() {
        ProductionLine line1 = new ProductionLine("L1", "一线", "L1", null);

        MotherRollOrder orderO1 = new MotherRollOrder();
        orderO1.setId("O1");
        orderO1.setAssignedLine(line1);
        orderO1.setCompatibleLines(Set.of("L1", "L2"));

        MotherRollOrder orderO2 = new MotherRollOrder();
        orderO2.setId("O2");
        orderO2.setAssignedLine(line1);
        orderO2.setCompatibleLines(Set.of("L2")); // 不兼容 L1

        constraintVerifier.verifyThat(MotherRollConstraintProvider::productLineMustBeCompatible)
                .given(line1, orderO1, orderO2)
                .penalizesBy(1); // O2 违背 1 次
    }

    @Test
    void hc5SubTaskMustMaintainOrder_Valid() {
        ProductionLine line = new ProductionLine("L1", "一线", "L1", null);

        MotherRollOrder order1 = new MotherRollOrder();
        order1.setId("O1-1"); order1.setParentTaskId("O1"); order1.setDayIndex(1);
        order1.setAssignedLine(line); order1.setSequenceIndex(0);

        MotherRollOrder order2 = new MotherRollOrder();
        order2.setId("O1-2"); order2.setParentTaskId("O1"); order2.setDayIndex(2);
        order2.setAssignedLine(line); order2.setSequenceIndex(1);

        line.setOrders(new java.util.ArrayList<>(java.util.List.of(order1, order2)));

        constraintVerifier.verifyThat(MotherRollConstraintProvider::subTaskMustMaintainOrder)
                .given(line, order1, order2)
                .penalizesBy(0);
    }

    @Test
    void hc5SubTaskMustMaintainOrder_Invalid() {
        ProductionLine line = new ProductionLine("L1", "一线", "L1", null);

        MotherRollOrder order1 = new MotherRollOrder();
        // 倒装：order1(是 day1 的分身) 被排到了顺位 1
        order1.setId("O1-1"); order1.setParentTaskId("O1"); order1.setDayIndex(1);
        order1.setAssignedLine(line); order1.setSequenceIndex(1);

        MotherRollOrder order2 = new MotherRollOrder();
        // 倒装：order2(是 day2 的分身) 被排到了顺位 0
        order2.setId("O1-2"); order2.setParentTaskId("O1"); order2.setDayIndex(2);
        order2.setAssignedLine(line); order2.setSequenceIndex(0);

        line.setOrders(new java.util.ArrayList<>(java.util.List.of(order2, order1)));

        constraintVerifier.verifyThat(MotherRollConstraintProvider::subTaskMustMaintainOrder)
                .given(line, order1, order2)
                .penalizesBy(1);
    }

    @Test
    void hc3NoOverlapWithExceptionTime() {
        ProductionLine line = new ProductionLine("L1", "一线", "L1", null);

        LocalDateTime base = LocalDateTime.of(2026, 3, 10, 8, 0);

        // 例外时间：10:00 到 12:00
        ExceptionTime exception = new ExceptionTime("L1", base.plusHours(2), base.plusHours(4));

        MotherRollOrder orderOK = new MotherRollOrder();
        orderOK.setId("OK"); orderOK.setAssignedLine(line);
        orderOK.setStartTime(base.plusHours(0));
        orderOK.setEndTime(base.plusHours(2)); // 8:00 - 10:00，刚好不重叠

        MotherRollOrder orderOverlap = new MotherRollOrder();
        orderOverlap.setId("Overlap"); orderOverlap.setAssignedLine(line);
        orderOverlap.setStartTime(base.plusHours(1));
        orderOverlap.setEndTime(base.plusHours(3)); // 9:00 - 11:00，与例外时间 10:00-11:00 重叠（60分钟）

        // 验证没有重叠的不惩罚
        constraintVerifier.verifyThat(MotherRollConstraintProvider::noOverlapWithExceptionTime)
                .given(line, exception, orderOK)
                .penalizesBy(0);

        // 验证有重叠的惩罚重叠分钟数 (60分钟)
        constraintVerifier.verifyThat(MotherRollConstraintProvider::noOverlapWithExceptionTime)
                .given(line, exception, orderOverlap)
                .penalizesBy(60);
    }

    @Test
    void hc2UrgentInventoryMustBePrioritized() {
        ProductionLine line = new ProductionLine("L1", "一线", "L1", null);

        MotherRollOrder urgent = new MotherRollOrder();
        urgent.setId("Urgent"); urgent.setMonthlyShipment(30); urgent.setCurrentInventory(5); // (5/30)*30 = 5 days < 10
        urgent.setAssignedLine(line);

        MotherRollOrder normal = new MotherRollOrder();
        normal.setId("Normal"); normal.setMonthlyShipment(30); normal.setCurrentInventory(15); // (15/30)*30 = 15 days >= 10
        normal.setAssignedLine(line);

        // Valid: Urgent is before normal
        urgent.setSequenceIndex(0); normal.setSequenceIndex(1);
        constraintVerifier.verifyThat(MotherRollConstraintProvider::urgentInventoryMustBePrioritized)
                .given(line, urgent, normal)
                .penalizesBy(0);

        // Invalid: Normal is before urgent
        normal.setSequenceIndex(0); urgent.setSequenceIndex(1);
        constraintVerifier.verifyThat(MotherRollConstraintProvider::urgentInventoryMustBePrioritized)
                .given(line, urgent, normal)
                .penalizesBy(1);
    }

    @Test
    void hc4ThicknessMonotonicity() {
        ProductionLine line = new ProductionLine("L1", "一线", "L1", null);

        // Sequence: 50 -> 75 -> 50 (V-shape, UP then DOWN)
        MotherRollOrder o1 = new MotherRollOrder(); o1.setId("o1"); o1.setThickness(50);
        o1.setAssignedLine(line);

        MotherRollOrder o2 = new MotherRollOrder(); o2.setId("o2"); o2.setThickness(75);
        o2.setPreviousOrder(o1); o2.setAssignedLine(line);

        MotherRollOrder o3 = new MotherRollOrder(); o3.setId("o3"); o3.setThickness(50);
        o3.setPreviousOrder(o2); o3.setAssignedLine(line);

        // 1 UP step (50->75), 1 DOWN step (75->50) => 1x1 = 1 penalty
        constraintVerifier.verifyThat(MotherRollConstraintProvider::thicknessMonotonicity)
                .given(line, o1, o2, o3)
                .penalizesBy(1);

        // Sequence: 50 -> 75 -> 100 (Monotonic UP)
        MotherRollOrder o4 = new MotherRollOrder(); o4.setId("o4"); o4.setThickness(100);
        o4.setPreviousOrder(o2); o4.setAssignedLine(line);

        // 2 UP steps (50->75, 75->100), 0 DOWN steps => 2x0 = 0 penalty
        constraintVerifier.verifyThat(MotherRollConstraintProvider::thicknessMonotonicity)
                .given(line, o1, o2, o4)
                .penalizesBy(0);
    }
}
