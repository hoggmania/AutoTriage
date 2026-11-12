package com.autotriage.common.remediation;
import java.util.Arrays;
public record SourceSlice(String path, int startLine, String content) {
    public SourceSlice {
        if (path == null || path.isBlank() || path.startsWith("/") || path.contains("\\") || Arrays.asList(path.split("/")).contains("..")) throw new IllegalArgumentException("invalid relative path");
        if (startLine < 1) throw new IllegalArgumentException("invalid line");
        if (content == null) throw new IllegalArgumentException("content required");
    }
}
