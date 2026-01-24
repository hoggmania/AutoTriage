package com.autotriage.api;

import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.ScanState;
import com.autotriage.common.model.ScanStatus;
import com.autotriage.common.workflow.OpenGrepPRScanWorkflow;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowExecutionAlreadyStarted;
import io.temporal.client.WorkflowNotFoundException;
import io.temporal.client.WorkflowOptions;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

import jakarta.inject.Inject;

@Path("/scans")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ScanResource {

    private static final Logger log = Logger.getLogger(ScanResource.class);
    private static final String TASK_QUEUE = "scan-workflows";

    @Inject
    WorkflowClient workflowClient;

    @POST
    public Response createScan(ScanRequest request) {
        if (request == null || isBlank(request.getRunId()) || isBlank(request.getRepository()) || isBlank(request.getCommitSha())) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ScanStatus("unknown", ScanState.FAILED, "runId, repository, and commitSha are required"))
                    .build();
        }
        log.infov("Received scan request {0} for repo {1}", request.getRunId(), request.getRepository());
        String workflowId = "opengrep-scan-" + request.getRunId();
        WorkflowOptions options = WorkflowOptions.newBuilder()
                .setWorkflowId(workflowId)
                .setTaskQueue(TASK_QUEUE)
                .build();
        OpenGrepPRScanWorkflow workflow = workflowClient.newWorkflowStub(OpenGrepPRScanWorkflow.class, options);
        try {
            WorkflowClient.start(workflow::startScan, request);
        } catch (WorkflowExecutionAlreadyStarted e) {
            return Response.status(Response.Status.CONFLICT)
                    .entity(new ScanStatus(request.getRunId(), ScanState.FAILED, "Workflow already started"))
                    .build();
        }
        return Response.accepted(ScanStatus.running(request.getRunId())).build();
    }

    @GET
    @Path("{runId}")
    public Response getStatus(@PathParam("runId") String runId) {
        log.infov("Querying status for {0}", runId);
        if (isBlank(runId)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ScanStatus("unknown", ScanState.FAILED, "runId is required"))
                    .build();
        }
        String workflowId = "opengrep-scan-" + runId;
        OpenGrepPRScanWorkflow workflow = workflowClient.newWorkflowStub(OpenGrepPRScanWorkflow.class, workflowId);
        try {
            ScanStatus status = workflow.queryStatus(runId);
            return Response.ok(status).build();
        } catch (WorkflowNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ScanStatus(runId, ScanState.FAILED, "Workflow not found"))
                    .build();
        }
    }

    @POST
    @Path("{runId}/cancel")
    public Response cancel(@PathParam("runId") String runId) {
        log.infov("Cancel requested for {0}", runId);
        if (isBlank(runId)) {
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(new ScanStatus("unknown", ScanState.FAILED, "runId is required"))
                    .build();
        }
        String workflowId = "opengrep-scan-" + runId;
        OpenGrepPRScanWorkflow workflow = workflowClient.newWorkflowStub(OpenGrepPRScanWorkflow.class, workflowId);
        try {
            workflow.cancelScan(runId);
        } catch (WorkflowNotFoundException e) {
            return Response.status(Response.Status.NOT_FOUND)
                    .entity(new ScanStatus(runId, ScanState.FAILED, "Workflow not found"))
                    .build();
        }
        return Response.ok(new ScanStatus(runId, ScanState.CANCELED, "Cancel requested")).build();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
