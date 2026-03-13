package com.changyang.scheduling.service;

import com.changyang.scheduling.domain.ChangeoverEntry;
import com.changyang.scheduling.domain.MotherRollOrder;
import com.changyang.scheduling.domain.MotherRollSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Excel 数据加载器单元测试
 * <p>
 * 使用 classpath 中的 "data/生产订单_排程导入版.xlsx" 进行测试。
 * </p>
 */
class ExcelDataLoaderTest {

    private ExcelDataLoader loader;
    private ChangeoverService changeoverService;

    @BeforeEach
    void setUp() {
        changeoverService = new ChangeoverService();
        loader = new ExcelDataLoader(changeoverService);
    }

    @Test
    void testLoadExcel_基本加载() throws Exception {
        LocalDateTime solveStart = LocalDateTime.of(2026, 3, 13, 8, 0);

        try (InputStream is = new ClassPathResource("data/生产订单_排程导入版.xlsx").getInputStream()) {
            MotherRollSchedule schedule = loader.load(is, solveStart);

            // 验证产线为2条
            assertNotNull(schedule.getProductionLines());
            assertEquals(2, schedule.getProductionLines().size());
            assertEquals("L2", schedule.getProductionLines().get(0).getId());
            assertEquals("L4", schedule.getProductionLines().get(1).getId());
            assertEquals("双拉2线", schedule.getProductionLines().get(0).getLineCode());
            assertEquals("双拉4线", schedule.getProductionLines().get(1).getLineCode());

            // 验证订单数量 > 0
            assertNotNull(schedule.getOrders());
            assertFalse(schedule.getOrders().isEmpty(), "应至少加载一条订单");
            System.out.println("加载订单数: " + schedule.getOrders().size());
        }
    }

    @Test
    void testLoadExcel_型号范围正确() throws Exception {
        LocalDateTime solveStart = LocalDateTime.of(2026, 3, 13, 8, 0);
        Set<String> validCodes = Set.of("EST", "ESY", "FDX", "FDY", "DJX", "DJY", "QDJY");

        try (InputStream is = new ClassPathResource("data/生产订单_排程导入版.xlsx").getInputStream()) {
            MotherRollSchedule schedule = loader.load(is, solveStart);

            for (MotherRollOrder order : schedule.getOrders()) {
                assertTrue(validCodes.contains(order.getProductCode()),
                        "型号 " + order.getProductCode() + "（订单 " + order.getId() + "）不在有效范围内");
            }
        }
    }

    @Test
    void testLoadExcel_厚度范围正确() throws Exception {
        LocalDateTime solveStart = LocalDateTime.of(2026, 3, 13, 8, 0);
        Set<Integer> validThicknesses = Set.of(4, 5, 7, 9, 10, 14, 24, 29, 42, 61);

        try (InputStream is = new ClassPathResource("data/生产订单_排程导入版.xlsx").getInputStream()) {
            MotherRollSchedule schedule = loader.load(is, solveStart);

            for (MotherRollOrder order : schedule.getOrders()) {
                assertTrue(validThicknesses.contains(order.getThickness()),
                        "厚度 " + order.getThickness() + "μm（订单 " + order.getId() + "）不在有效范围内");
            }
        }
    }

    @Test
    void testLoadExcel_配方映射正确() throws Exception {
        LocalDateTime solveStart = LocalDateTime.of(2026, 3, 13, 8, 0);

        try (InputStream is = new ClassPathResource("data/生产订单_排程导入版.xlsx").getInputStream()) {
            MotherRollSchedule schedule = loader.load(is, solveStart);

            for (MotherRollOrder order : schedule.getOrders()) {
                String expected = ExcelDataLoader.FORMULA_MAP.get(order.getProductCode());
                if (expected != null) {
                    assertEquals(expected, order.getFormulaCode(),
                            "订单 " + order.getId() + " 型号 " + order.getProductCode() + " 配方编码不匹配");
                }
            }
        }
    }

    @Test
    void testLoadExcel_QDJY只兼容双拉2线() throws Exception {
        LocalDateTime solveStart = LocalDateTime.of(2026, 3, 13, 8, 0);

        try (InputStream is = new ClassPathResource("data/生产订单_排程导入版.xlsx").getInputStream()) {
            MotherRollSchedule schedule = loader.load(is, solveStart);

            for (MotherRollOrder order : schedule.getOrders()) {
                if ("QDJY".equals(order.getProductCode())) {
                    assertEquals(Set.of("双拉2线"), order.getCompatibleLines(),
                            "QDJY 订单 " + order.getId() + " 应只兼容双拉2线");
                }
            }
        }
    }

