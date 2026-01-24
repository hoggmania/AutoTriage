package com.autotriage.worker.filter;

import com.autotriage.worker.filter.activity.FilterScanActivities;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContext;
import io.grpc.netty.shaded.io.netty.handler.ssl.SslContextBuilder;
import io.quarkus.runtime.StartupEvent;
import io.temporal.client.WorkflowClient;
import io.temporal.serviceclient.WorkflowServiceStubs;
import io.temporal.serviceclient.WorkflowServiceStubsOptions;
import io.temporal.worker.Worker;
import io.temporal.worker.WorkerFactory;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.io.File;
import java.util.Optional;

@ApplicationScoped
public class FilterWorkerBootstrap {

    private static final Logger log = Logger.getLogger(FilterWorkerBootstrap.class);

    @ConfigProperty(name = "temporal.target", defaultValue = "localhost:7233")
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
    private WorkerFactory workerFactory;

    void onStart(@Observes StartupEvent event) {
        log.infov("Filter worker starting on queue scan-filter");
        serviceStubs = WorkflowServiceStubs.newServiceStubs(buildServiceOptions());
        WorkflowClient client = WorkflowClient.newInstance(serviceStubs, io.temporal.client.WorkflowClientOptions.newBuilder()
                .setNamespace(namespace)
                .build());
        workerFactory = WorkerFactory.newInstance(client);
        Worker worker = workerFactory.newWorker("scan-filter");
        worker.registerActivitiesImplementations(new FilterScanActivities());
        workerFactory.start();
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
        if (workerFactory != null) {
            workerFactory.shutdown();
        }
        if (serviceStubs != null) {
            serviceStubs.shutdown();
        }
    }
}
