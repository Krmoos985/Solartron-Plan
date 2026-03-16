package com.changyang.scheduling.service;

import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverStatus;
import com.changyang.scheduling.domain.MotherRollSchedule;
import com.changyang.scheduling.domain.ProductionLine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingService {

    private final SolverManager<MotherRollSchedule, String> solverManager;
    private final ChangeoverService changeoverService;

    private final ConcurrentMap<String, MotherRollSchedule> jobResults = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Throwable> jobErrors = new ConcurrentHashMap<>();

    /**
     * 预处理：初始化换型缓存。
     * 当前 Excel 模式下先保持原始订单粒度，不启用按天拆分，
     * 优先验证“分线 + 换型时间最小化”主链路。
     */
    private MotherRollSchedule preprocess(MotherRollSchedule problem) {
        if (problem.getChangeoverEntries() != null && !problem.getChangeoverEntries().isEmpty()) {
            changeoverService.initCache(problem.getChangeoverEntries());
        }
        return problem;
    }

    /**
     * 同步求解（阻塞直到求解结束）
     */
    public MotherRollSchedule solve(MotherRollSchedule problem) {
        log.info("开始同步排程...");
        MotherRollSchedule processedProblem = preprocess(problem);

        String jobId = UUID.randomUUID().toString();
        SolverJob<MotherRollSchedule, String> solverJob = solverManager.solveAndListen(jobId,
                id -> processedProblem,
                bestSolution -> { /* ignore intermediate */ });

        try {
            MotherRollSchedule solution = solverJob.getFinalBestSolution();
            validateSolution(solution);
            return solution;
        } catch (InterruptedException | ExecutionException e) {
            log.error("排程失败", e);
            throw new RuntimeException("排程求解执行失败", e);
        }
    }

    /**
     * 异步求解：提交任务并返回 jobId
     */
    public String solveAsync(MotherRollSchedule problem) {
        String jobId = UUID.randomUUID().toString();
        log.info("提交异步排程任务，jobId: {}", jobId);

        MotherRollSchedule processedProblem = preprocess(problem);

        solverManager.solveBuilder()
                .withProblemId(jobId)
                .withProblemFinder(id -> processedProblem)
                .withBestSolutionConsumer(bestSolution -> {
                    log.info("任务 {} 找到更好的解: {}", jobId, bestSolution.getScore());
                    jobResults.put(jobId, bestSolution);
                })
                .withFinalBestSolutionConsumer(finalBestSolution -> {
                    log.info("异步排程任务 {} 完成，最终 Score: {}", jobId, finalBestSolution.getScore());
                    validateSolution(finalBestSolution);
                    jobResults.put(jobId, finalBestSolution);
                })
                .withExceptionHandler((id, throwable) -> {
                    log.error("异步排程任务 {} 执行异常", id, throwable);
                    jobErrors.put(id, throwable);
                })
                .run();

        return jobId;
    }

    /**
     * 验证求解结果的完整性
     */
    public void validateSolution(MotherRollSchedule solution) {
        int totalOrders = solution.getOrders().size();
        int assignedCount = 0;

        for (ProductionLine line : solution.getProductionLines()) {
            assignedCount += line.getOrders().size();
        }

        int unassignedCount = totalOrders - assignedCount;
        boolean fullyInitialized = (unassignedCount == 0);
        boolean feasible = solution.getScore() != null && solution.getScore().isFeasible();

        log.info("=== 排程结果校验 ===");
        log.info("总任务数: {}, 已分配: {}, 未分配: {}", totalOrders, assignedCount, unassignedCount);
        log.info("评分: {}", solution.getScore());
        log.info("完全初始化: {}", fullyInitialized);
        log.info("硬约束可行: {}", feasible);

        if (!fullyInitialized) {
            log.warn("⚠️ 排程结果不完整！{}/{}条任务未被分配到产线。" +
                            "可能原因：求解时间不足、硬约束过严。请增加 timefold.solver.termination.spent-limit 配置。",
                    unassignedCount, totalOrders);
        }

        if (!feasible) {
            log.warn("⚠️ 排程结果仍存在硬约束冲突，当前结果只可用于调试，不应直接用于业务下发。");
        }

        // 打印产线分配统计
        for (ProductionLine line : solution.getProductionLines()) {
            log.info("  {} ({}): {}条任务", line.getName(), line.getId(), line.getOrders().size());
        }
    }

    /**
     * 获取未分配任务数量
     */
    public static int getUnassignedCount(MotherRollSchedule solution) {
        int totalOrders = solution.getOrders().size();
        int assignedCount = 0;
        for (ProductionLine line : solution.getProductionLines()) {
            assignedCount += line.getOrders().size();
        }
        return totalOrders - assignedCount;
    }

    /**
     * 查询 SolverJob 状态
     */
    public SolverStatus getStatus(String jobId) {
        return solverManager.getSolverStatus(jobId);
    }

    /**
     * 获取异步任务结果
     */
    public MotherRollSchedule getResult(String jobId) {
        if (jobErrors.containsKey(jobId)) {
            throw new RuntimeException("任务执行中出现异常", jobErrors.get(jobId));
        }
        return jobResults.get(jobId);
    }

    /**
     * 提前终止求解
     */
    public void stopSolver(String jobId) {
        solverManager.terminateEarly(jobId);
    }
}
