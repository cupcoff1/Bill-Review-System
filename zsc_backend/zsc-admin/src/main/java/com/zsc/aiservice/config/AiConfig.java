package com.zsc.aiservice.config;

import com.zsc.aiservice.repository.RedisChatMemoryStore;
import dev.langchain4j.data.document.Document;
import dev.langchain4j.data.document.splitter.DocumentSplitters;
import dev.langchain4j.data.segment.TextSegment;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import dev.langchain4j.rag.content.retriever.ContentRetriever;
import dev.langchain4j.rag.content.retriever.EmbeddingStoreContentRetriever;
import dev.langchain4j.store.embedding.EmbeddingStore;
import dev.langchain4j.store.embedding.EmbeddingStoreIngestor;
import dev.langchain4j.community.store.embedding.redis.RedisEmbeddingStore;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

/**
 * LangChain4j AI 配置类
 * 配置 ChatMemory、Embedding Store、ContentRetriever（RAG）
 */
@Configuration
public class AiConfig {

    @Value("${langchain4j.open-ai.embedding-model.base-url}")
    private String embeddingBaseUrl;

    @Value("${langchain4j.open-ai.embedding-model.api-key}")
    private String embeddingApiKey;

    @Value("${langchain4j.open-ai.embedding-model.model-name}")
    private String embeddingModelName;

    /**
     * ChatMemoryProvider — 为每个会话提供独立的聊天记忆
     * 最多保留最近 20 轮对话
     */
    @Bean
    ChatMemoryProvider chatMemoryProvider(ChatMemoryStore chatMemoryStore) {
        return memoryId -> MessageWindowChatMemory.builder()
                .id(memoryId)
                .chatMemoryStore(chatMemoryStore)
                .maxMessages(20)
                .build();
    }

    /**
     * ChatMemoryStore — Redis 后端实现
     * 使用独立的 RedisTemplate<String,String> 避免与项目 FastJson2 序列化冲突
     */
    @Bean
    ChatMemoryStore chatMemoryStore(RedisConnectionFactory connectionFactory) {
        return new RedisChatMemoryStore(connectionFactory, "langchain4j:memory:");
    }

    /**
     * EmbeddingModel — DashScope text-embedding-v3（OpenAI 兼容协议）
     * 输出 1024 维向量
     */
    @Bean
    EmbeddingModel embeddingModel() {
        return OpenAiEmbeddingModel.builder()
                .baseUrl(embeddingBaseUrl)
                .apiKey(embeddingApiKey)
                .modelName(embeddingModelName)
                .timeout(Duration.ofSeconds(30))
                .maxSegmentsPerBatch(10)
                .build();
    }

    /**
     * Redis 嵌入向量存储
     * 复用项目已有的 Redis 配置（spring.data.redis.host/port）
     */
    @Bean
    EmbeddingStore<TextSegment> embeddingStore(
            @Value("${spring.data.redis.host:localhost}") String redisHost,
            @Value("${spring.data.redis.port:6379}") int redisPort) {
        return RedisEmbeddingStore.builder()
                .host(redisHost)
                .port(redisPort)
                .indexName("admin-knowledge")
                .dimension(1024)
                .build();
    }

    /**
     * RAG 内容检索器
     * 最小相似度 0.5，最多返回 3 个相关片段
     */
    @Bean
    ContentRetriever contentRetriever(
            EmbeddingStore<TextSegment> embeddingStore,
            EmbeddingModel embeddingModel) {
        return EmbeddingStoreContentRetriever.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .maxResults(3)
                .minScore(0.5)
                .build();
    }

    /**
     * 知识库文档摄入器（首次启动运行）
     * 加载 admin_knowledge.md，递归切分（500字符/100重叠），向量化存入 Redis
     *
     * 第二次启动后可注释掉 @Bean 以避免重复摄入（数据已在 Redis 中持久化）
     */
    @Bean
    EmbeddingStoreIngestor embeddingStoreIngestor(
            EmbeddingModel embeddingModel,
            EmbeddingStore<TextSegment> embeddingStore,
            @Value("classpath:content/admin_knowledge.md") Resource knowledgeResource)
            throws IOException {

        String content = new String(
                knowledgeResource.getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);

        Document document = Document.from(
                content,
                dev.langchain4j.data.document.Metadata.from("source", "admin_knowledge.md"));

        EmbeddingStoreIngestor ingestor = EmbeddingStoreIngestor.builder()
                .embeddingStore(embeddingStore)
                .embeddingModel(embeddingModel)
                .documentSplitter(DocumentSplitters.recursive(500, 100))
                .build();

        ingestor.ingest(document);
        return ingestor;
    }
}
