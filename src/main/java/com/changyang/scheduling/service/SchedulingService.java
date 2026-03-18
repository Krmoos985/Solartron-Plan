package com.changyang.scheduling.service;

import ai.timefold.solver.core.api.solver.SolverConfigOverride;
import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverJobBuilder;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverStatus;
import ai.timefold.solver.core.config.solver.termination.TerminationConfig;
import com.changyang.scheduling.domain.MotherRollSchedule;
import com.changyang.scheduling.domain.ProductionLine;
import com.changyang.scheduling.rest.dto.SolveRequestConfigDto;
import com.changyang.scheduling.rest.dto.TerminationSettingsDto;
import com.changyang.scheduling.solver.SchedulingConstraintConfiguration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutionException;

@Slf4j
@Service
@RequiredArgsConstructor
public class SchedulingService {

    private static final long PROGRESS_LOG_INTERVAL_MILLIS = 2_000L;

    private final SolverManager<MotherRollSchedule, String> solverManager;
    private final ChangeoverService changeoverService;

    private final ConcurrentMap<String, MotherRollSchedule> jobResults = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Throwable> jobErrors = new ConcurrentHashMap<>();

    /**
     * Current Excel mode keeps original order granularity.
     * Daily splitting stays disabled until the split-specific workflow is re-enabled.
     */
    private MotherRollSchedule preprocess(MotherRollSchedule problem, SolveRequestConfigDto config) {
        if (problem.getChangeoverEntries() != null && !problem.getChangeoverEntries().isEmpty()) {
            changeoverService.initCache(problem.getChangeoverEntries());
        }
        problem.setConstraintConfiguration(SchedulingConstraintConfiguration.fromSelection(
                config == null ? null : config.getConstraints()
        ));
        return problem;
    }

    public MotherRollSchedule solve(MotherRollSchedule problem) {
        return solve(problem, null);
    }

    public MotherRollSchedule solve(MotherRollSchedule problem, SolveRequestConfigDto config) {
        log.info("Start synchronous solving.");
        MotherRollSchedule processedProblem = preprocess(problem, config);
        logEffectiveConfiguration("sync", processedProblem.getConstraintConfiguration(), config == null ? null : config.getTermination());

        String jobId = UUID.randomUUID().toString();
        SolverProgressTracker progressTracker = new SolverProgressTracker(jobId);

        SolverJobBuilder<MotherRollSchedule, String> builder = solverManager.solveBuilder()
                .withProblemId(jobId)
                .withProblemFinder(id -> processedProblem)
                .withBestSolutionConsumer(progressTracker::logProgress);

        SolverConfigOverride<MotherRollSchedule> configOverride =
                buildConfigOverride(config == null ? null : config.getTermination());
        if (configOverride != null) {
            builder.withConfigOverride(configOverride);
        }

        SolverJob<MotherRollSchedule, String> solverJob = builder.run();

        try {
            MotherRollSchedule solution = solverJob.getFinalBestSolution();
            validateSolution(solution);
            return solution;
        } catch (InterruptedException | ExecutionException e) {
            log.error("Synchronous solving failed.", e);
            throw new RuntimeException("Scheduling solve failed.", e);
        }
    }

    public String solveAsync(MotherRollSchedule problem) {
        return solveAsync(problem, null);
    }

    public String solveAsync(MotherRollSchedule problem, SolveRequestConfigDto config) {
        String jobId = UUID.randomUUID().toString();
        log.info("Submitted async solving job, jobId={}", jobId);

        MotherRollSchedule processedProblem = preprocess(problem, config);
        logEffectiveConfiguration("async", processedProblem.getConstraintConfiguration(), config == null ? null : config.getTermination());
        SolverProgressTracker progressTracker = new SolverProgressTracker(jobId);

        SolverJobBuilder<MotherRollSchedule, String> builder = solverManager.solveBuilder()
                .withProblemId(jobId)
                .withProblemFinder(id -> processedProblem)
                .withBestSolutionConsumer(bestSolution -> {
                    progressTracker.logProgress(bestSolution);
                    log.info("Job {} found a better solution: {}", jobId, bestSolution.getScore());
                    jobResults.put(jobId, bestSolution);
                })
                .withFinalBestSolutionConsumer(finalBestSolution -> {
                    log.info("Async solving job {} finished, final score={}", jobId, finalBestSolution.getScore());
                    validateSolution(finalBestSolution);
                    jobResults.put(jobId, finalBestSolution);
                })
                .withExceptionHandler((id, throwable) -> {
                    log.error("Async solving job {} failed.", id, throwable);
                    jobErrors.put(id, throwable);
                });

        SolverConfigOverride<MotherRollSchedule> configOverride =
                buildConfigOverride(config == null ? null : config.getTermination());
        if (configOverride != null) {
            builder.withConfigOverride(configOverride);
        }

        builder.run();
        return jobId;
    }

    private SolverConfigOverride<MotherRollSchedule> buildConfigOverride(TerminationSettingsDto termination) {
        if (termination == null) {
            return null;
        }

        TerminationConfig terminationConfig = new TerminationConfig();
        if (termination.getTimeLimitSeconds() != null && termination.getTimeLimitSeconds() > 0) {
            terminationConfig.setSpentLimit(Duration.ofSeconds(termination.getTimeLimitSeconds()));
        }
        if (termination.getUnimprovedTimeLimitSeconds() != null && termination.getUnimprovedTimeLimitSeconds() > 0) {
            terminationConfig.setUnimprovedSpentLimit(Duration.ofSeconds(termination.getUnimprovedTimeLimitSeconds()));
        }
        if (termination.getStepCountLimit() != null && termination.getStepCountLimit() > 0) {
            terminationConfig.setStepCountLimit(termination.getStepCountLimit());
        }
        if (Boolean.TRUE.equals(termination.getStopOnFeasible())) {
            terminationConfig.setBestScoreFeasible(Boolean.TRUE);
        }

        if (!terminationConfig.isConfigured()) {
            return null;
        }
        return new SolverConfigOverride<MotherRollSchedule>().withTerminationConfig(terminationConfig);
    }

