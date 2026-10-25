package com.autotriage.worker.light.security;

import org.eclipse.microprofile.config.ConfigProvider;

import java.net.URI;
import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Validates caller-supplied git inputs before they reach the git CLI. Scan requests arrive from the network,
 * so the repository location decides where the clone token is sent and refs end up as git arguments.
 */
public final class GitInputValidator {

    public static final String ALLOWED_HOSTS_CONFIG = "git.clone.allowed-hosts";
    private static final String DEFAULT_ALLOWED_HOSTS = "github.com";
    private static final Pattern SCP_LIKE = Pattern.compile("^([A-Za-z0-9._-]+)@([A-Za-z0-9.-]+):([A-Za-z0-9._~/-]+)$");
    private static final Pattern REPOSITORY_PATH = Pattern.compile("^/?[A-Za-z0-9._~-]+(/[A-Za-z0-9._~-]+)*/?$");
    private static final Pattern COMMIT_SHA = Pattern.compile("^([0-9a-fA-F]{7,40}|[0-9a-fA-F]{64})$");
    private static final Pattern REF = Pattern.compile("^[A-Za-z0-9._/-]+$");
    private static final int MAX_REF_LENGTH = 255;

    private GitInputValidator() {
    }

    /** Returns the repository unchanged when it is an https or ssh location on an allowlisted host. */
    public static String requireAllowedRepository(String repository) {
        return requireAllowedRepository(repository, allowedHosts());
    }

    static String requireAllowedRepository(String repository, Set<String> allowedHosts) {
        if (repository == null || repository.isBlank()) {
            throw new IllegalArgumentException("repository is required");
        }
        String host = repositoryHost(repository);
        if (!allowedHosts.contains(host.toLowerCase(Locale.ROOT))) {
            throw new IllegalArgumentException("repository host is not allowlisted in " + ALLOWED_HOSTS_CONFIG);
        }
        return repository;
    }

    /** Accepts an abbreviated or full commit SHA (SHA-1 or SHA-256 object format). */
    public static String requireCommitSha(String commitSha) {
        if (commitSha == null || !COMMIT_SHA.matcher(commitSha).matches()) {
            throw new IllegalArgumentException("commitSha must be a hexadecimal commit id");
        }
        return commitSha;
    }

    /** Accepts a commit SHA or a branch/tag name that git cannot interpret as an option or revision expression. */
    public static String requireRef(String ref) {
        if (ref == null || ref.isBlank()) {
            throw new IllegalArgumentException("ref is required");
        }
        if (COMMIT_SHA.matcher(ref).matches()) {
            return ref;
        }
        if (ref.length() > MAX_REF_LENGTH
                || !REF.matcher(ref).matches()
                || ref.startsWith("-")
                || ref.startsWith("/")
                || ref.endsWith("/")
                || ref.endsWith(".")
                || ref.endsWith(".lock")
                || ref.contains("..")
                || ref.contains("//")
                || ref.contains("/.")
                || ref.startsWith(".")) {
            throw new IllegalArgumentException("ref is not a valid branch or tag name");
        }
        return ref;
    }

    private static String repositoryHost(String repository) {
        if (repository.startsWith("-")) {
            throw new IllegalArgumentException("repository must not start with '-'");
        }
        Matcher scp = SCP_LIKE.matcher(repository);
        if (scp.matches()) {
            if (scp.group(3).startsWith("-")) {
                throw new IllegalArgumentException("repository path must not start with '-'");
            }
            return scp.group(2);
        }
        URI uri;
        try {
            uri = new URI(repository);
        } catch (Exception e) {
            throw new IllegalArgumentException("repository must be an https or ssh URL", e);
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("ssh")) {
            throw new IllegalArgumentException("repository must use the https or ssh scheme");
        }
        if (uri.getHost() == null || uri.getHost().isBlank()) {
            throw new IllegalArgumentException("repository URL must include a host");
        }
        String userInfo = uri.getRawUserInfo();
        if (userInfo != null && (scheme.equals("https") || userInfo.contains(":"))) {
            throw new IllegalArgumentException("repository URL must not embed credentials");
        }
        if (uri.getRawQuery() != null || uri.getRawFragment() != null) {
            throw new IllegalArgumentException("repository URL must not include a query or fragment");
        }
        String path = uri.getRawPath();
        if (path == null || path.isEmpty() || path.equals("/") || !REPOSITORY_PATH.matcher(path).matches()) {
            throw new IllegalArgumentException("repository URL path is invalid");
        }
        return uri.getHost();
    }

    static Set<String> allowedHosts() {
        String configured = ConfigProvider.getConfig()
                .getOptionalValue(ALLOWED_HOSTS_CONFIG, String.class)
                .filter(value -> !value.isBlank())
                .orElse(DEFAULT_ALLOWED_HOSTS);
        return Arrays.stream(configured.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
