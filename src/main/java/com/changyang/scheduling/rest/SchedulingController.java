package com.changyang.scheduling.rest;

import ai.timefold.solver.core.api.solver.SolverStatus;
import com.changyang.scheduling.domain.MergedTask;
import com.changyang.scheduling.domain.MotherRollSchedule;
import com.changyang.scheduling.domain.ProductionLine;
import com.changyang.scheduling.rest.dto.ExcelValidationSummaryDto;
import com.changyang.scheduling.rest.dto.SolveRequestConfigDto;
import com.changyang.scheduling.service.DemoDataGenerator;
import com.changyang.scheduling.service.ExcelDataLoader;
import com.changyang.scheduling.service.ExcelValidationService;
import com.changyang.scheduling.service.SchedulingService;
import com.changyang.scheduling.service.TaskMerger;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@RestController
@RequestMapping("/api/v1/scheduling")
@RequiredArgsConstructor
public class SchedulingController {

    private final SchedulingService schedulingService;
    private final TaskMerger taskMerger;
    private final DemoDataGenerator demoDataGenerator;
    private final ExcelDataLoader excelDataLoader;
    private final ExcelValidationService excelValidationService;
    private final ObjectMapper objectMapper;

    @PostMapping("/solve")
    public ResponseEntity<Map<String, Object>> solve(@RequestBody MotherRollSchedule problem) {
        MotherRollSchedule solution = schedulingService.solve(problem);
        return ResponseEntity.ok(buildResponse(solution, null, null));
    }