    private void logEffectiveConfiguration(
            String solveMode,
            SchedulingConstraintConfiguration constraintConfiguration,
            TerminationSettingsDto termination) {
        SchedulingConstraintConfiguration effectiveConfiguration =
                constraintConfiguration == null ? SchedulingConstraintConfiguration.defaults() : constraintConfiguration;

        log.info("=== Effective constraint configuration [{}] ===", solveMode);
        log.info(
                "Enabled constraints ({}): {}",
                effectiveConfiguration.describeEnabledConstraints().size(),
                String.join(", ", effectiveConfiguration.describeEnabledConstraints())
        );
        log.info(
                "Disabled implemented constraints ({}): {}",
                effectiveConfiguration.describeDisabledConstraints().size(),
                String.join(", ", effectiveConfiguration.describeDisabledConstraints())
        );
        log.info(
                "Split-locked constraints: {}",
                String.join(", ", SchedulingConstraintConfiguration.splitLockedConstraintCodes())
        );

        if (termination == null) {
            log.info("Termination override: none, using application defaults.");
            return;
        }

        log.info(
                "Termination settings: timeLimit={}s, unimprovedLimit={}s, stepLimit={}, stopOnFeasible={}",
                termination.getTimeLimitSeconds(),
                termination.getUnimprovedTimeLimitSeconds(),
                termination.getStepCountLimit(),
                termination.getStopOnFeasible()
        );
    }

    public void validateSolution(MotherRollSchedule solution) {
        int totalOrders = solution.getOrders().size();
        int assignedCount = getAssignedCount(solution);
        int unassignedCount = totalOrders - assignedCount;
        boolean fullyInitialized = (unassignedCount == 0);
        boolean feasible = solution.getScore() != null && solution.getScore().isFeasible();

        log.info("=== Schedule validation ===");
        log.info("Total orders: {}, assigned: {}, unassigned: {}", totalOrders, assignedCount, unassignedCount);
        log.info("Score: {}", solution.getScore());
        log.info("Fully initialized: {}", fullyInitialized);
        log.info("Hard-score feasible: {}", feasible);

        if (!fullyInitialized) {
            log.warn(
                    "The schedule is incomplete. {}/{} orders are still unassigned. " +
                            "Possible causes: too little solving time or overly strict hard constraints.",
                    unassignedCount,
                    totalOrders
            );
        }

        if (!feasible) {
            log.warn("The resulting schedule still violates hard constraints and should only be used for debugging.");
        }

        for (ProductionLine line : solution.getProductionLines()) {
            log.info("  {} ({}): {} orders", line.getName(), line.getId(), line.getOrders().size());
        }
    }

    public static int getUnassignedCount(MotherRollSchedule solution) {
        int totalOrders = solution.getOrders().size();
        int assignedCount = getAssignedCount(solution);
        return totalOrders - assignedCount;
    }

    private static int getAssignedCount(MotherRollSchedule solution) {
        int assignedCount = 0;
        for (ProductionLine line : solution.getProductionLines()) {
            assignedCount += line.getOrders().size();
        }
        return assignedCount;
    }

    public SolverStatus getStatus(String jobId) {
        return solverManager.getSolverStatus(jobId);
    }

    public MotherRollSchedule getResult(String jobId) {
        if (jobErrors.containsKey(jobId)) {
            throw new RuntimeException("Job execution failed.", jobErrors.get(jobId));
        }
        return jobResults.get(jobId);
    }

    public void stopSolver(String jobId) {
        solverManager.terminateEarly(jobId);
    }

    private static final class SolverProgressTracker {

        private final String jobId;
        private final long startedAtMillis;
        private long lastLoggedAtMillis = Long.MIN_VALUE;

        private SolverProgressTracker(String jobId) {
            this.jobId = jobId;
            this.startedAtMillis = System.currentTimeMillis();
        }

        private void logProgress(MotherRollSchedule bestSolution) {
            long now = System.currentTimeMillis();
            if (lastLoggedAtMillis != Long.MIN_VALUE
                    && now - lastLoggedAtMillis < PROGRESS_LOG_INTERVAL_MILLIS) {
                return;
            }

            lastLoggedAtMillis = now;
            long elapsedSeconds = Math.max(0L, (now - startedAtMillis) / 1000L);
            int totalOrders = bestSolution.getOrders() == null ? 0 : bestSolution.getOrders().size();
            int assignedCount = getAssignedCount(bestSolution);
            int unassignedCount = Math.max(0, totalOrders - assignedCount);

            log.info(
                    "Solve progress jobId={} elapsed={}s score={} feasible={} assigned={}/{} unassigned={}",
                    jobId,
                    elapsedSeconds,
                    bestSolution.getScore(),
                    bestSolution.getScore() != null && bestSolution.getScore().isFeasible(),
                    assignedCount,
                    totalOrders,
                    unassignedCount
            );
        }
    }
}
