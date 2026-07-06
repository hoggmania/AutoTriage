package com.autotriage.triage.api;

import com.autotriage.common.model.TriageCandidateRequest;

import com.autotriage.triage.model.AuditEventEntity;
import com.autotriage.triage.model.FindingEntity;
import com.autotriage.triage.model.FindingStatus;
import com.autotriage.triage.service.TriageActionService;
import com.autotriage.triage.service.TriageCandidateService;
import io.quarkus.security.identity.SecurityIdentity;
import jakarta.annotation.security.PermitAll;
import jakarta.annotation.security.RolesAllowed;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import jakarta.ws.rs.Consumes;
import jakarta.ws.rs.GET;
import jakarta.ws.rs.HeaderParam;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.PathParam;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.QueryParam;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;
import org.eclipse.microprofile.config.ConfigProvider;

import java.net.URI;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Path("/triage")
@Produces(MediaType.APPLICATION_JSON)
public class TriageResource {

    private static final String TRIAGE_ROLE = "security:vuln_assessor:triager";

    @Inject
    TriageCandidateService candidateService;

    @Inject
    TriageActionService actionService;

    @Inject
    SecurityIdentity identity;

    @POST
    @Path("/candidates")
    @PermitAll
    @Consumes(MediaType.APPLICATION_JSON)
    public Response ingestCandidate(@HeaderParam("Authorization") String authorization,
                                    TriageCandidateRequest request) {
        if (!isIngestAuthorized(authorization)) {
            return Response.status(Response.Status.UNAUTHORIZED).build();
        }
        if (request == null || !isRepositoryAllowed(request.getRepository())) {
            return Response.status(Response.Status.BAD_REQUEST).build();
        }
        return Response.ok(candidateService.ingest(request)).build();
    }

    @GET
    @Path("/findings")
    @RolesAllowed(TRIAGE_ROLE)
    public List<FindingView> listFindings(@QueryParam("repository") String repository,
                                          @QueryParam("status") FindingStatus status) {
        List<FindingEntity> findings;
        if (repository != null && status != null) {
            findings = FindingEntity.list("repository = ?1 and status = ?2 order by createdAt desc", repository, status);
        } else if (repository != null) {
            findings = FindingEntity.list("repository = ?1 order by createdAt desc", repository);
        } else if (status != null) {
            findings = FindingEntity.list("status = ?1 order by createdAt desc", status);
        } else {
            findings = FindingEntity.list("order by createdAt desc");
        }
        return findings.stream().map(FindingView::new).collect(Collectors.toList());
    }

    @POST
    @Path("/findings/{id}/claim")
    @RolesAllowed(TRIAGE_ROLE)
    @Transactional
    public Response claimFinding(@PathParam("id") String id) {
        FindingEntity finding = actionService.claim(parseId(id), resolveActor());
        return finding == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(new FindingView(finding)).build();
    }

    @POST
    @Path("/findings/{id}/approve")
    @RolesAllowed(TRIAGE_ROLE)
    @Transactional
    public Response approveFinding(@PathParam("id") String id) {
        FindingEntity finding = actionService.approve(parseId(id), resolveActor());
        return finding == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(new FindingView(finding)).build();
    }

    @POST
    @Path("/findings/{id}/deny")
    @RolesAllowed(TRIAGE_ROLE)
    @Transactional
    public Response denyFinding(@PathParam("id") String id) {
        FindingEntity finding = actionService.deny(parseId(id), resolveActor());
        return finding == null ? Response.status(Response.Status.NOT_FOUND).build() : Response.ok(new FindingView(finding)).build();
    }

    @GET
    @Path("/audit")
    @RolesAllowed(TRIAGE_ROLE)
    public List<AuditEventView> listAudit(@QueryParam("repository") String repository) {
        List<AuditEventEntity> events;
        if (repository != null) {
            events = AuditEventEntity.list("repository = ?1 order by createdAt desc", repository);
        } else {
            events = AuditEventEntity.list("order by createdAt desc");
        }
        return events.stream().map(AuditEventView::new).collect(Collectors.toList());
    }

    private UUID parseId(String id) {
        return UUID.fromString(id);
    }

    private String resolveActor() {
        return identity == null || identity.getPrincipal() == null
                ? "unknown"
                : identity.getPrincipal().getName();
    }

    private boolean isIngestAuthorized(String authorization) {
        Optional<String> token = ConfigProvider.getConfig()
                .getOptionalValue("triage.ingest.token", String.class)
                .filter(value -> !value.isBlank());
        boolean allowUnauthenticated = ConfigProvider.getConfig()
                .getOptionalValue("triage.ingest.allow-unauthenticated", Boolean.class)
                .orElse(false);
        if (token.isEmpty()) {
            return allowUnauthenticated;
        }
        return ("Bearer " + token.get()).equals(authorization);
    }

    private boolean isRepositoryAllowed(String repository) {
        if (repository == null || repository.isBlank()) {
            return false;
        }
        String allowedHosts = ConfigProvider.getConfig()
                .getOptionalValue("triage.repository.allowed-hosts", String.class)
                .orElse("github.com");
        try {
            URI uri = URI.create(repository);
            String host = uri.getHost();
            if (host == null || !("https".equalsIgnoreCase(uri.getScheme()) || "ssh".equalsIgnoreCase(uri.getScheme()))) {
                return false;
            }
            for (String allowedHost : allowedHosts.split(",")) {
                if (host.equalsIgnoreCase(allowedHost.trim())) {
                    return true;
                }
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }
}
