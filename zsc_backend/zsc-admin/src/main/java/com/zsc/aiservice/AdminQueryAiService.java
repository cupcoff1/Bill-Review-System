package com.zsc.aiservice;

import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.SystemMessage;
import dev.langchain4j.service.UserMessage;
import dev.langchain4j.service.spring.AiService;
import dev.langchain4j.service.spring.AiServiceWiringMode;
import reactor.core.publisher.Flux;

/**
 * 管理员 AI 查询服务接口
 * 通过 Tool Calling 调用 AdminQueryTools 查询系统数据，
 * 结合 RAG 知识库，用自然语言回复管理员
 */
@AiService(
        wiringMode = AiServiceWiringMode.EXPLICIT,
        chatModel = "openAiChatModel",
        streamingChatModel = "openAiStreamingChatModel",
        chatMemoryProvider = "chatMemoryProvider",
        contentRetriever = "contentRetriever",
        tools = "adminQueryTools"
)
public interface AdminQueryAiService {

    /**
     * 管理员 AI 对话（流式）
     * @param memoryId 会话ID，用于区分多轮对话
     * @param message  管理员输入的自然语言问题
     * @return 流式 AI 回复
     */
    @SystemMessage(fromResource = "system-admin.txt")
    Flux<String> query(@MemoryId String memoryId, @UserMessage String message);
}
