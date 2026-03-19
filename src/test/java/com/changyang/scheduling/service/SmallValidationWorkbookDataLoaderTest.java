package com.changyang.scheduling.service;

import com.changyang.scheduling.domain.MotherRollOrder;
import com.changyang.scheduling.domain.MotherRollSchedule;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SmallValidationWorkbookDataLoaderTest {

    private static final Path WORKBOOK_PATH = Path.of("docs", "validation-data", "validation-workbook-small.xlsx");

    private ExcelDataLoader loader;

    @BeforeEach
    void setUp() {
        loader = new ExcelDataLoader(new ChangeoverService());
    }

    @Test
    void loadSmallValidationWorkbook_readsCompactProblemFacts() throws Exception {
        assertTrue(Files.exists(WORKBOOK_PATH), "small validation workbook should exist under docs/validation-data");

        try (InputStream inputStream = Files.newInputStream(WORKBOOK_PATH)) {
            MotherRollSchedule schedule = loader.load(inputStream, LocalDateTime.of(2026, 3, 13, 8, 0));

            assertEquals(42, schedule.getOrders().size());
            assertEquals(2, schedule.getProductionLines().size());
            assertEquals(6, schedule.getExceptionTimes().size());
            assertEquals(4, schedule.getFilterChangePlans().size());
            assertEquals(7, schedule.getFactoryCalendar().getHolidays().size());
        }
    }

    @Test
    void loadSmallValidationWorkbook_preservesSyntheticAndSplitCandidates() throws Exception {
        assertTrue(Files.exists(WORKBOOK_PATH), "small validation workbook should exist under docs/validation-data");

        try (InputStream inputStream = Files.newInputStream(WORKBOOK_PATH)) {
            MotherRollSchedule schedule = loader.load(inputStream, LocalDateTime.of(2026, 3, 13, 8, 0));

            assertTrue(schedule.getOrders().stream().map(MotherRollOrder::getId).anyMatch("VAL-MC1-001"::equals));
            assertTrue(schedule.getOrders().stream().anyMatch(order -> order.getCompatibleLines() != null
                    && order.getCompatibleLines().size() > 1));
            assertTrue(schedule.getOrders().stream().anyMatch(order -> order.getCurrentInventory() > 0
                    && order.getMonthlyShipment() > 0));
            assertTrue(schedule.getOrders().stream().anyMatch(order -> order.getProductionDurationHours() >= 84.0));
        }
    }
}
