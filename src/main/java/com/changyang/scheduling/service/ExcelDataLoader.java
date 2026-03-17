package com.changyang.scheduling.service;

import com.changyang.scheduling.domain.ChangeoverEntry;
import com.changyang.scheduling.domain.ExceptionTime;
import com.changyang.scheduling.domain.FactoryCalendar;
import com.changyang.scheduling.domain.FilterChangePlan;
import com.changyang.scheduling.domain.MotherRollOrder;
import com.changyang.scheduling.domain.MotherRollSchedule;
import com.changyang.scheduling.domain.ProductionLine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeFormatterBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelDataLoader {

    private static final String LINE_2_CODE = "双拉2线";
    private static final String LINE_4_CODE = "双拉4线";

    private static final String EXCEPTION_SHEET = "停机计划";
    private static final String FILTER_CHANGE_SHEET = "过滤器更换计划";
    private static final String FACTORY_CALENDAR_SHEET = "工厂日历";

    private static final DataFormatter DF = new DataFormatter();

    private final ChangeoverService changeoverService;

    private static final Pattern DESC_PATTERN =
            Pattern.compile(".+_T(\\d+)_([A-Z]+)_(\\d+)");

    private static final DateTimeFormatter DATE_FMT =
            DateTimeFormatter.ofPattern("yyyy.MM.dd");

    private static final DateTimeFormatter DATE_TIME_FMT =
            new DateTimeFormatterBuilder().appendPattern("yyyy-MM-dd HH:mm").toFormatter();

    private static final DateTimeFormatter DATE_TIME_DOT_FMT =
            new DateTimeFormatterBuilder().appendPattern("yyyy.MM.dd HH:mm").toFormatter();

    static final Map<String, String> FORMULA_MAP = Map.of(
            "EST", "Formula_EST",
            "ESY", "Formula_EST",
            "FDX", "Formula_FD",
            "FDY", "Formula_FD",
            "DJX", "Formula_DJ",
            "DJY", "Formula_DJ",
            "QDJY", "Formula_DJ"
    );

    static final Map<String, Double> HOURLY_OUTPUT = Map.of(
            "EST", 4000.0,
            "ESY", 4000.0,
            "FDX", 5000.0,
            "FDY", 5000.0,
            "DJX", 6000.0,
            "DJY", 6000.0,
            "QDJY", 6000.0
    );

    public MotherRollSchedule load(InputStream stream, LocalDateTime solveStart) throws Exception {
        List<MotherRollOrder> orders = new ArrayList<>();

        try (Workbook workbook = new XSSFWorkbook(stream)) {
            Sheet orderSheet = workbook.getSheetAt(0);
            int loaded = 0;
            int skipped = 0;

            for (Row row : orderSheet) {
                if (row.getRowNum() == 0) {
                    continue;
                }
                try {
                    MotherRollOrder order = parseRow(row, solveStart);
                    if (order != null) {
                        orders.add(order);
                        loaded++;
                    } else {
                        skipped++;
                    }
                } catch (Exception e) {
                    log.warn("Failed to parse row {}: {}", row.getRowNum() + 1, e.getMessage());
                    skipped++;
                }
            }

            log.info("Excel loaded: valid={}, skipped={}", loaded, skipped);
            logDistribution(orders);

            List<ProductionLine> lines = List.of(
                    new ProductionLine("L2", LINE_2_CODE, LINE_2_CODE, solveStart),
                    new ProductionLine("L4", LINE_4_CODE, LINE_4_CODE, solveStart)
            );

            MotherRollSchedule schedule = new MotherRollSchedule(lines, orders);
            schedule.setChangeoverEntries(buildChangeoverMatrix());
            schedule.setExceptionTimes(parseExceptionTimes(workbook));
            schedule.setFilterChangePlans(parseFilterChangePlans(workbook));
            schedule.setFactoryCalendar(parseFactoryCalendar(workbook));

            changeoverService.initCache(schedule.getChangeoverEntries());

            log.info(
                    "Schedule built: lines={}, orders={}, exceptions={}, filterPlans={}, holidays={}",
                    lines.size(),
                    orders.size(),
                    schedule.getExceptionTimes().size(),
                    schedule.getFilterChangePlans().size(),
                    schedule.getFactoryCalendar() == null ? 0 : schedule.getFactoryCalendar().getHolidays().size()
            );
            return schedule;
        }
    }

    private MotherRollOrder parseRow(Row row, LocalDateTime solveStart) {
        String orderId = getCellString(row, 0);
        if (orderId == null || orderId.isBlank()) {
            return null;
        }

        String productCode = getCellString(row, 11);
        String formulaCode = getCellString(row, 9);
        int thickness = (int) getCellDouble(row, 10);

        if (productCode == null || productCode.isBlank()) {
            String desc = getCellString(row, 2);
            if (desc == null) {
                return null;
            }
            Matcher matcher = DESC_PATTERN.matcher(desc);
            if (!matcher.matches()) {
                return null;
            }
            thickness = Integer.parseInt(matcher.group(1));
            productCode = matcher.group(2);
        }

        if (formulaCode == null || formulaCode.isBlank()) {
            formulaCode = FORMULA_MAP.getOrDefault(productCode, "Formula_Unknown");
        }

        double quantity = getCellDouble(row, 3);
        if (quantity <= 0) {
            return null;
        }

        double durationHours = getCellDouble(row, 12);
        if (durationHours <= 0) {
            durationHours = quantity / HOURLY_OUTPUT.getOrDefault(productCode, 5000.0);
        }
        durationHours = Math.max(durationHours, 1.0);

        double currentInventory = getCellDouble(row, 13);
        double monthlyShipment = getCellDouble(row, 14);
        if (monthlyShipment <= 0) {
            monthlyShipment = quantity / 3.0;
        }

        LocalDateTime expectedStart = parseDate(row, 6, solveStart);
        Set<String> compatibleLines = parseCompatibleLines(getCellString(row, 15), productCode);
        String preferredLineCode = normalizeLineCode(getCellString(row, 8));

        MotherRollOrder order = new MotherRollOrder();
        order.setId(orderId);
        order.setMaterialCode(getCellString(row, 1));
        order.setProductCode(productCode);
        order.setFormulaCode(formulaCode);
        order.setThickness(thickness);
        order.setQuantity(quantity);
        order.setProductionDurationHours(durationHours);
        order.setCurrentInventory(currentInventory);
        order.setMonthlyShipment(monthlyShipment);
        order.setExpectedStartTime(expectedStart);
        order.setCompatibleLines(compatibleLines);
        order.setPreferredLineCode(preferredLineCode);
        return order;
    }

    public List<ChangeoverEntry> buildChangeoverMatrix() {
        List<ChangeoverEntry> entries = new ArrayList<>();
        String[] codes = {"EST", "ESY", "FDX", "FDY", "DJX", "DJY", "QDJY"};

        for (String from : codes) {
            for (String to : codes) {
                if (from.equals(to)) {
                    continue;
                }
                entries.add(new ChangeoverEntry(
                        ChangeoverEntry.Type.FORMULA_MODEL,
                        FORMULA_MAP.get(from),
                        from,
                        FORMULA_MAP.get(to),
                        to,
                        null,
                        null,
                        calcFormulaModelChangeover(from, to)
                ));
            }
        }

        int[] thicknesses = {4, 5, 7, 9, 10, 14, 24, 29, 42, 61};
        for (int i = 0; i < thicknesses.length; i++) {
            for (int j = 0; j < thicknesses.length; j++) {
                if (i == j) {
                    continue;
                }
                int steps = Math.abs(i - j);
                int changeoverMinutes = steps == 1 ? 20 : steps == 2 ? 40 : 60;
                entries.add(new ChangeoverEntry(
                        ChangeoverEntry.Type.THICKNESS,
                        null,
                        null,
                        null,
                        null,
                        thicknesses[i],
                        thicknesses[j],
                        changeoverMinutes
                ));
            }
        }

        return entries;
    }

    private int calcFormulaModelChangeover(String from, String to) {
        String fromFamily = family(from);
        String toFamily = family(to);
        if (fromFamily.equals(toFamily)) {
            return 30;
        }
        if (between(fromFamily, toFamily, "EST", "FD")) {
            return 120;
        }
        if (between(fromFamily, toFamily, "EST", "DJ")) {
            return 180;
        }
        if (between(fromFamily, toFamily, "FD", "DJ")) {
            return 150;
        }
        return 120;
    }

    private String family(String code) {
        if ("EST".equals(code) || "ESY".equals(code)) {
            return "EST";
        }
        if ("FDX".equals(code) || "FDY".equals(code)) {
            return "FD";
        }
        return "DJ";
    }

    private boolean between(String a, String b, String x, String y) {
        return (a.equals(x) && b.equals(y)) || (a.equals(y) && b.equals(x));
    }

    private void logDistribution(List<MotherRollOrder> orders) {
        Map<String, Long> productDistribution = orders.stream()
                .collect(Collectors.groupingBy(MotherRollOrder::getProductCode, Collectors.counting()));
        Map<Integer, Long> thicknessDistribution = orders.stream()
                .collect(Collectors.groupingBy(MotherRollOrder::getThickness, Collectors.counting()));

        log.info("=== Product distribution ===");
        productDistribution.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> log.info("  {} : {}", entry.getKey(), entry.getValue()));

        log.info("=== Thickness distribution ===");
        thicknessDistribution.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> log.info("  {}um : {}", entry.getKey(), entry.getValue()));
    }

    private String getCellString(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }
        String value = DF.formatCellValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    private double getCellDouble(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return 0.0;
        }
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> cell.getNumericCellValue();
                case STRING -> Double.parseDouble(cell.getStringCellValue().replace(",", "").trim());
                default -> 0.0;
            };
        } catch (Exception e) {
            return 0.0;
        }
    }

    private LocalDateTime parseDate(Row row, int col, LocalDateTime fallback) {
        LocalDateTime parsed = parseDateTimeCell(row, col);
        return parsed == null ? fallback : parsed;
    }

    private List<ExceptionTime> parseExceptionTimes(Workbook workbook) {
        Sheet sheet = workbook.getSheet(EXCEPTION_SHEET);
        if (sheet == null) {
            return new ArrayList<>();
        }

        List<ExceptionTime> result = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) {
                continue;
            }
            String lineId = getCellString(row, 0);
            LocalDateTime startTime = parseDateTimeCell(row, 2);
            LocalDateTime endTime = parseDateTimeCell(row, 3);
            if (lineId == null || startTime == null || endTime == null) {
                continue;
            }
            result.add(new ExceptionTime(lineId.trim(), startTime, endTime));
        }
        return result;
    }

    private List<FilterChangePlan> parseFilterChangePlans(Workbook workbook) {
        Sheet sheet = workbook.getSheet(FILTER_CHANGE_SHEET);
        if (sheet == null) {
            return new ArrayList<>();
        }

        List<FilterChangePlan> result = new ArrayList<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) {
                continue;
            }
            String lineId = getCellString(row, 0);
            LocalDateTime changeTime = parseDateTimeCell(row, 2);
            int downtimeMinutes = (int) Math.round(getCellDouble(row, 3));
            if (lineId == null || changeTime == null || downtimeMinutes <= 0) {
                continue;
            }
            result.add(new FilterChangePlan(lineId.trim(), changeTime, downtimeMinutes));
        }
        return result;
    }

    private FactoryCalendar parseFactoryCalendar(Workbook workbook) {
        Sheet sheet = workbook.getSheet(FACTORY_CALENDAR_SHEET);
        if (sheet == null) {
            return new FactoryCalendar(new HashSet<>());
        }

        Set<LocalDate> holidays = new HashSet<>();
        for (Row row : sheet) {
            if (row.getRowNum() == 0) {
                continue;
            }
            LocalDate date = parseLocalDateCell(row, 0);
            String isWorkingDay = getCellString(row, 1);
            if (date == null || isWorkingDay == null) {
                continue;
            }
            if (!"Y".equalsIgnoreCase(isWorkingDay.trim())) {
                holidays.add(date);
            }
        }
        return new FactoryCalendar(holidays);
    }

    private LocalDateTime parseDateTimeCell(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }

        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue();
            }

            String value = DF.formatCellValue(cell).trim();
            if (value.isEmpty()) {
                return null;
            }

            try {
                return LocalDateTime.parse(value, DATE_TIME_FMT);
            } catch (Exception ignored) {
            }

            try {
                return LocalDateTime.parse(value, DATE_TIME_DOT_FMT);
            } catch (Exception ignored) {
            }

            try {
                return LocalDateTime.parse(value);
            } catch (Exception ignored) {
            }

            return LocalDate.parse(value, DATE_FMT).atStartOfDay();
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDate parseLocalDateCell(Row row, int col) {
        Cell cell = row.getCell(col, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }

        try {
            if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
                return cell.getLocalDateTimeCellValue().toLocalDate();
            }

            String value = DF.formatCellValue(cell).trim();
            if (value.isEmpty()) {
                return null;
            }

            try {
                return LocalDate.parse(value);
            } catch (Exception ignored) {
                return LocalDate.parse(value, DATE_FMT);
            }
        } catch (Exception e) {
            return null;
        }
    }

    private Set<String> parseCompatibleLines(String compatCellValue, String productCode) {
        if (compatCellValue != null && !compatCellValue.isBlank()) {
            return Arrays.stream(compatCellValue.split(";"))
                    .map(String::trim)
                    .filter(value -> !value.isEmpty())
                    .map(this::normalizeLineCode)
                    .collect(Collectors.toCollection(HashSet::new));
        }

        if ("QDJY".equals(productCode)) {
            return Set.of(LINE_2_CODE);
        }
        return new HashSet<>(Set.of(LINE_2_CODE, LINE_4_CODE));
    }

    private String normalizeLineCode(String value) {
        if (value == null) {
            return null;
        }

        String trimmed = value.trim();
        if (trimmed.isEmpty()) {
            return null;
        }

        return switch (trimmed.toUpperCase(Locale.ROOT)) {
            case "L2" -> LINE_2_CODE;
            case "L4" -> LINE_4_CODE;
            default -> trimmed;
        };
    }
}
