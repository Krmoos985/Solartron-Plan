package com.changyang.scheduling.service;

import com.changyang.scheduling.domain.MergedTask;
import com.changyang.scheduling.domain.MotherRollOrder;
import com.changyang.scheduling.domain.ProductionLine;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class PrePostProcessTest {

    @Test
    void testTaskSplitter() {
        TaskSplitter splitter = new TaskSplitter();

        // 构造一个 60 小时的任务（跨 3 天）
        MotherRollOrder orderLong = new MotherRollOrder();
        orderLong.setId("O1");
        orderLong.setProductionDurationHours(60.0);
        orderLong.setQuantity(300.0);
        orderLong.setPinned(true);

        // 构造一个 10 小时的任务（1 天内）
        MotherRollOrder orderShort = new MotherRollOrder();
        orderShort.setId("O2");
        orderShort.setProductionDurationHours(10.0);
        orderShort.setQuantity(50.0);

        List<MotherRollOrder> result = splitter.splitTasks(List.of(orderLong, orderShort));

        assertEquals(4, result.size(), "60小时拆3天，10小时不拆（但计为1天），共4个子任务");

        // 检查拆分的 O1
        MotherRollOrder o1_1 = result.get(0);
        assertEquals("O1-1", o1_1.getId());
        assertEquals("O1", o1_1.getParentTaskId());
        assertTrue(o1_1.isSplit());
        assertEquals(3, o1_1.getTotalDays());
        assertEquals(1, o1_1.getDayIndex());
        assertEquals(20.0, o1_1.getProductionDurationHours());
        assertEquals(100.0, o1_1.getQuantity());
        assertTrue(o1_1.isPinned());

        MotherRollOrder o1_3 = result.get(2);
        assertEquals("O1-3", o1_3.getId());
        assertEquals(3, o1_3.getDayIndex());

        // 检查不拆分的 O2
        MotherRollOrder o2 = result.get(3);
        assertEquals("O2", o2.getId());
        assertFalse(o2.isSplit());
        assertEquals(1, o2.getTotalDays());
        assertEquals(1, o2.getDayIndex());
    }

    @Test
    void testTaskMerger() {
        TaskMerger merger = new TaskMerger();

        LocalDateTime baseTime = LocalDateTime.of(2026, 3, 10, 8, 0);

        // 模拟 Solver 排好序的列表：[O1-1, O1-2, O2, O1-3]
        MotherRollOrder o1_1 = new MotherRollOrder();
        o1_1.setId("O1-1"); o1_1.setParentTaskId("O1"); o1_1.setDayIndex(1); o1_1.setTotalDays(3);
        o1_1.setStartTime(baseTime);
        o1_1.setEndTime(baseTime.plusHours(20));

        MotherRollOrder o1_2 = new MotherRollOrder();
        o1_2.setId("O1-2"); o1_2.setParentTaskId("O1"); o1_2.setDayIndex(2); o1_2.setTotalDays(3);
        o1_2.setStartTime(baseTime.plusHours(20));
        o1_2.setEndTime(baseTime.plusHours(40));

        MotherRollOrder o2 = new MotherRollOrder();
        o2.setId("O2"); o2.setParentTaskId(null); o2.setDayIndex(1); o2.setTotalDays(1);
        o2.setStartTime(baseTime.plusHours(40));
        o2.setEndTime(baseTime.plusHours(50));

        MotherRollOrder o1_3 = new MotherRollOrder(); // 被打断的 O1-3
        o1_3.setId("O1-3"); o1_3.setParentTaskId("O1"); o1_3.setDayIndex(3); o1_3.setTotalDays(3);
        o1_3.setStartTime(baseTime.plusHours(50));
        o1_3.setEndTime(baseTime.plusHours(70));

        ProductionLine line = new ProductionLine();
        line.setId("L1");
        line.setOrders(List.of(o1_1, o1_2, o2, o1_3));

        List<MergedTask> mergedTasks = merger.mergeTasks(line);

        assertEquals(3, mergedTasks.size(), "O1-1和O1-2合并，O2独立，O1-3因打断而独立");

        // 断言合并块 1
        MergedTask m1 = mergedTasks.get(0);
        assertEquals("O1", m1.getOriginalTaskId());
        assertEquals(2, m1.getDaysCovered());
        assertEquals(baseTime, m1.getPlannedStart());
        assertEquals(baseTime.plusHours(40), m1.getPlannedEnd());

        // 断言合并块 2
        MergedTask m2 = mergedTasks.get(1);
        assertEquals("O2", m2.getOriginalTaskId());
        assertEquals(1, m2.getDaysCovered());

        // 断言合并块 3
        MergedTask m3 = mergedTasks.get(2);
        assertEquals("O1", m3.getOriginalTaskId());
        assertEquals(1, m3.getDaysCovered(), "O1的第3天应该被单独作为一个合并块输出");
        assertEquals(baseTime.plusHours(50), m3.getPlannedStart());
    }
}
