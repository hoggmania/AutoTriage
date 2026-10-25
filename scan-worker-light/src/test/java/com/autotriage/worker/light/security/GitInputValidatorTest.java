package com.autotriage.worker.light.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class GitInputValidatorTest {

    private static final Set<String> ALLOWED = Set.of("github.com");

    @AfterEach
    void clearConfig() {
        System.clearProperty(GitInputValidator.ALLOWED_HOSTS_CONFIG);
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://github.com/example/repo.git",
            "https://GitHub.com/example/repo",
            "ssh://git@github.com/example/repo.git",
            "git@github.com:example/repo.git"
    })
    void acceptsNetworkRepositoriesOnAllowlistedHosts(String repository) {
        assertEquals(repository, GitInputValidator.requireAllowedRepository(repository, ALLOWED));
    }

    @ParameterizedTest
    @ValueSource(strings = {
            // Token exfiltration: the clone token would be sent to whichever host the caller names.
            "https://attacker.example/example/repo.git",
            "https://github.com.attacker.example/example/repo.git",
            "git@attacker.example:example/repo.git",
            // Local and non-network transports read the worker's own filesystem or run commands.
            "file:///etc",
            "/var/run/secrets",
            "ext::sh -c touch% /tmp/pwned",
            "http://github.com/example/repo.git",
            // Option injection into `git clone`.
            "--upload-pack=touch /tmp/pwned",
            "-uhttps://github.com/example/repo.git",
            "git@github.com:-oProxyCommand=touch",
            "git@github.com:-oProxyCommand",
            // Embedded credentials, queries and fragments.
            "https://user:pass@github.com/example/repo.git",
            "https://user@github.com/example/repo.git",
            "ssh://git:secret@github.com/example/repo.git",
            "https://github.com/example/repo.git?x=1",
            "https://github.com",
            ""
    })
    void rejectsRepositoriesThatCouldLeakTheTokenOrReachLocalTransports(String repository) {
        assertThrows(IllegalArgumentException.class,
                () -> GitInputValidator.requireAllowedRepository(repository, ALLOWED));
    }

    @Test
    void rejectsNullRepository() {
        assertThrows(IllegalArgumentException.class,
                () -> GitInputValidator.requireAllowedRepository(null, ALLOWED));
    }

    @Test
    void readsAllowedHostsFromConfiguration() {
        System.setProperty(GitInputValidator.ALLOWED_HOSTS_CONFIG, " github.example.com , GitLab.example.com ");

        assertEquals(Set.of("github.example.com", "gitlab.example.com"), GitInputValidator.allowedHosts());
        assertEquals("https://gitlab.example.com/team/repo.git",
                GitInputValidator.requireAllowedRepository("https://gitlab.example.com/team/repo.git"));
        assertThrows(IllegalArgumentException.class,
                () -> GitInputValidator.requireAllowedRepository("https://github.com/example/repo.git"));
    }

    @Test
    void defaultsToGitHubWhenNoHostsConfigured() {
        assertEquals(Set.of("github.com"), GitInputValidator.allowedHosts());
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc1234", "0123456789abcdef0123456789abcdef01234567",
            "0123456789abcdef0123456789abcdef0123456789abcdef0123456789abcdef"})
    void acceptsCommitShas(String sha) {
        assertEquals(sha, GitInputValidator.requireCommitSha(sha));
    }

    @ParameterizedTest
    @ValueSource(strings = {"--orphan=x", "-f", "HEAD", "main", "abc12", "abc1234~1", ""})
    void rejectsNonShaCommitIds(String sha) {
        assertThrows(IllegalArgumentException.class, () -> GitInputValidator.requireCommitSha(sha));
    }

    @ParameterizedTest
    @ValueSource(strings = {"main", "feature/login-fix", "release-1.2.3", "v1.0", "abc1234"})
    void acceptsBranchTagAndShaRefs(String ref) {
        assertEquals(ref, GitInputValidator.requireRef(ref));
    }

    @ParameterizedTest
    @ValueSource(strings = {"--orphan=evil", "-f", "main..evil", "HEAD@{1}", "main~1", "main^", "a b",
            "/main", "main/", "feature//x", "feature/.hidden", ".hidden", "main.lock", "main.", "a:b", ""})
    void rejectsRefsGitWouldTreatAsOptionsOrExpressions(String ref) {
        assertThrows(IllegalArgumentException.class, () -> GitInputValidator.requireRef(ref));
    }
}
