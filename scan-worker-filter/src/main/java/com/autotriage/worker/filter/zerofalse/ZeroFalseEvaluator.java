package com.autotriage.worker.filter.zerofalse;

import java.util.Optional;

public interface ZeroFalseEvaluator {
    Optional<ZeroFalseVerdict> evaluate(String prompt);
}
