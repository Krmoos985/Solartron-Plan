package com.changyang.scheduling.rest;

import ai.timefold.solver.core.api.solver.SolverStatus;
import com.changyang.scheduling.domain.MergedTask;
import com.changyang.scheduling.domain.MotherRollSchedule;
import com.changyang.scheduling.domain.ProductionLine;
import com.changyang.scheduling.service.DemoDataGenerator;
import com.changyang.scheduling.service.ExcelDataLoader;
import com.changyang.scheduling.service.SchedulingService;
import com.changyang.scheduling.service.TaskMerger;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.InputStream;
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

    // ==================== JSON 排程 ====================

    /**
     * 同步求解排程（JSON 输入）
     */
    @PostMapping("/solve")
    public ResponseEntity<Map<String, Object>> solve(@RequestBody MotherRollSchedule problem) {
        MotherRollSchedule solution = schedulingService.solve(problem);
        return ResponseEntity.ok(buildResponse(solution));
    }

    /**
     * 异步提交求解任务（JSON 输入）
     */
    @PostMapping("/solve-async")
    public ResponseEntity<Map<String, String>> solveAsync(@RequestBody MotherRollSchedule problem) {
        String jobId = schedulingService.solveAsync(problem);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId, "message", "排程任务已提交"));
    }

    // ==================== Excel 排程 ====================

    /**
     * 同步排程（Excel 上传）
     *
     * @param file      Excel 文件（.xlsx）
     * @param startTime 排程基准开始时间，默认当前时间
     */
    @PostMapping("/solve-excel")
    public ResponseEntity<Map<String, Object>> solveExcel(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "startTime", required = false) String startTime) {
        try {
            LocalDateTime solveStart = parseStartTime(startTime);
            MotherRollSchedule problem = excelDataLoader.load(file.getInputStream(), solveStart);
            MotherRollSchedule solution = schedulingService.solve(problem);
            return ResponseEntity.ok(buildResponse(solution));
        } catch (Exception e) {
            log.error("Excel排程失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 异步排程（Excel 上传）
     */
    @PostMapping("/solve-excel-async")
    public ResponseEntity<Map<String, String>> solveExcelAsync(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "startTime", required = false) String startTime) {
        try {
            LocalDateTime solveStart = parseStartTime(startTime);
            MotherRollSchedule problem = excelDataLoader.load(file.getInputStream(), solveStart);
            String jobId = schedulingService.solveAsync(problem);
            return ResponseEntity.accepted().body(Map.of("jobId", jobId, "message", "Excel排程任务已提交"));
        } catch (Exception e) {
            log.error("Excel异步排程提交失败", e);
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * 使用内置 Excel 数据快速演示排程
     */
    @GetMapping("/demo-real")
    public ResponseEntity<Map<String, Object>> demoReal(
            @RequestParam(value = "startTime", required = false) String startTime) {
        try {
            LocalDateTime solveStart = parseStartTime(startTime);
            ClassPathResource resource = new ClassPathResource("data/生产订单_排程导入版.xlsx");
            try (InputStream is = resource.getInputStream()) {
                MotherRollSchedule problem = excelDataLoader.load(is, solveStart);
                MotherRollSchedule solution = schedulingService.solve(problem);
                return ResponseEntity.ok(buildResponse(solution));
            }
        } catch (Exception e) {
            log.error("真实数据演示排程失败", e);
            return ResponseEntity.internalServerError().body(Map.of("error", e.getMessage()));
        }
    }

    // ==================== 任务管理 ====================

    /**
     * 查询异步任务状态与结果
     */
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
                response.put("result", buildResponse(partialOrFinalSolution));
            }
        }
        
        return ResponseEntity.ok(response);
    }

    /**
     * 终止求解任务
     */
    @DeleteMapping("/stop/{jobId}")
    public ResponseEntity<Map<String, String>> stopSolver(@PathVariable String jobId) {
        schedulingService.stopSolver(jobId);
        return ResponseEntity.ok(Map.of("message", "任务 " + jobId + " 终止信号已发送"));
    }

    /**
     * 生成演示数据（假数据）
     */
    @GetMapping("/demo")
    public ResponseEntity<MotherRollSchedule> getDemoData() {
        return ResponseEntity.ok(demoDataGenerator.generateDemoData());
    }

    // ==================== 辅助方法 ====================

    private Map<String, Object> buildResponse(MotherRollSchedule schedule) {
        Map<String, Object> responseMap = new HashMap<>();
        if (schedule.getScore() != null) {
            responseMap.put("score", schedule.getScore().toString());
            responseMap.put("isFeasible", schedule.getScore().isFeasible());
        }

        // 完整性校验
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

        // 添加排程统计信息
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
        
        return responseMap;
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
