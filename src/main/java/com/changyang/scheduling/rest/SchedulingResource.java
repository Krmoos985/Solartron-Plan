package com.changyang.scheduling.rest;

import ai.timefold.solver.core.api.solver.SolverJob;
import ai.timefold.solver.core.api.solver.SolverManager;
import ai.timefold.solver.core.api.solver.SolverStatus;
import com.changyang.scheduling.domain.MotherRollSchedule;
import jakarta.inject.Inject;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutionException;

@Path("/api/scheduling")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class SchedulingResource {

    @Inject
    SolverManager<MotherRollSchedule, String> solverManager;

    // 存储异步求解任务的最终结果
    private final Map<String, SolverJob<MotherRollSchedule, String>> solverJobs = new ConcurrentHashMap<>();
    private final Map<String, MotherRollSchedule> solutions = new ConcurrentHashMap<>();

    /**
     * 同步求解：阻塞等待求解完成后返回结果
     */
    @POST
    @Path("/solve")
    public MotherRollSchedule solve(MotherRollSchedule problem) {
        String jobId = UUID.randomUUID().toString();
        SolverJob<MotherRollSchedule, String> solverJob = solverManager.solve(jobId, problem);
        try {
            return solverJob.getFinalBestSolution();
        } catch (InterruptedException | ExecutionException e) {
            throw new WebApplicationException("求解失败: " + e.getMessage(),
                    Response.Status.INTERNAL_SERVER_ERROR);
        }
    }

    /**
     * 异步求解：立即返回 jobId，后台持续求解
     */
    @POST
    @Path("/solve-async")
    public Map<String, String> solveAsync(MotherRollSchedule problem) {
        String jobId = UUID.randomUUID().toString();
        SolverJob<MotherRollSchedule, String> solverJob = solverManager.solve(
                jobId, problem, solution -> solutions.put(jobId, solution));
        solverJobs.put(jobId, solverJob);
        return Map.of("jobId", jobId);
    }

    /**
     * 查询异步求解状态和结果
     */
    @GET
    @Path("/status/{jobId}")
    public Response getStatus(@PathParam("jobId") String jobId) {
        SolverStatus status = solverManager.getSolverStatus(jobId);
        MotherRollSchedule solution = solutions.get(jobId);

        if (status == SolverStatus.NOT_SOLVING && solution == null) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(Map.of("error", "未找到该求解任务: " + jobId))
                    .build();
        }

        return Response.ok(Map.of(
                "jobId", jobId,
                "status", status.name(),
                "solution", solution != null ? solution : "求解中...")).build();
    }

    /**
     * 终止指定的异步求解任务
     */
    @DELETE
    @Path("/stop/{jobId}")
    public Response stop(@PathParam("jobId") String jobId) {
        solverManager.terminateEarly(jobId);
        return Response.ok(Map.of(
                "jobId", jobId,
                "message", "已发送终止请求")).build();
    }
}
