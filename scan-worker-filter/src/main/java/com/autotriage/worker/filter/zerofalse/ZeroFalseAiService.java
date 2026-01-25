package com.autotriage.worker.filter.zerofalse;

import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.V;
import io.quarkiverse.langchain4j.RegisterAiService;

@RegisterAiService
public interface ZeroFalseAiService {
    @UserMessage("{prompt}")
    String evaluate(@V("prompt") String prompt);
}
