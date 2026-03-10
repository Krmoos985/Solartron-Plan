package com.changyang.scheduling.rest;

import ai.timefold.solver.core.api.solver.SolverStatus;
import com.changyang.scheduling.domain.MergedTask;
import com.changyang.scheduling.domain.MotherRollSchedule;
import com.changyang.scheduling.domain.ProductionLine;
import com.changyang.scheduling.service.SchedulingService;
import com.changyang.scheduling.service.TaskMerger;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/scheduling")
@RequiredArgsConstructor
public class SchedulingController {

    private final SchedulingService schedulingService;
    private final TaskMerger taskMerger;

    /**
     * 同步求解排程
     */
    @PostMapping("/solve")
    public ResponseEntity<Map<String, Object>> solve(@RequestBody MotherRollSchedule problem) {
        MotherRollSchedule solution = schedulingService.solve(problem);
        return ResponseEntity.ok(buildResponse(solution));
    }

    /**
     * 异步提交求解任务
     */
    @PostMapping("/solve-async")
    public ResponseEntity<Map<String, String>> solveAsync(@RequestBody MotherRollSchedule problem) {
        String jobId = schedulingService.solveAsync(problem);
        return ResponseEntity.accepted().body(Map.of("jobId", jobId, "message", "排程任务已提交"));
    }

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
                // 如果求解已结束，返回完整合并后的结果
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
     * 构建响应，应用 TaskMerger 进行后处理
     */
    private Map<String, Object> buildResponse(MotherRollSchedule schedule) {
        Map<String, Object> responseMap = new HashMap<>();
        if (schedule.getScore() != null) {
            responseMap.put("score", schedule.getScore().toString());
        }
        
        Map<String, List<MergedTask>> lineTasks = new HashMap<>();
        if (schedule.getProductionLines() != null) {
            for (ProductionLine line : schedule.getProductionLines()) {
                lineTasks.put(line.getId(), taskMerger.mergeTasks(line));
            }
        }
        responseMap.put("lineTasks", lineTasks);
        
        return responseMap;
    }
}
