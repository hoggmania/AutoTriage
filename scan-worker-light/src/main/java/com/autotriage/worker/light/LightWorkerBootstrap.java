package com.autotriage.worker.light;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

@ApplicationScoped
public class LightWorkerBootstrap {

    private static final Logger log = Logger.getLogger(LightWorkerBootstrap.class);

    void onStart(@Observes StartupEvent event) {
        log.infov("Light worker starting on queue scan-light");
    }
}
