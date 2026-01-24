package com.autotriage.api;

import com.autotriage.common.model.ScanRequest;
import com.autotriage.common.model.ScanState;
import com.autotriage.common.model.ScanStatus;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.jboss.logging.Logger;

@Path("/scans")
@Produces(MediaType.APPLICATION_JSON)
@Consumes(MediaType.APPLICATION_JSON)
public class ScanResource {

    private static final Logger log = Logger.getLogger(ScanResource.class);

    @POST
    public Response createScan(ScanRequest request) {
        log.infov("Received scan request {0} for repo {1}", request.getRunId(), request.getRepository());
        ScanStatus status = ScanStatus.running(request.getRunId());
        return Response.accepted(status).build();
    }

    @GET
    @Path("{runId}")
    public ScanStatus getStatus(@PathParam("runId") String runId) {
        log.infov("Querying status for {0}", runId);
        return new ScanStatus(runId, ScanState.RUNNING, "Status query is not wired to Temporal yet");
    }

    @POST
    @Path("{runId}/cancel")
    public Response cancel(@PathParam("runId") String runId) {
        log.infov("Cancel requested for {0}", runId);
        return Response.ok().build();
    }
}
