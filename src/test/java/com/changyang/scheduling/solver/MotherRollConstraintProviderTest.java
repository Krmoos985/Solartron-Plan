package com.changyang.scheduling.solver;

import ai.timefold.solver.test.api.score.stream.ConstraintVerifier;
import com.changyang.scheduling.domain.MotherRollOrder;
import com.changyang.scheduling.domain.MotherRollSchedule;
import com.changyang.scheduling.domain.ProductionLine;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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
}
