package com.changyang.scheduling.service;

import com.changyang.scheduling.domain.MotherRollOrder;
import com.changyang.scheduling.domain.MotherRollSchedule;
import com.changyang.scheduling.domain.ProductionLine;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Excel 排程集成测试
 * <p>
 * 使用 Spring Boot 上下文启动完整的 Solver，验证从 Excel 加载到排程输出的端到端流程。
 * Solver 求解时间限制为 5 秒（通过 test profile 覆盖）。
 * </p>
 */
@SpringBootTest(properties = {"timefold.solver.termination.spent-limit=30s"})
class ExcelSchedulingIntegrationTest {

    @Autowired
    private ExcelDataLoader excelDataLoader;

    @Autowired
    private SchedulingService schedulingService;

    @Test
    void testEndToEndExcelScheduling() throws Exception {
        // 1. 加载 Excel 数据
        LocalDateTime solveStart = LocalDateTime.of(2026, 3, 13, 8, 0);
        MotherRollSchedule problem;
        try (InputStream is = new ClassPathResource("data/生产订单_排程导入版.xlsx").getInputStream()) {
            problem = excelDataLoader.load(is, solveStart);
        }

        int originalOrderCount = problem.getOrders().size();
        System.out.println("原始订单数: " + originalOrderCount);

        // 2. 求解
        MotherRollSchedule solution = schedulingService.solve(problem);

        // 3. 验证基本结果
        assertNotNull(solution.getScore(), "应返回评分");
        assertTrue(solution.getScore().isFeasible(), "当前 Excel 模式下应得到硬约束可行解");
        System.out.println("最终评分: " + solution.getScore());

        // 4. 当前 Excel 模式只保留原始订单粒度，不按天拆分
        assertEquals(originalOrderCount, solution.getOrders().size(), "当前 Excel 模式下不应按天拆分任务");

        // 5. 验证完整性：所有订单都被分配到产线
        int unassignedCount = SchedulingService.getUnassignedCount(solution);
        System.out.println("未分配任务数: " + unassignedCount);

        int totalAssigned = 0;
        for (ProductionLine line : solution.getProductionLines()) {
            System.out.println(line.getName() + " 分配了 " + line.getOrders().size() + " 条任务");
            totalAssigned += line.getOrders().size();

            // 打印前5条任务
            int show = Math.min(5, line.getOrders().size());
            for (int i = 0; i < show; i++) {
                MotherRollOrder o = line.getOrders().get(i);
                System.out.printf("  [%d] %s %s t=%dμm 开始=%s 结束=%s 换型=%s分钟%n",
                        i, o.getId(), o.getProductCode(), o.getThickness(),
                        o.getStartTime(), o.getEndTime(), o.getChangeoverMinutes());
            }
            if (line.getOrders().size() > show) {
                System.out.println("  ... 还有 " + (line.getOrders().size() - show) + " 条");
            }
        }
        assertTrue(totalAssigned > 0, "至少有一条任务被分配");
        assertEquals(originalOrderCount, totalAssigned, "所有原始订单都应被分配");

        // 6. 验证 HC1：所有分配的订单都在兼容产线上
        for (ProductionLine line : solution.getProductionLines()) {
            for (MotherRollOrder order : line.getOrders()) {
                assertTrue(order.isCompatibleWith(line),
                        "订单 " + order.getId() + "(" + order.getProductCode()
                                + ") 被分配到不兼容的 " + line.getLineCode());
            }
        }

        System.out.println("=== 集成测试通过 ===");
    }
}
