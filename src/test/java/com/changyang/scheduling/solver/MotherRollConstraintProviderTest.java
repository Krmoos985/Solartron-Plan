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

    @Test
    void mc1FilterChangePreferredOrder() {
        ProductionLine line = new ProductionLine("L1", "一线", "L1", null);
        LocalDateTime base = LocalDateTime.of(2026, 3, 10, 8, 0);

        // Filter change at Day 0
        com.changyang.scheduling.domain.FilterChangePlan filter = new com.changyang.scheduling.domain.FilterChangePlan();
        filter.setLineId("L1");
        filter.setChangeTime(base);

        // T19EST has rank 1, T7FDX has rank 4. T19EST should be before T7FDX.
        MotherRollOrder o1 = new MotherRollOrder();
        o1.setId("o1"); o1.setProductCode("T19EST"); o1.setAssignedLine(line);
        o1.setStartTime(base.plusDays(5)); // Within 20 days
        
        MotherRollOrder o2 = new MotherRollOrder();
        o2.setId("o2"); o2.setProductCode("T7FDX"); o2.setAssignedLine(line);
        o2.setStartTime(base.plusDays(10)); // Within 20 days

        // valid order: rank 1 before rank 4
        o1.setSequenceIndex(0); o2.setSequenceIndex(1);
        constraintVerifier.verifyThat(MotherRollConstraintProvider::filterChangePreferredOrder)
                .given(line, filter, o1, o2)
                .penalizesBy(0);

        // invalid order: rank 4 before rank 1 (difference is 4 - 1 = 3)
        o1.setSequenceIndex(1); o2.setSequenceIndex(0);
        constraintVerifier.verifyThat(MotherRollConstraintProvider::filterChangePreferredOrder)
                .given(line, filter, o1, o2)
                .penalizesBy(3);
    }

    @Test
    void mc2HighInventoryShouldNotBeAdvanced() {
        ProductionLine line = new ProductionLine("L1", "一线", "L1", null);

        MotherRollOrder order = new MotherRollOrder();
        order.setId("O1"); order.setAssignedLine(line);
        order.setMonthlyShipment(30);
        order.setCurrentInventory(40); // (40/30)*30 = 40 days > 30

        LocalDateTime expectedTime = LocalDateTime.of(2026, 3, 15, 12, 0);
        order.setExpectedStartTime(expectedTime);
        
        // Scenario 1: started on time or later
        order.setStartTime(expectedTime.plusHours(5));
        constraintVerifier.verifyThat(MotherRollConstraintProvider::highInventoryShouldNotBeAdvanced)
                .given(line, order)
                .penalizesBy(0);

        // Scenario 2: advanced by 10 hours
        order.setStartTime(expectedTime.minusHours(10));
        constraintVerifier.verifyThat(MotherRollConstraintProvider::highInventoryShouldNotBeAdvanced)
                .given(line, order)
                .penalizesBy(10);
    }

    @Test
    void sc1MinimizeChangeoverTime() {
        ProductionLine line = new ProductionLine("L1", "一线", "L1", null);
        MotherRollOrder o1 = new MotherRollOrder(); o1.setId("o1"); o1.setAssignedLine(line);
        MotherRollOrder o2 = new MotherRollOrder(); o2.setId("o2"); o2.setAssignedLine(line);
        o2.setPreviousOrder(o1);

        // Scenario 1: No changeover time
        o2.setChangeoverMinutes(0);
        constraintVerifier.verifyThat(MotherRollConstraintProvider::minimizeChangeoverTime)
                .given(line, o1, o2)
                .penalizesBy(0);

        // Scenario 2: Changeover time = 45 mins
        o2.setChangeoverMinutes(45);
        constraintVerifier.verifyThat(MotherRollConstraintProvider::minimizeChangeoverTime)
                .given(line, o1, o2)
                .penalizesBy(45);
    }

    @Test
    void sc2PrioritizeByInventorySupplyDays() {
        ProductionLine line = new ProductionLine("L1", "一线", "L1", null);

        MotherRollOrder o1 = new MotherRollOrder(); o1.setId("o1"); o1.setAssignedLine(line);
        // Supply = 30 days
        o1.setMonthlyShipment(30); o1.setCurrentInventory(30);

        MotherRollOrder o2 = new MotherRollOrder(); o2.setId("o2"); o2.setAssignedLine(line);
        // Supply = 15 days
        o2.setMonthlyShipment(30); o2.setCurrentInventory(15);

        // Valid: o2 (15) comes before o1 (30).
        o2.setSequenceIndex(0); o1.setSequenceIndex(1);
        constraintVerifier.verifyThat(MotherRollConstraintProvider::prioritizeByInventorySupplyDays)
                .given(line, o1, o2)
                .penalizesBy(0);

        // Invalid: o1 (30) comes before o2 (15). Penalty is diff (15). BUT penalty weight is 2.
        o1.setSequenceIndex(0); o2.setSequenceIndex(1);
        constraintVerifier.verifyThat(MotherRollConstraintProvider::prioritizeByInventorySupplyDays)
                .given(line, o1, o2)
                .penalizesBy(15); // penalizesBy count is multiplied by weight internally in Verifier usually, wait, NO!
        // verifyThat penalizesBy counts the *match weight* directly! So it should be the match weight (15).
    }

    @Test
    void sc3RespectExpectedStartTime() {
        ProductionLine line = new ProductionLine("L1", "一线", "L1", null);

        MotherRollOrder order = new MotherRollOrder();
        order.setId("O1"); order.setAssignedLine(line);
        LocalDateTime expectedTime = LocalDateTime.of(2026, 3, 15, 12, 0);
        order.setExpectedStartTime(expectedTime);

        // Valid: started on time or earlier
        order.setStartTime(expectedTime.minusHours(5));
        constraintVerifier.verifyThat(MotherRollConstraintProvider::respectExpectedStartTime)
                .given(line, order)
                .penalizesBy(0);

        // Invalid: started 10 hours late
        order.setStartTime(expectedTime.plusHours(10));
        constraintVerifier.verifyThat(MotherRollConstraintProvider::respectExpectedStartTime)
                .given(line, order)
                .penalizesBy(10);
    }

    @Test
    void sc4PreferredLineMatch() {
        ProductionLine line2 = new ProductionLine("L2", "二线", "LINE_2", null);
        ProductionLine line4 = new ProductionLine("L4", "四线", "LINE_4", null);

        MotherRollOrder order = new MotherRollOrder();
        order.setId("O1"); order.setPreferredLineCode("LINE_2");

        // Valid
        order.setAssignedLine(line2);
        constraintVerifier.verifyThat(MotherRollConstraintProvider::preferredLineMatch)
                .given(line2, line4, order)
                .penalizesBy(0);

        // Invalid
        order.setAssignedLine(line4);
        constraintVerifier.verifyThat(MotherRollConstraintProvider::preferredLineMatch)
                .given(line2, line4, order)
                .penalizesBy(1);
    }

    @Test
    void sc5ContinuousProduction() {
        ProductionLine line1 = new ProductionLine("L1", "一线", "L1", null);
        ProductionLine line2 = new ProductionLine("L2", "二线", "L2", null);

        MotherRollOrder o1 = new MotherRollOrder(); o1.setId("o1"); o1.setParentTaskId("Parent"); o1.setDayIndex(1);
        MotherRollOrder o2 = new MotherRollOrder(); o2.setId("o2"); o2.setParentTaskId("Parent"); o2.setDayIndex(2);

        // Scenario 1: Cross line (+1000)
        o1.setAssignedLine(line1); o1.setSequenceIndex(0);
        o2.setAssignedLine(line2); o2.setSequenceIndex(0);
        constraintVerifier.verifyThat(MotherRollConstraintProvider::continuousProduction)
                .given(line1, line2, o1, o2)
                .penalizesBy(1000);

        // Scenario 2: Same line, but non-adjacent (+10)
        o1.setAssignedLine(line1); o1.setSequenceIndex(0);
        o2.setAssignedLine(line1); o2.setSequenceIndex(2); // difference > 1
        constraintVerifier.verifyThat(MotherRollConstraintProvider::continuousProduction)
                .given(line1, line2, o1, o2)
                .penalizesBy(10);

        // Scenario 3: Same line, adjacent (0)
        o1.setAssignedLine(line1); o1.setSequenceIndex(1);
        o2.setAssignedLine(line1); o2.setSequenceIndex(2); // difference == 1
        constraintVerifier.verifyThat(MotherRollConstraintProvider::continuousProduction)
                .given(line1, line2, o1, o2)
                .penalizesBy(0);
    }
}
