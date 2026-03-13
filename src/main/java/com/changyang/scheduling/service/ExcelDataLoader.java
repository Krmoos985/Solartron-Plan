package com.changyang.scheduling.service;

import com.changyang.scheduling.domain.*;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Excel 导入加载器（适配真实生产数据 "排程导入版"）
 *
 * <h3>读取的 Excel 列（从0开始）</h3>
 * <pre>
 *  0  订单号
 *  1  物料编码
 *  2  物料描述       → 解析 thickness, productCode
 *  3  订单数量(M2)
 *  4  确认产量
 *  5  已交货
 *  6  排产开始       → expectedStartTime
 *  7  计划完工
 *  8  线别           → preferredLineCode
 *  9  配方编码       → formulaCode（自动推断列）
 * 10  厚度(μm)       → thickness（自动解析列）
 * 11  型号           → productCode（自动解析列）
 * 12  生产时长(小时)  → productionDurationHours ⚠️需确认
 * 13  当前库存(M2)   → currentInventory ⚠️需填写
 * 14  月发货量(M2)   → monthlyShipment ⚠️需填写
 * 15  兼容产线        → compatibleLines
 * </pre>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelDataLoader {

    private final ChangeoverService changeoverService;

    private static final Pattern DESC_PATTERN =
            Pattern.compile(".+_T(\\d+)_([A-Z]+)_(\\d+)");

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy.MM.dd");

    /** 型号 → 配方编码 */
    static final Map<String, String> FORMULA_MAP = Map.of(
            "EST",  "Formula_EST", "ESY", "Formula_EST",
            "FDX",  "Formula_FD",  "FDY", "Formula_FD",
            "DJX",  "Formula_DJ",  "DJY", "Formula_DJ", "QDJY", "Formula_DJ"
    );

    /**
     * 每小时产量估算 (M2/h)
     * ⚠️ 必须与工艺部门确认后替换！
     */
    static final Map<String, Double> HOURLY_OUTPUT = Map.of(
            "EST", 4000.0, "ESY", 4000.0,
            "FDX", 5000.0, "FDY", 5000.0,
            "DJX", 6000.0, "DJY", 6000.0, "QDJY", 6000.0
    );

    // ==================== 主方法 ====================

    /**
     * 从 "排程导入版" Excel 加载，构建可提交给 Solver 的 MotherRollSchedule
     *
     * @param stream      Excel 文件流
     * @param solveStart  排程基准时间（产线可用开始时间）
     */
    public MotherRollSchedule load(InputStream stream, LocalDateTime solveStart) throws Exception {
        List<MotherRollOrder> orders = new ArrayList<>();

        try (Workbook wb = new XSSFWorkbook(stream)) {
            Sheet sheet = wb.getSheetAt(0);
            int loaded = 0, skipped = 0;
            for (Row row : sheet) {
                if (row.getRowNum() == 0) continue;
                try {
                    MotherRollOrder order = parseRow(row, solveStart);
                    if (order != null) { orders.add(order); loaded++; }
                    else skipped++;
                } catch (Exception e) {
                    log.warn("第{}行解析失败: {}", row.getRowNum() + 1, e.getMessage());
                    skipped++;
                }
            }
            log.info("Excel加载: {}条有效, {}条跳过", loaded, skipped);
        }

        // 输出型号和厚度分布统计
        logDistribution(orders);

        // 实际只有2条产线
        List<ProductionLine> lines = List.of(
                new ProductionLine("L2", "双拉2线", "双拉2线", solveStart),
                new ProductionLine("L4", "双拉4线", "双拉4线", solveStart)
        );

        MotherRollSchedule schedule = new MotherRollSchedule(lines, orders);
        schedule.setChangeoverEntries(buildChangeoverMatrix());
        schedule.setFactoryCalendar(new FactoryCalendar(new HashSet<>()));
        schedule.setExceptionTimes(new ArrayList<>());
        schedule.setFilterChangePlans(new ArrayList<>());

        // 初始化换型缓存
        changeoverService.initCache(schedule.getChangeoverEntries());

        log.info("Schedule构建完成: {}条产线, {}条订单", lines.size(), orders.size());
        return schedule;
    }

    // ==================== 行解析 ====================

    private MotherRollOrder parseRow(Row row, LocalDateTime solveStart) {
        String orderId = getCellString(row, 0);
        if (orderId == null || orderId.isBlank()) return null;

        // 优先读已解析列（9-11），失败回退到物料描述
        String productCode = getCellString(row, 11);
        String formulaCode = getCellString(row, 9);
        int thickness = (int) getCellDouble(row, 10);

        if (productCode == null || productCode.isBlank()) {
            String desc = getCellString(row, 2);
            if (desc == null) return null;
            Matcher m = DESC_PATTERN.matcher(desc);
            if (!m.matches()) return null;
            thickness   = Integer.parseInt(m.group(1));
            productCode = m.group(2);
        }
        if (formulaCode == null || formulaCode.isBlank()) {
            formulaCode = FORMULA_MAP.getOrDefault(productCode, "Formula_Unknown");
        }

        double qty = getCellDouble(row, 3);
        if (qty <= 0) return null;

        // 生产时长：优先读Excel列，否则估算
        double duration = getCellDouble(row, 12);
        if (duration <= 0) {
            duration = qty / HOURLY_OUTPUT.getOrDefault(productCode, 5000.0);
        }
        duration = Math.max(duration, 1.0);

        double inventory = getCellDouble(row, 13);
        double monthly   = getCellDouble(row, 14);
        if (monthly <= 0) monthly = qty / 3.0;

        LocalDateTime expectedStart = parseDate(row, 6, solveStart);

        // 兼容产线
        String compatStr = getCellString(row, 15);
        Set<String> compatibleLines;
        if (compatStr != null && !compatStr.isBlank()) {
            compatibleLines = new HashSet<>(Arrays.asList(compatStr.split(";")));
        } else {
            compatibleLines = "QDJY".equals(productCode)
                    ? Set.of("双拉2线")
                    : new HashSet<>(Set.of("双拉2线", "双拉4线"));
        }

        String preferredLine = getCellString(row, 8);
        if (preferredLine != null && preferredLine.isBlank()) preferredLine = null;

        MotherRollOrder order = new MotherRollOrder();
        order.setId(orderId);
        order.setMaterialCode(getCellString(row, 1));
        order.setProductCode(productCode);
        order.setFormulaCode(formulaCode);
        order.setThickness(thickness);
        order.setQuantity(qty);
        order.setProductionDurationHours(duration);
        order.setCurrentInventory(inventory);
        order.setMonthlyShipment(monthly);
        order.setExpectedStartTime(expectedStart);
        order.setCompatibleLines(compatibleLines);
        order.setPreferredLineCode(preferredLine);
        return order;
    }

    // ==================== 换型矩阵 ====================

    /**
     * 换型矩阵
     * ⚠️ 以下时间均为估算值，需工艺/设备工程师确认！
     *
     * 型号族换型（分钟）:
     *   同族内(如EST→ESY) = 30
     *   EST ↔ FD          = 120
     *   EST ↔ DJ          = 180
     *   FD  ↔ DJ          = 150
     *
     * 厚度档换型（分钟）: 相邻1档=20, 2档=40, ≥3档=60
     */
    public List<ChangeoverEntry> buildChangeoverMatrix() {
        List<ChangeoverEntry> entries = new ArrayList<>();
        String[] codes = {"EST", "ESY", "FDX", "FDY", "DJX", "DJY", "QDJY"};

        for (String from : codes) {
            for (String to : codes) {
                if (from.equals(to)) continue;
                entries.add(new ChangeoverEntry(
                        ChangeoverEntry.Type.FORMULA_MODEL,
                        FORMULA_MAP.get(from), from,
                        FORMULA_MAP.get(to),   to,
                        null, null,
                        calcFormulaModelChangeover(from, to)));
            }
        }

        int[] thicknesses = {4, 5, 7, 9, 10, 14, 24, 29, 42, 61};
        for (int i = 0; i < thicknesses.length; i++) {
            for (int j = 0; j < thicknesses.length; j++) {
                if (i == j) continue;
                int steps = Math.abs(i - j);
                int min   = steps == 1 ? 20 : steps == 2 ? 40 : 60;
                entries.add(new ChangeoverEntry(
                        ChangeoverEntry.Type.THICKNESS,
                        null, null, null, null,
                        thicknesses[i], thicknesses[j], min));
            }
        }
        return entries;
    }

    private int calcFormulaModelChangeover(String from, String to) {
        String f1 = family(from), f2 = family(to);
        if (f1.equals(f2)) return 30;
        if (between(f1, f2, "EST", "FD"))  return 120;
        if (between(f1, f2, "EST", "DJ"))  return 180;
        if (between(f1, f2, "FD",  "DJ"))  return 150;
        return 120;
    }

    private String family(String code) {
        if ("EST".equals(code) || "ESY".equals(code)) return "EST";
        if ("FDX".equals(code) || "FDY".equals(code)) return "FD";
        return "DJ";
    }

    private boolean between(String a, String b, String x, String y) {
        return (a.equals(x) && b.equals(y)) || (a.equals(y) && b.equals(x));
    }

    // ==================== 日志统计 ====================

    private void logDistribution(List<MotherRollOrder> orders) {
        Map<String, Long> productDist = orders.stream()
                .collect(Collectors.groupingBy(MotherRollOrder::getProductCode, Collectors.counting()));
        Map<Integer, Long> thicknessDist = orders.stream()
                .collect(Collectors.groupingBy(MotherRollOrder::getThickness, Collectors.counting()));

        log.info("=== 型号分布 ===");
        productDist.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> log.info("  {} : {}条", e.getKey(), e.getValue()));

        log.info("=== 厚度分布 ===");
        thicknessDist.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(e -> log.info("  {}μm : {}条", e.getKey(), e.getValue()));
    }

    // ==================== 工具 ====================

    private static final DataFormatter DF = new DataFormatter();

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return null;
        String v = DF.formatCellValue(cell).trim();
        return v.isEmpty() ? null : v;
    }

    private double getCellDouble(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return 0.0;
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> cell.getNumericCellValue();
                case STRING  -> Double.parseDouble(cell.getStringCellValue().replace(",", "").trim());
                default -> 0.0;
            };
        } catch (Exception e) { return 0.0; }
    }

    /**
     * 日期解析：支持字符串格式（yyyy.MM.dd）和 Excel 数值型日期
     */
    private LocalDateTime parseDate(Row row, int col, LocalDateTime fallback) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) return fallback;

        try {
            // 优先处理 Excel 数值型日期
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                java.util.Date date = cell.getDateCellValue();
                return date.toInstant().atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
            }

            // 回退到字符串解析
            String s = DF.formatCellValue(cell).trim();
            if (s.isEmpty()) return fallback;
            return LocalDate.parse(s, DATE_FMT).atStartOfDay();
        } catch (Exception e) {
            return fallback;
        }
    }
}
