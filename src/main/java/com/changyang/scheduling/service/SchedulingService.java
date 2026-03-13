package com.changyang.scheduling.service;

import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverStatus;
import com.changyang.scheduling.domain.MotherRollOrder;
import com.changyang.scheduling.domain.MotherRollSchedule;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingService {

    private final SolverManager<MotherRollSchedule, String> solverManager;
    private final TaskSplitter taskSplitter;
    private final ChangeoverService changeoverService;

    private final ConcurrentMap<String, MotherRollSchedule> jobResults = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Throwable> jobErrors = new ConcurrentHashMap<>();

    /**
     * 预处理：拆分任务 + 初始化换型缓存
     */
    private MotherRollSchedule preprocess(MotherRollSchedule problem) {
        // 初始化换型缓存（如果 ExcelDataLoader 已经初始化过，这里会刷新）
        if (problem.getChangeoverEntries() != null && !problem.getChangeoverEntries().isEmpty()) {
            changeoverService.initCache(problem.getChangeoverEntries());
        }

        if (problem.getOrders() != null) {
            List<MotherRollOrder> splitOrders = taskSplitter.splitTasks(problem.getOrders());
            problem.setOrders(splitOrders);
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
            return solverJob.getFinalBestSolution();
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
