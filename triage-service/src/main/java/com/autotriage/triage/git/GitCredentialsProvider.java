package com.autotriage.triage.git;

import org.eclipse.microprofile.config.ConfigProvider;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.UsernamePasswordCredentialsProvider;

import java.util.Optional;

@jakarta.enterprise.context.ApplicationScoped
public class GitCredentialsProvider {

    public CredentialsProvider build() {
        Optional<String> token = ConfigProvider.getConfig().getOptionalValue("git.clone.token", String.class);
        if (token.isEmpty()) {
            return null;
        }
        String username = ConfigProvider.getConfig()
                .getOptionalValue("git.clone.username", String.class)
                .orElse("x-access-token");
        return new UsernamePasswordCredentialsProvider(username, token.get());
    }
}
