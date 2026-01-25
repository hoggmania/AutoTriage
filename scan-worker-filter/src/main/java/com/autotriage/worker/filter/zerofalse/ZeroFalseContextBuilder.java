package com.autotriage.worker.filter.zerofalse;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ApplicationScoped
public class ZeroFalseContextBuilder {

    private static final Logger log = Logger.getLogger(ZeroFalseContextBuilder.class);

    public ZeroFalseContext build(Path sourceRoot, JsonNode runNode, JsonNode result, ZeroFalseSettings settings) {
        List<TraceStep> steps = extractTraceSteps(result, runNode, settings.maxTraceSteps(), sourceRoot);
        String annotatedTrace = formatAnnotatedTrace(steps);
        String codeContext = formatCodeContext(steps, settings);
        return new ZeroFalseContext(codeContext, annotatedTrace);
    }

    private List<TraceStep> extractTraceSteps(JsonNode result, JsonNode runNode, int maxSteps, Path sourceRoot) {
        List<TraceStep> steps = new ArrayList<>();
        if (result == null) {
            return steps;
        }
        JsonNode codeFlows = result.path("codeFlows");
        if (codeFlows != null && codeFlows.isArray()) {
            for (JsonNode codeFlow : codeFlows) {
                JsonNode threadFlows = codeFlow.path("threadFlows");
                if (threadFlows == null || !threadFlows.isArray()) {
                    continue;
                }
                for (JsonNode threadFlow : threadFlows) {
                    JsonNode locations = threadFlow.path("locations");
                    if (locations == null || !locations.isArray()) {
                        locations = threadFlow.path("threadFlowLocations");
                    }
                    if (locations == null || !locations.isArray()) {
                        continue;
                    }
                    for (JsonNode location : locations) {
                        if (steps.size() >= maxSteps) {
                            return steps;
                        }
                        TraceStep step = parseTraceStep(location, runNode, sourceRoot);
                        if (step != null) {
                            steps.add(step);
                        }
                    }
                }
            }
        }
        if (!steps.isEmpty()) {
            return steps;
        }
        JsonNode locations = result.path("locations");
        if (locations != null && locations.isArray()) {
            for (JsonNode location : locations) {
                if (steps.size() >= maxSteps) {
                    break;
                }
                TraceStep step = parseTraceStep(location, runNode, sourceRoot);
                if (step != null) {
                    steps.add(step);
                }
            }
        }
        return steps;
    }

    private TraceStep parseTraceStep(JsonNode locationNode, JsonNode runNode, Path sourceRoot) {
        if (locationNode == null || locationNode.isMissingNode()) {
            return null;
        }
        JsonNode resolved = locationNode.path("location");
        if (resolved.isMissingNode()) {
            resolved = locationNode;
        }
        JsonNode physical = resolved.path("physicalLocation");
        if (physical.isMissingNode()) {
            return null;
        }
        JsonNode artifactLocation = physical.path("artifactLocation");
        String uri = artifactLocation.path("uri").asText(null);
        String baseId = artifactLocation.path("uriBaseId").asText(null);
        Path path = resolveArtifactPath(sourceRoot, runNode, uri, baseId);
        int line = physical.path("region").path("startLine").asInt(-1);
        String message = locationNode.path("message").path("text").asText(null);
        String displayPath = displayPath(path, uri, sourceRoot);
        return new TraceStep(path, displayPath, line, message);
    }

    private Path resolveArtifactPath(Path sourceRoot, JsonNode runNode, String uri, String baseId) {
        if (uri == null || uri.isBlank()) {
            return null;
        }
        if (uri.startsWith("file:")) {
            try {
                return Path.of(URI.create(uri));
            } catch (Exception ignored) {
                return null;
            }
        }
        String baseUri = null;
        if (baseId != null && runNode != null) {
            baseUri = runNode.path("originalUriBaseIds").path(baseId).path("uri").asText(null);
        }
        if (baseUri != null && !baseUri.isBlank()) {
            try {
                Path basePath = baseUri.startsWith("file:") ? Path.of(URI.create(baseUri)) : Path.of(baseUri);
                return basePath.resolve(uri).normalize();
            } catch (Exception ignored) {
                return null;
            }
        }
        if (sourceRoot != null) {
            return sourceRoot.resolve(uri).normalize();
        }
        try {
            return Path.of(uri);
        } catch (Exception ignored) {
            return null;
        }
    }

    private String displayPath(Path path, String uri, Path sourceRoot) {
        if (path != null) {
            if (sourceRoot != null && path.startsWith(sourceRoot)) {
                return sourceRoot.relativize(path).toString();
            }
            return path.toString();
        }
        return uri == null ? "unknown" : uri;
    }

    private String formatAnnotatedTrace(List<TraceStep> steps) {
        StringBuilder builder = new StringBuilder();
        int index = 1;
        for (TraceStep step : steps) {
            if (step == null) {
                continue;
            }
            builder.append(index++)
                    .append(". ")
                    .append(step.displayPath())
                    .append(step.line() > 0 ? ":" + step.line() : "")
                    .append(step.message() != null ? " - " + step.message() : "")
                    .append("\n");
        }
        return builder.toString().trim();
    }

    private String formatCodeContext(List<TraceStep> steps, ZeroFalseSettings settings) {
        StringBuilder builder = new StringBuilder();
        Map<Path, List<String>> fileCache = new HashMap<>();
        Set<String> seen = new HashSet<>();
        int index = 1;
        for (TraceStep step : steps) {
            if (step == null) {
                continue;
            }
            String key = step.displayPath() + ":" + step.line();
            if (!seen.add(key)) {
                continue;
            }
            builder.append("Step ").append(index++).append("\n");
            builder.append("File: ").append(step.displayPath());
            if (step.line() > 0) {
                builder.append(" line ").append(step.line());
            }
            builder.append("\n");
            builder.append(loadSnippet(step, settings, fileCache)).append("\n\n");
        }
        return builder.toString().trim();
    }

    private String loadSnippet(TraceStep step, ZeroFalseSettings settings, Map<Path, List<String>> fileCache) {
        if (step.path() == null || step.line() <= 0) {
            return "<<source unavailable>>";
        }
        try {
            List<String> lines = fileCache.computeIfAbsent(step.path(), path -> {
                try {
                    return Files.readAllLines(path, StandardCharsets.UTF_8);
                } catch (Exception e) {
                    log.debugv("ZeroFalse failed to read source {0}: {1}", path, e.getMessage());
                    return List.of();
                }
            });
            if (lines.isEmpty()) {
                return "<<source unavailable>>";
            }
            int start = Math.max(1, step.line() - settings.contextLinesBefore());
            int end = Math.min(lines.size(), step.line() + settings.contextLinesAfter());
            StringBuilder snippet = new StringBuilder();
            for (int line = start; line <= end; line++) {
                snippet.append(String.format("%4d: %s", line, lines.get(line - 1))).append("\n");
            }
            return snippet.toString().trim();
        } catch (Exception e) {
            return "<<source unavailable>>";
        }
    }

    private record TraceStep(Path path, String displayPath, int line, String message) {
    }
}
