package com.changyang.scheduling.service;

import com.changyang.scheduling.domain.MotherRollOrder;
import com.changyang.scheduling.domain.MotherRollSchedule;
import com.changyang.scheduling.rest.dto.ConstraintSelectionDto;
import com.changyang.scheduling.rest.dto.ConstraintStatusDto;
import com.changyang.scheduling.rest.dto.ExcelValidationSummaryDto;
import com.changyang.scheduling.solver.SchedulingConstraintIds;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class ExcelValidationService {

    private static final String EXCEPTION_SHEET = "停机计划";
    private static final String FILTER_CHANGE_SHEET = "过滤器更换计划";
    private static final String FACTORY_CALENDAR_SHEET = "工厂日历";

    private static final DataFormatter DF = new DataFormatter();

    public ExcelValidationSummaryDto analyze(String sourceName, byte[] workbookBytes, MotherRollSchedule schedule) throws IOException {
        RawWorkbookStats rawStats;
        try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(workbookBytes))) {
            rawStats = inspectWorkbook(workbook);
        }

        ExcelValidationSummaryDto summary = new ExcelValidationSummaryDto();
        summary.setSourceName(sourceName);
        summary.setMetrics(buildMetrics(rawStats, schedule));
        summary.setConstraintStatus(buildConstraintStatus(rawStats, schedule));
        summary.setWarnings(buildWarnings(rawStats, schedule));
        return summary;
    }

    public List<String> validateSelection(ConstraintSelectionDto selection, ExcelValidationSummaryDto summary) {
        List<String> errors = new ArrayList<>();
        if (selection == null || summary == null || summary.getConstraintStatus() == null) {
            return errors;
        }

        Map<String, ConstraintStatusDto> statusById = new LinkedHashMap<>();
        for (ConstraintStatusDto status : summary.getConstraintStatus()) {
            statusById.put(status.getId(), status);
        }

        validateConstraint(errors, statusById, SchedulingConstraintIds.HC1, selection.isHc1());
        validateConstraint(errors, statusById, SchedulingConstraintIds.HC2, selection.isHc2());
        validateConstraint(errors, statusById, SchedulingConstraintIds.HC3, selection.isHc3());
        validateConstraint(errors, statusById, SchedulingConstraintIds.HC4, selection.isHc4());
        validateConstraint(errors, statusById, SchedulingConstraintIds.MC1, selection.isMc1());
        validateConstraint(errors, statusById, SchedulingConstraintIds.MC2, selection.isMc2());
        validateConstraint(errors, statusById, SchedulingConstraintIds.SC1, selection.isSc1());
        validateConstraint(errors, statusById, SchedulingConstraintIds.SC2, selection.isSc2());
        validateConstraint(errors, statusById, SchedulingConstraintIds.SC3, selection.isSc3());
        validateConstraint(errors, statusById, SchedulingConstraintIds.SC4, selection.isSc4());
        return errors;
    }

    private void validateConstraint(List<String> errors, Map<String, ConstraintStatusDto> statusById, String id, boolean selected) {
        if (!selected) {
            return;
        }
        ConstraintStatusDto status = statusById.get(id);
        if (status == null) {
            errors.add(id + " 未在当前数据校验结果中出现。");
            return;
        }
        if (!status.isAvailable()) {
            errors.add(status.getLabel() + " 当前模式不可启用：" + status.getNote());
            return;
        }
        if (!status.isDataReady()) {
            errors.add(status.getLabel() + " 当前数据未准备好：" + status.getNote());
        }
    }

    private Map<String, Object> buildMetrics(RawWorkbookStats rawStats, MotherRollSchedule schedule) {
        Map<String, Object> metrics = new LinkedHashMap<>();
        metrics.put("rawOrderRows", rawStats.totalOrderRows);
        metrics.put("parsedOrders", schedule.getOrders().size());
        metrics.put("inventoryBlankRows", rawStats.inventoryBlankRows);
        metrics.put("monthlyShipmentBlankRows", rawStats.monthlyShipmentBlankRows);
        metrics.put("multiLineCompatibleRows", rawStats.multiLineCompatibleRows);
        metrics.put("preferredLineBlankRows", rawStats.preferredLineBlankRows);
        metrics.put("expectedStartBlankRows", rawStats.expectedStartBlankRows);
        metrics.put("mc1PriorityComboHits", rawStats.mc1PriorityComboHits.size());
        metrics.put("exceptionWindows", rawStats.exceptionSheetRows);
        metrics.put("filterChangePlans", rawStats.filterChangeSheetRows);
        metrics.put("calendarRows", rawStats.calendarSheetRows);
        metrics.put("holidayRows", rawStats.holidayRows);
        metrics.put("dualCompatibleOrders", schedule.getOrders().stream().filter(order -> order.getCompatibleLines() != null && order.getCompatibleLines().size() > 1).count());
        metrics.put("urgentInventoryOrders", schedule.getOrders().stream().filter(order -> order.getInventorySupplyDays() < 10.0).count());
        metrics.put("highInventoryOrders", schedule.getOrders().stream().filter(order -> order.getInventorySupplyDays() > 30.0).count());
        metrics.put("longDurationOrders", schedule.getOrders().stream().filter(order -> order.getProductionDurationHours() > 24.0).count());
        metrics.put("productFamilyCount", schedule.getOrders().stream().map(MotherRollOrder::getProductCode).distinct().count());
        metrics.put("thicknessLevelCount", schedule.getOrders().stream().map(MotherRollOrder::getThickness).distinct().count());
        return metrics;
    }

    private List<ConstraintStatusDto> buildConstraintStatus(RawWorkbookStats rawStats, MotherRollSchedule schedule) {
        List<ConstraintStatusDto> statuses = new ArrayList<>();

        boolean inventoryReady = rawStats.totalOrderRows > 0
                && rawStats.inventoryBlankRows < rawStats.totalOrderRows
                && rawStats.monthlyShipmentBlankRows < rawStats.totalOrderRows;
        boolean expectedStartReady = rawStats.expectedStartBlankRows < rawStats.totalOrderRows;
        boolean preferredLineReady = rawStats.preferredLineBlankRows < rawStats.totalOrderRows;
        boolean thicknessReady = schedule.getOrders().stream().map(MotherRollOrder::getThickness).distinct().count() >= 3;
        boolean changeoverReady = schedule.getOrders().size() > 1 && !schedule.getChangeoverEntries().isEmpty();
        boolean mc1Ready = rawStats.filterChangeSheetRows > 0 && !rawStats.mc1PriorityComboHits.isEmpty();

        statuses.add(new ConstraintStatusDto(SchedulingConstraintIds.HC1, "产线兼容", "hard", true, true, true, true,
                rawStats.multiLineCompatibleRows > 0 ? "已存在双线兼容样本，可验证分线兼容关系。" : "仅有单线兼容样本，能验证基础兼容约束。"));
        statuses.add(new ConstraintStatusDto(SchedulingConstraintIds.HC2, "紧急库存优先", "hard", true, inventoryReady, false, inventoryReady,
                inventoryReady ? "库存与月发货量列已提供，可验证 <10 天优先。" : "库存列为空时 loader 会回退估算，当前不建议启用库存类约束。"));
        statuses.add(new ConstraintStatusDto(SchedulingConstraintIds.HC3, "停机冲突", "hard", true, rawStats.exceptionSheetRows > 0, false, rawStats.exceptionSheetRows > 0,
                rawStats.exceptionSheetRows > 0 ? "停机窗口已存在，可验证排程与停机冲突。" : "缺少停机计划 sheet 或无有效行。"));
        statuses.add(new ConstraintStatusDto(SchedulingConstraintIds.HC4, "厚度单峰", "hard", true, thicknessReady, false, thicknessReady,
                thicknessReady ? "厚度层级足够，可验证单峰波浪顺序。" : "厚度层级不足，无法验证单峰波浪。"));
        statuses.add(new ConstraintStatusDto(SchedulingConstraintIds.MC1, "过滤器优先顺序", "medium", true, mc1Ready, false, mc1Ready,
                mc1Ready ? "过滤器计划已接入，关键组合覆盖 " + rawStats.mc1PriorityComboHits.size() + "/7。" : "缺少过滤器计划或关键优先组合覆盖不足。"));
        statuses.add(new ConstraintStatusDto(SchedulingConstraintIds.MC2, "高库存不前移", "medium", true, inventoryReady, false, inventoryReady,
                inventoryReady ? "库存列已提供，可验证 >30 天订单不前移。" : "库存列为空，不建议启用高库存约束。"));
        statuses.add(new ConstraintStatusDto(SchedulingConstraintIds.SC1, "换型最小化", "soft", true, changeoverReady, true, true,
                "当前主链路约束，适合优先验证换线/换型行为。"));
        statuses.add(new ConstraintStatusDto(SchedulingConstraintIds.SC2, "库存天数优先级", "soft", true, inventoryReady, false, inventoryReady,
                inventoryReady ? "库存数据可信时可启用同型号库存优先级。" : "库存数据未提供，不建议启用。"));
        statuses.add(new ConstraintStatusDto(SchedulingConstraintIds.SC3, "期望时间偏差", "soft", true, expectedStartReady, false, expectedStartReady,
                expectedStartReady ? "存在期望开始时间，可验证延迟惩罚。" : "缺少期望开始时间列值。"));
        statuses.add(new ConstraintStatusDto(SchedulingConstraintIds.SC4, "偏好产线", "soft", true, preferredLineReady, false, preferredLineReady,
                preferredLineReady ? "线别已作为 preferredLineCode 载入，可验证偏好产线。" : "缺少偏好产线列。"));
        statuses.add(new ConstraintStatusDto(SchedulingConstraintIds.HC5, "子任务保序", "hard", false, false, false, false,
                "当前 Excel 模式不启用按天拆分，无法验证子任务顺序。"));
        statuses.add(new ConstraintStatusDto(SchedulingConstraintIds.SC5, "拆分连续生产", "soft", false, false, false, false,
                "当前 Excel 模式不启用按天拆分，无法验证拆分连续性。"));
        return statuses;
    }

    private List<String> buildWarnings(RawWorkbookStats rawStats, MotherRollSchedule schedule) {
        List<String> warnings = new ArrayList<>();
        if (rawStats.totalOrderRows > 0 && rawStats.inventoryBlankRows == rawStats.totalOrderRows) {
            warnings.add("当前库存列在原始表中全空，库存类约束如果启用会依赖回退估算值。验证版 workbook 才适合做库存优先验证。");
        }
        if (rawStats.totalOrderRows > 0 && rawStats.monthlyShipmentBlankRows == rawStats.totalOrderRows) {
            warnings.add("月发货量列在原始表中全空，所有库存覆盖天数都不是原始业务数据。" );
        }
        if (rawStats.exceptionSheetRows == 0) {
            warnings.add("未发现停机计划 sheet，HC3 只能保持关闭。" );
        }
        if (rawStats.filterChangeSheetRows == 0) {
            warnings.add("未发现过滤器更换计划 sheet，MC1 只能保持关闭。" );
        }
        if (rawStats.calendarSheetRows > 0 && schedule.getFactoryCalendar() != null && !schedule.getFactoryCalendar().getHolidays().isEmpty()) {
            warnings.add("工厂日历已读入，但当前时间推导仍按 7x24 直加分钟计算，节假日不会真正阻断排程。" );
        }
        if (rawStats.multiLineCompatibleRows == 0) {
            warnings.add("兼容产线列没有双线样本，只能验证基础兼容，不足以验证灵活换线收益。" );
        }
        return warnings;
    }

    private RawWorkbookStats inspectWorkbook(Workbook workbook) {
        RawWorkbookStats stats = new RawWorkbookStats();
        Sheet orderSheet = workbook.getSheetAt(0);

        for (Row row : orderSheet) {
            if (row.getRowNum() == 0) {
                continue;
            }
            String orderId = getCellString(row, 0);
            if (orderId == null || orderId.isBlank()) {
                continue;
            }
            stats.totalOrderRows++;

            if (isBlank(row, 13)) {
                stats.inventoryBlankRows++;
            }
            if (isBlank(row, 14)) {
                stats.monthlyShipmentBlankRows++;
            }
            if (isBlank(row, 15)) {
                stats.compatibilityBlankRows++;
            }
            if (isBlank(row, 8)) {
                stats.preferredLineBlankRows++;
            }
            if (isBlank(row, 6)) {
                stats.expectedStartBlankRows++;
            }

            String compatibleLines = getCellString(row, 15);
            if (compatibleLines != null && compatibleLines.contains(";")) {
                stats.multiLineCompatibleRows++;
            }

            String productCode = getCellString(row, 11);
            Double thickness = getCellDouble(row, 10);
            if (productCode != null && thickness != null) {
                String key = productCode + "_" + thickness.intValue();
                if (SchedulingConstraintIds.MC1_PRIORITY_KEYS.contains(key)) {
                    stats.mc1PriorityComboHits.add(key);
                }
            }
        }

        stats.exceptionSheetRows = countSheetRows(workbook.getSheet(EXCEPTION_SHEET));
        stats.filterChangeSheetRows = countSheetRows(workbook.getSheet(FILTER_CHANGE_SHEET));
        stats.calendarSheetRows = countSheetRows(workbook.getSheet(FACTORY_CALENDAR_SHEET));
        Sheet calendarSheet = workbook.getSheet(FACTORY_CALENDAR_SHEET);
        if (calendarSheet != null) {
            for (Row row : calendarSheet) {
                if (row.getRowNum() == 0) {
                    continue;
                }
                String workFlag = getCellString(row, 1);
                if (workFlag != null && !"Y".equalsIgnoreCase(workFlag)) {
                    stats.holidayRows++;
                }
            }
        }
        return stats;
    }

    private int countSheetRows(Sheet sheet) {
        if (sheet == null) {
            return 0;
        }
        int count = 0;
        for (Row row : sheet) {
            if (row.getRowNum() == 0) {
                continue;
            }
            if (!rowIsEmpty(row)) {
                count++;
            }
        }
        return count;
    }

    private boolean rowIsEmpty(Row row) {
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            if (i < 0) {
                continue;
            }
            if (!isBlank(row, i)) {
                return false;
            }
        }
        return true;
    }

    private boolean isBlank(Row row, int index) {
        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return true;
        }
        if (cell.getCellType() == CellType.BLANK) {
            return true;
        }
        return DF.formatCellValue(cell).trim().isEmpty();
    }

    private String getCellString(Row row, int index) {
        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }
        String value = DF.formatCellValue(cell).trim();
        return value.isEmpty() ? null : value;
    }

    private Double getCellDouble(Row row, int index) {
        Cell cell = row.getCell(index, Row.MissingCellPolicy.RETURN_BLANK_AS_NULL);
        if (cell == null) {
            return null;
        }
        try {
            return switch (cell.getCellType()) {
                case NUMERIC -> cell.getNumericCellValue();
                case STRING -> Double.parseDouble(cell.getStringCellValue().trim());
                default -> null;
            };
        } catch (Exception e) {
            return null;
        }
    }

    private static final class RawWorkbookStats {
        private int totalOrderRows;
        private int inventoryBlankRows;
        private int monthlyShipmentBlankRows;
        private int compatibilityBlankRows;
        private int preferredLineBlankRows;
        private int expectedStartBlankRows;
        private int multiLineCompatibleRows;
        private int exceptionSheetRows;
        private int filterChangeSheetRows;
        private int calendarSheetRows;
        private int holidayRows;
        private final Set<String> mc1PriorityComboHits = new HashSet<>();
    }
}
