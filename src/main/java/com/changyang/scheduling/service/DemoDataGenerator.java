package com.changyang.scheduling.service;

import com.changyang.scheduling.domain.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.Set;

/**
 * 演示数据生成器
 * <p>
 * 产线配置与真实环境一致：双拉2线(L2)、双拉4线(L4)。
 * 订单使用真实型号和厚度范围。
 * </p>
 */
@Service
public class DemoDataGenerator {

    public MotherRollSchedule generateDemoData() {
        MotherRollSchedule schedule = new MotherRollSchedule();
        Random random = new Random(42);

        // 1. 真实产线配置：只有2条
        LocalDateTime baseTime = LocalDateTime.of(2026, 3, 11, 8, 0);
        List<ProductionLine> lines = List.of(
                new ProductionLine("L2", "双拉2线", "双拉2线", baseTime),
                new ProductionLine("L4", "双拉4线", "双拉4线", baseTime)
        );
        schedule.setProductionLines(lines);

        // 2. 生成异常停机时间 - 双拉2线第二天有维修
        ExceptionTime ex1 = new ExceptionTime("L2",
                baseTime.plusDays(1).withHour(8).withMinute(0),
                baseTime.plusDays(1).withHour(20).withMinute(0));
        schedule.setExceptionTimes(List.of(ex1));

        // 3. 过滤器更换计划
        FilterChangePlan fcp1 = new FilterChangePlan("L4", baseTime.plusHours(10), 120);
        schedule.setFilterChangePlans(List.of(fcp1));

        // 4. 换型矩阵 - 使用 ExcelDataLoader 中的真实换型矩阵
        ExcelDataLoader loader = new ExcelDataLoader(new ChangeoverService());
        schedule.setChangeoverEntries(loader.buildChangeoverMatrix());

        // 5. 工厂日历
        FactoryCalendar cal = new FactoryCalendar(java.util.Collections.emptySet());
        schedule.setFactoryCalendar(cal);

        // 6. 生成订单 - 使用真实型号和厚度
        List<MotherRollOrder> orders = new ArrayList<>();
        String[] productCodes = {"EST", "ESY", "FDX", "FDY", "DJX", "DJY"};
        int[] thicknesses = {4, 5, 7, 9, 10, 14, 24, 29, 42, 61};

        for (int i = 1; i <= 30; i++) {
            MotherRollOrder order = new MotherRollOrder();
            order.setId("ORD-" + String.format("%03d", i));
            String pc = productCodes[random.nextInt(productCodes.length)];
            order.setProductCode(pc);
            order.setFormulaCode(ExcelDataLoader.FORMULA_MAP.getOrDefault(pc, "Formula_Unknown"));
            order.setMaterialCode("MAT-" + pc);
            order.setThickness(thicknesses[random.nextInt(thicknesses.length)]);

            order.setQuantity(500 + random.nextInt(2000));
            order.setProductionDurationHours(
                    order.getQuantity() / ExcelDataLoader.HOURLY_OUTPUT.getOrDefault(pc, 5000.0));

            // 库存字段暂设0（与真实数据一致）
            order.setCurrentInventory(0);
            order.setMonthlyShipment(order.getQuantity() / 3.0);

            order.setExpectedStartTime(baseTime.plusDays(random.nextInt(5)).plusHours(random.nextInt(12)));

            // 所有型号兼容两条产线
            order.setCompatibleLines(new java.util.HashSet<>(Set.of("双拉2线", "双拉4线")));

            // 三分之一几率有偏好线
            if (random.nextInt(3) == 0) {
                order.setPreferredLineCode(random.nextBoolean() ? "双拉2线" : "双拉4线");
            }

            orders.add(order);
        }

        // 添加几个 QDJY 订单（只能在双拉2线）
        for (int i = 31; i <= 33; i++) {
            MotherRollOrder qdjy = new MotherRollOrder();
            qdjy.setId("QDJY-" + i);
            qdjy.setProductCode("QDJY");
            qdjy.setFormulaCode("Formula_DJ");
            qdjy.setMaterialCode("MAT-QDJY");
            qdjy.setThickness(thicknesses[random.nextInt(thicknesses.length)]);
            qdjy.setQuantity(1000 + random.nextInt(1000));
            qdjy.setProductionDurationHours(qdjy.getQuantity() / 6000.0);
            qdjy.setCurrentInventory(0);
            qdjy.setMonthlyShipment(qdjy.getQuantity() / 3.0);
            qdjy.setExpectedStartTime(baseTime.plusDays(random.nextInt(3)));
            qdjy.setCompatibleLines(Set.of("双拉2线")); // QDJY 只能在双拉2线
            orders.add(qdjy);
        }

        schedule.setOrders(orders);
        return schedule;
    }
}
