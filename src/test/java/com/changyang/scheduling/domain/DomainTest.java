package com.changyang.scheduling.domain;

import com.changyang.scheduling.service.ChangeoverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class DomainTest {

    private ChangeoverService changeoverService;

    @BeforeEach
    void setUp() {
        changeoverService = new ChangeoverService(); // 触发 INSTANCE 初始化

        // 模拟外部输入的换型数据
        List<ChangeoverEntry> entries = List.of(
                new ChangeoverEntry(ChangeoverEntry.Type.FORMULA_MODEL, "F1", "M1", "F2", "M2", null, null, 40),
                new ChangeoverEntry(ChangeoverEntry.Type.THICKNESS, null, null, null, null, 100, 150, 15)
        );
        changeoverService.initCache(entries);
    }

    @Test
    void testMotherRollOrderUpdateStartAndEndTime() {
        // 1. 准备一条产线
        LocalDateTime lineStart = LocalDateTime.of(2026, 3, 10, 8, 0);
        ProductionLine line = new ProductionLine("L1", "一线", "L1", lineStart);

        // 2. 准备订单 1：队首任务
        MotherRollOrder order1 = new MotherRollOrder();
        order1.setId("O1");
        order1.setFormulaCode("F1");
        order1.setProductCode("M1");
        order1.setThickness(100);
        order1.setProductionDurationHours(10.0);

        // 模拟分配到产线
        order1.setAssignedLine(line);
        order1.setPreviousOrder(null);
        order1.updateStartAndEndTime(); // 手动触发级联更新（实际由 Solver 自动触发）

        assertEquals(0, order1.getChangeoverMinutes());
        assertEquals(lineStart, order1.getStartTime(), "首任务应从产线可用时间开始");
        assertEquals(lineStart.plusHours(10), order1.getEndTime());

        // 3. 准备订单 2：非队首任务，且发生换型
        MotherRollOrder order2 = new MotherRollOrder();
        order2.setId("O2");
        order2.setFormulaCode("F2");
        order2.setProductCode("M2");
        order2.setThickness(150);
        order2.setProductionDurationHours(5.0);

        order2.setAssignedLine(line);
        order2.setPreviousOrder(order1);
        order2.updateStartAndEndTime();

        // 换型时间应该是 40 (配方维度) 和 15 (厚度维度) 的最大值，所以是 40
        assertEquals(40, order2.getChangeoverMinutes());
        LocalDateTime expectedStart2 = order1.getEndTime().plusMinutes(40);
        assertEquals(expectedStart2, order2.getStartTime());
        assertEquals(expectedStart2.plusHours(5), order2.getEndTime());

        // 4. 准备订单 3：与订单 2 相同产品（如拆分后的第二天）
        MotherRollOrder order3 = new MotherRollOrder();
        order3.setId("O3");
        order3.setFormulaCode("F2");
        order3.setProductCode("M2");
        order3.setThickness(150);
        order3.setProductionDurationHours(5.0);

        order3.setAssignedLine(line);
        order3.setPreviousOrder(order2);
        order3.updateStartAndEndTime();

        assertEquals(0, order3.getChangeoverMinutes(), "同产品换型应为 0");
        assertEquals(order2.getEndTime(), order3.getStartTime());
        assertEquals(order2.getEndTime().plusHours(5), order3.getEndTime());

        // 5. 测分配取消的情况
        order3.setAssignedLine(null);
        order3.updateStartAndEndTime();
        assertNull(order3.getStartTime());
        assertNull(order3.getEndTime());
        assertNull(order3.getChangeoverMinutes());
    }
}
