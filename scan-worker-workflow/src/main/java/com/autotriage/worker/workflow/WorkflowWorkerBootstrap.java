package com.autotriage.worker.workflow;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

@ApplicationScoped
public class WorkflowWorkerBootstrap {

    private static final Logger log = Logger.getLogger(WorkflowWorkerBootstrap.class);

    void onStart(@Observes StartupEvent event) {
        log.infov("Workflow worker starting on queue scan-workflows");
    }
}
