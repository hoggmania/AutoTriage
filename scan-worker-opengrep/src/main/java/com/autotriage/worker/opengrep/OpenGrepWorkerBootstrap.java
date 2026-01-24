package com.autotriage.worker.opengrep;

import io.quarkus.runtime.StartupEvent;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.event.Observes;
import org.jboss.logging.Logger;

@ApplicationScoped
public class OpenGrepWorkerBootstrap {

    private static final Logger log = Logger.getLogger(OpenGrepWorkerBootstrap.class);

    void onStart(@Observes StartupEvent event) {
        log.infov("OpenGrep worker starting on queue scan-opengrep");
    }
}
