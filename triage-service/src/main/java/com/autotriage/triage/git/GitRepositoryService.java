package com.autotriage.triage.git;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.jboss.logging.Logger;

import java.nio.file.Files;
import java.nio.file.Path;

@jakarta.enterprise.context.ApplicationScoped
public class GitRepositoryService {

    private static final Logger log = Logger.getLogger(GitRepositoryService.class);

    public Git cloneRepository(String repositoryUrl, CredentialsProvider credentialsProvider, Path directory) throws Exception {
        return Git.cloneRepository()
                .setURI(repositoryUrl)
                .setDirectory(directory.toFile())
                .setCredentialsProvider(credentialsProvider)
                .setDepth(1)
                .call();
    }

    public String resolveCheckedOutBranch(Git git) {
        try {
            String fullBranch = git.getRepository().getFullBranch();
            if (fullBranch != null && fullBranch.startsWith("refs/heads/")) {
                return Repository.shortenRefName(fullBranch);
            }
            return git.getRepository().getBranch();
        } catch (Exception e) {
            log.warnv("Failed to resolve checked out branch: {0}", e.getMessage());
            return "main";
        }
    }

    public void deleteWorkspace(Path root) {
        if (root == null) {
            return;
        }
        try {
            Files.walk(root)
                    .sorted((a, b) -> b.compareTo(a))
                    .forEach(path -> {
                        try {
                            Files.deleteIfExists(path);
                        } catch (Exception ignored) {
                        }
                    });
        } catch (Exception ignored) {
        }
    }
}
