package com.autotriage.api;

import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.temporal.client.WorkflowClient;
import io.temporal.client.WorkflowClientOptions;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import org.eclipse.microprofile.config.inject.ConfigProperty;

import java.io.File;
import java.util.Optional;

@ApplicationScoped
public class TemporalClientProducer {

    @ConfigProperty(name = "temporal.target")
    String target;

    @ConfigProperty(name = "temporal.namespace", defaultValue = "scan-platform")
    String namespace;

    @ConfigProperty(name = "temporal.tls.enabled", defaultValue = "false")
    boolean tlsEnabled;

    @ConfigProperty(name = "temporal.tls.client-cert-path")
    Optional<String> clientCertPath;

    @ConfigProperty(name = "temporal.tls.client-key-path")
    Optional<String> clientKeyPath;

    @ConfigProperty(name = "temporal.tls.server-ca-path")
    Optional<String> serverCaPath;

    private WorkflowServiceStubs serviceStubs;

    @Produces
    @ApplicationScoped
    public WorkflowClient workflowClient() {
        if (serviceStubs == null) {
            serviceStubs = WorkflowServiceStubs.newServiceStubs(buildServiceOptions());
        }
        WorkflowClientOptions clientOptions = WorkflowClientOptions.newBuilder()
                .setNamespace(namespace)
                .build();
        return WorkflowClient.newInstance(serviceStubs, clientOptions);
    }

    private WorkflowServiceStubsOptions buildServiceOptions() {
        WorkflowServiceStubsOptions.Builder builder = WorkflowServiceStubsOptions.newBuilder()
                .setTarget(target);
        if (tlsEnabled) {
            builder.setSslContext(buildSslContext());
        }
        return builder.build();
    }

    private SslContext buildSslContext() {
        String certPath = clientCertPath.orElseThrow(() -> new IllegalStateException("Client cert path required when TLS is enabled"));
        String keyPath = clientKeyPath.orElseThrow(() -> new IllegalStateException("Client key path required when TLS is enabled"));
        String caPath = serverCaPath.orElseThrow(() -> new IllegalStateException("Server CA path required when TLS is enabled"));
        try {
            return SslContextBuilder.forClient()
                    .keyManager(new File(certPath), new File(keyPath))
                    .trustManager(new File(caPath))
                    .build();
        } catch (Exception e) {
            throw new IllegalStateException("Failed to build Temporal TLS context", e);
        }
    }

    @PreDestroy
    void shutdown() {
        if (serviceStubs != null) {
            serviceStubs.shutdown();
        }
    }
}
