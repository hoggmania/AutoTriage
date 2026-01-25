package com.autotriage.worker.filter.zerofalse;

import java.util.Optional;

public class ZeroFalseEvaluatorDisabled implements ZeroFalseEvaluator {
    @Override
    public Optional<ZeroFalseVerdict> evaluate(String prompt) {
        return Optional.empty();
    }
}
