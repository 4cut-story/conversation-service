package net.java21.fourcut.conversation.pipeline;

/** LLM 작업이 대기열에 들어갔다. 커밋 뒤 Gemini가 먼저 처리해 본다. */
record LlmJobQueued(Long jobId) {}
