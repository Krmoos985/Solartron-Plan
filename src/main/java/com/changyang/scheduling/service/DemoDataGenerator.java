package com.changyang.scheduling.service;

import com.changyang.scheduling.domain.*;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;

@Service
public class DemoDataGenerator {

    public MotherRollSchedule generateDemoData() {
        MotherRollSchedule schedule = new MotherRollSchedule();
        Random random = new Random(42); // 固定种子以获得一致的演示效果

        // 1. 生成产线
        List<ProductionLine> lines = new ArrayList<>();
        LocalDateTime baseTime = LocalDateTime.of(2026, 3, 11, 8, 0);
        
        for (int i = 1; i <= 3; i++) {
            ProductionLine line = new ProductionLine();
            line.setId("L" + i);
            line.setName(i + "号线");
            line.setLineCode("Line_" + i);
            line.setAvailableFrom(baseTime);
            lines.add(line);
        }
        schedule.setProductionLines(lines);

        // 2. 生成异常停机时间 (ExceptionTime) - 假设L1在第二天有维修
        ExceptionTime ex1 = new ExceptionTime("L1", 
                baseTime.plusDays(1).withHour(8).withMinute(0),
                baseTime.plusDays(1).withHour(20).withMinute(0));
        schedule.setExceptionTimes(List.of(ex1));

        // 3. 生成过滤器更换计划 (FilterChangePlan)
        FilterChangePlan fcp1 = new FilterChangePlan("L2", baseTime.plusHours(10), 120);
        schedule.setFilterChangePlans(List.of(fcp1));

        // 4. 生成换型矩阵项 (ChangeoverEntry) - 简化的换型矩阵
        List<ChangeoverEntry> changeovers = new ArrayList<>();
        changeovers.add(new ChangeoverEntry(ChangeoverEntry.Type.FORMULA_MODEL, "F1", "PROD-F1-M1", "F2", "PROD-F2-M2", null, null, 120)); // 配方改变需要 2 小时
        changeovers.add(new ChangeoverEntry(ChangeoverEntry.Type.FORMULA_MODEL, "F2", "PROD-F2-M2", "F1", "PROD-F1-M1", null, null, 120));
        schedule.setChangeoverEntries(changeovers);
        
        // FactoryCalendar 
        FactoryCalendar cal = new FactoryCalendar(java.util.Collections.emptySet()); // 无休息日
        schedule.setFactoryCalendar(cal);

        // 5. 生成母卷订单群 (30个不同厚度和材料的订单)
        List<MotherRollOrder> orders = new ArrayList<>();
        String[] formulas = {"F1", "F2", "F3"};
        String[] materials = {"M1", "M2", "M3"};
        int[] thicknesses = {25, 38, 50, 75, 100, 125, 150, 200}; // 离散厚度

        for (int i = 1; i <= 30; i++) {
            MotherRollOrder order = new MotherRollOrder();
            order.setId("ORD-" + String.format("%03d", i));
            order.setFormulaCode(formulas[random.nextInt(formulas.length)]);
            order.setMaterialCode(materials[random.nextInt(materials.length)]);
            order.setProductCode("PROD-" + order.getFormulaCode() + "-" + order.getMaterialCode());
            order.setThickness(thicknesses[random.nextInt(thicknesses.length)]);
            
            // 随机分配属性
            order.setQuantity(500 + random.nextInt(2000));
            // 生产时长随机 12~72 小时
            order.setProductionDurationHours(12.0 + random.nextInt(60));
            
            // 库存天数：模拟部分高危紧急（<10），部分充裕（>30）
            order.setCurrentInventory(random.nextInt(100));
            order.setMonthlyShipment(150 + random.nextInt(100)); // SupplyDays = Cur / (Month/30)
            
            // 期待开工时间随机散布在接下来5天内
            order.setExpectedStartTime(baseTime.plusDays(random.nextInt(5)).plusHours(random.nextInt(12)));
            
            // 产线兼容性
            List<String> compatible = new ArrayList<>();
            compatible.add("Line_" + (1 + random.nextInt(3)));
            if (random.nextBoolean()) {
                compatible.add("Line_" + (1 + random.nextInt(3)));
            }
            order.setCompatibleLines(compatible.stream().collect(java.util.stream.Collectors.toSet()));
            
            // 三分之一几率有偏好线
            if (random.nextInt(3) == 0) {
                order.setPreferredLineCode(compatible.get(0));
            }
            
            orders.add(order);
        }
        
        // 特意添加几个高紧急度订单 (库存极低)
        for (int i = 31; i <= 35; i++) {
            MotherRollOrder urgent = new MotherRollOrder();
            urgent.setId("URGENT-" + i);
            urgent.setFormulaCode("F1");
            urgent.setMaterialCode("M1");
            urgent.setProductCode("PROD-URGENT");
            urgent.setThickness(50);
            urgent.setQuantity(1000.0);
            urgent.setProductionDurationHours(24.0);
            urgent.setCurrentInventory(5); // 非常低
            urgent.setMonthlyShipment(300); // 每天 10，所以只够半天
            urgent.setExpectedStartTime(baseTime);
            urgent.setCompatibleLines(java.util.Set.of("Line_1", "Line_2", "Line_3"));
            orders.add(urgent);
        }
        
        schedule.setOrders(orders);
        return schedule;
    }
}