    @Test
    void testLoadExcel_生产时长合理() throws Exception {
        LocalDateTime solveStart = LocalDateTime.of(2026, 3, 13, 8, 0);

        try (InputStream is = new ClassPathResource("data/生产订单_排程导入版.xlsx").getInputStream()) {
            MotherRollSchedule schedule = loader.load(is, solveStart);

            for (MotherRollOrder order : schedule.getOrders()) {
                assertTrue(order.getProductionDurationHours() >= 1.0,
                        "订单 " + order.getId() + "（qty=" + order.getQuantity()
                                + "）生产时长不应小于1小时，实际: " + order.getProductionDurationHours());
                // 上限放宽（大订单可能上千小时）
            }
        }
    }

    @Test
    void testBuildChangeoverMatrix_条目数正确() {
        List<ChangeoverEntry> entries = loader.buildChangeoverMatrix();

        // 型号换型：7种型号，7×6=42 条（排除自身）
        long formulaCount = entries.stream()
                .filter(e -> e.getType() == ChangeoverEntry.Type.FORMULA_MODEL).count();
        assertEquals(42, formulaCount, "型号换型条目数应为 7×6=42");

        // 厚度换型：10种厚度，10×9=90 条（排除自身）
        long thicknessCount = entries.stream()
                .filter(e -> e.getType() == ChangeoverEntry.Type.THICKNESS).count();
        assertEquals(90, thicknessCount, "厚度换型条目数应为 10×9=90");

        System.out.println("换型矩阵条目总数: " + entries.size() + " (型号:" + formulaCount + " + 厚度:" + thicknessCount + ")");
    }

    @Test
    void testBuildChangeoverMatrix_换型时间值正确() {
        List<ChangeoverEntry> entries = loader.buildChangeoverMatrix();

        // 验证同族换型 30 分钟
        ChangeoverEntry estToEsy = entries.stream()
                .filter(e -> e.getType() == ChangeoverEntry.Type.FORMULA_MODEL
                        && "EST".equals(e.getFromProductCode()) && "ESY".equals(e.getToProductCode()))
                .findFirst().orElseThrow();
        assertEquals(30, estToEsy.getChangeoverMinutes(), "EST→ESY 同族换型应为30分钟");

        // 验证 EST ↔ FD 跨族 120 分钟
        ChangeoverEntry estToFdx = entries.stream()
                .filter(e -> e.getType() == ChangeoverEntry.Type.FORMULA_MODEL
                        && "EST".equals(e.getFromProductCode()) && "FDX".equals(e.getToProductCode()))
                .findFirst().orElseThrow();
        assertEquals(120, estToFdx.getChangeoverMinutes(), "EST→FDX 跨族换型应为120分钟");

        // 验证 EST ↔ DJ 跨族 180 分钟
        ChangeoverEntry estToDjy = entries.stream()
                .filter(e -> e.getType() == ChangeoverEntry.Type.FORMULA_MODEL
                        && "EST".equals(e.getFromProductCode()) && "DJY".equals(e.getToProductCode()))
                .findFirst().orElseThrow();
        assertEquals(180, estToDjy.getChangeoverMinutes(), "EST→DJY 跨族换型应为180分钟");
    }

    @Test
    void testLoadExcel_换型缓存已初始化() throws Exception {
        LocalDateTime solveStart = LocalDateTime.of(2026, 3, 13, 8, 0);

        try (InputStream is = new ClassPathResource("data/生产订单_排程导入版.xlsx").getInputStream()) {
            loader.load(is, solveStart);
        }

        // 验证 ChangeoverService 缓存已初始化
        assertNotNull(ChangeoverService.getInstance(), "ChangeoverService 单例应已初始化");

        // 构造两个不同型号的订单测试换型计算
        MotherRollOrder est = new MotherRollOrder();
        est.setFormulaCode("Formula_EST"); est.setProductCode("EST"); est.setThickness(29);

        MotherRollOrder fdx = new MotherRollOrder();
        fdx.setFormulaCode("Formula_FD"); fdx.setProductCode("FDX"); fdx.setThickness(29);

        int changeover = changeoverService.calcChangeover(est, fdx);
        assertEquals(120, changeover, "EST→FDX 换型时间应为120分钟");
    }
}
