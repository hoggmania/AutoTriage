package com.autotriage.suppressionmock;

import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Path("/ingest")
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class IngestResource {

    @POST
    public Response ingest(Map<String, Object> payload) {
        String runId = payload == null ? "unknown" : String.valueOf(payload.getOrDefault("runId", "unknown"));
        String token = UUID.randomUUID().toString();
        Map<String, Object> response = new HashMap<>();
        response.put("runId", runId);
        response.put("reportUrl", "https://mock.local/report/" + token);
        response.put("dashboardUrl", "https://mock.local/dashboard/" + token);
        response.put("prCommentUrl", "https://mock.local/pr/" + token);
        return Response.ok(response).build();
    }
}