    @PostMapping("/solve-async")
    public ResponseEntity<Map<String, String>> solveAsync(@RequestBody MotherRollSchedule problem) {
        String jobId = schedulingService.solveAsync(problem);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId, "message", "排程任务已提交"));
    }

    @PostMapping("/analyze-excel")
    public ResponseEntity<?> analyzeExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "startTime", required = false) String startTime) {
        try {
            LocalDateTime solveStart = parseStartTime(startTime);
            byte[] workbookBytes = file.getBytes();
            MotherRollSchedule problem = excelDataLoader.load(file.getInputStream(), solveStart);
            ExcelValidationSummaryDto validation = excelValidationService.analyze(file.getOriginalFilename(), workbookBytes, problem);
            return ResponseEntity.ok(validation);
        } catch (Exception e) {
            log.error("Excel数据验证失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/solve-excel")
    public ResponseEntity<?> solveExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "config", required = false) String configJson) {
        try {
            LocalDateTime solveStart = parseStartTime(startTime);
            SolveRequestConfigDto config = parseConfig(configJson);
            byte[] workbookBytes = file.getBytes();
            MotherRollSchedule problem = excelDataLoader.load(file.getInputStream(), solveStart);
            ExcelValidationSummaryDto validation = excelValidationService.analyze(file.getOriginalFilename(), workbookBytes, problem);

            List<String> selectionErrors = excelValidationService.validateSelection(config.getConstraints(), validation);
            if (!selectionErrors.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "errors", selectionErrors,
                        "validation", validation
                ));
            }

            MotherRollSchedule solution = schedulingService.solve(problem, config);
            return ResponseEntity.ok(buildResponse(solution, validation, config));
        } catch (Exception e) {
            log.error("Excel排程失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/solve-excel-async")
    public ResponseEntity<?> solveExcelAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "startTime", required = false) String startTime,
            @RequestParam(value = "config", required = false) String configJson) {
        try {
            LocalDateTime solveStart = parseStartTime(startTime);
            SolveRequestConfigDto config = parseConfig(configJson);
            byte[] workbookBytes = file.getBytes();
            MotherRollSchedule problem = excelDataLoader.load(file.getInputStream(), solveStart);
            ExcelValidationSummaryDto validation = excelValidationService.analyze(file.getOriginalFilename(), workbookBytes, problem);

            List<String> selectionErrors = excelValidationService.validateSelection(config.getConstraints(), validation);
            if (!selectionErrors.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of(
                        "errors", selectionErrors,
                        "validation", validation
                ));
            }

            String jobId = schedulingService.solveAsync(problem, config);
            return ResponseEntity.accepted().body(Map.of(
                    "jobId", jobId,
                    "message", "Excel排程任务已提交"
            ));
        } catch (Exception e) {
            log.error("Excel异步排程提交失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/demo-real")
    public ResponseEntity<Map<String, Object>> demoReal(
            @RequestParam(value = "startTime", required = false) String startTime) {
        try {
            LocalDateTime solveStart = parseStartTime(startTime);
            ClassPathResource resource = new ClassPathResource("data/生产订单_排程导入版.xlsx");
            try (InputStream is = resource.getInputStream()) {
                MotherRollSchedule problem = excelDataLoader.load(is, solveStart);
                MotherRollSchedule solution = schedulingService.solve(problem);
                return ResponseEntity.ok(buildResponse(solution, null, null));
            }
        } catch (Exception e) {
            log.error("真实数据演示排程失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    @GetMapping("/status/{jobId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable String jobId) {
        SolverStatus status = schedulingService.getStatus(jobId);
        MotherRollSchedule partialOrFinalSolution = schedulingService.getResult(jobId);

        Map<String, Object> response = new HashMap<>();
        response.put("jobId", jobId);
        response.put("status", status.name());

        if (partialOrFinalSolution != null) {
            response.put("score", partialOrFinalSolution.getScore() != null ? partialOrFinalSolution.getScore().toString() : "N/A");
            if (status == SolverStatus.NOT_SOLVING) {
                response.put("result", buildResponse(partialOrFinalSolution, null, null));
            }
        }

        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/stop/{jobId}")
    public ResponseEntity<Map<String, String>> stopSolver(@PathVariable String jobId) {
        schedulingService.stopSolver(jobId);
        return ResponseEntity.ok(Map.of("message", "任务 " + jobId + " 终止信号已发送"));
    }

    @GetMapping("/demo")
    public ResponseEntity<MotherRollSchedule> getDemoData() {
        return ResponseEntity.ok(demoDataGenerator.generateDemoData());
    }

    @GetMapping("/validation-workbook")
    public ResponseEntity<Resource> getValidationWorkbook() {
        try {
            Path workbookPath = Path.of("docs", "validation-data", "validation-workbook.xlsx")
                    .toAbsolutePath()
                    .normalize();
            if (!Files.exists(workbookPath)) {
                return ResponseEntity.notFound().build();
            }

            Resource resource = new FileSystemResource(workbookPath.toFile());
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                            .filename("validation-workbook.xlsx", StandardCharsets.UTF_8)
                            .build()
                            .toString())
                    .contentType(MediaType.parseMediaType(
                            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    .body(resource);
        } catch (Exception e) {
            log.error("读取内置验证 workbook 失败", e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private Map<String, Object> buildResponse(
            MotherRollSchedule schedule,
            ExcelValidationSummaryDto validation,
            SolveRequestConfigDto config) {
        Map<String, Object> responseMap = new HashMap<>();
        if (schedule.getScore() != null) {
            responseMap.put("score", schedule.getScore().toString());
            responseMap.put("isFeasible", schedule.getScore().isFeasible());
        }

        int unassignedCount = SchedulingService.getUnassignedCount(schedule);
        responseMap.put("totalOrders", schedule.getOrders().size());
        responseMap.put("unassignedCount", unassignedCount);
        responseMap.put("isFullyInitialized", unassignedCount == 0);

        Map<String, List<MergedTask>> lineTasks = new HashMap<>();
        if (schedule.getProductionLines() != null) {
            for (ProductionLine line : schedule.getProductionLines()) {
                lineTasks.put(line.getId(), taskMerger.mergeTasks(line));
            }
        }
        responseMap.put("lineTasks", lineTasks);

        if (schedule.getProductionLines() != null) {
            Map<String, Object> stats = new HashMap<>();
            for (ProductionLine line : schedule.getProductionLines()) {
                Map<String, Object> lineStats = new HashMap<>();
                lineStats.put("orderCount", line.getOrders().size());
                if (!line.getOrders().isEmpty()) {
                    lineStats.put("firstStart", line.getOrders().get(0).getStartTime());
                    lineStats.put("lastEnd", line.getOrders().get(line.getOrders().size() - 1).getEndTime());
                }
                stats.put(line.getName(), lineStats);
            }
            responseMap.put("lineStats", stats);
        }

        if (validation != null) {
            responseMap.put("validation", validation);
        }
        if (config != null) {
            responseMap.put("appliedConfig", config);
        }
        return responseMap;
    }

    private SolveRequestConfigDto parseConfig(String configJson) throws Exception {
        if (configJson == null || configJson.isBlank()) {
            return new SolveRequestConfigDto();
        }
        return objectMapper.readValue(configJson, SolveRequestConfigDto.class);
    }

    private LocalDateTime parseStartTime(String startTime) {
        if (startTime == null || startTime.isBlank()) {
            return LocalDateTime.now();
        }
        try {
            return LocalDateTime.parse(startTime);
        } catch (Exception e) {
            log.warn("无法解析startTime: {}，使用当前时间", startTime);
            return LocalDateTime.now();
        }
    }
}
