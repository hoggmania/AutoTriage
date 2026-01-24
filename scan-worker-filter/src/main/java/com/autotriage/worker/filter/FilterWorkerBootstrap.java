package com.autotriage.worker.filter;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

@ApplicationScoped
public class FilterWorkerBootstrap {

    private static final Logger log = Logger.getLogger(FilterWorkerBootstrap.class);

    void onStart(@Observes StartupEvent event) {
        log.infov("Filter worker starting on queue scan-filter");
    }
}
