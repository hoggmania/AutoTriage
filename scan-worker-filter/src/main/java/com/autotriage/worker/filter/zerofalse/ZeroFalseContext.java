package com.autotriage.worker.filter.zerofalse;

public record ZeroFalseContext(String codeContext, String annotatedTrace) {
    public boolean isEmpty() {
        return (codeContext == null || codeContext.isBlank())
                && (annotatedTrace == null || annotatedTrace.isBlank());
    }
}
