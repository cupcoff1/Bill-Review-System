package com.zsc.aiservice.repository;

import dev.langchain4j.data.message.ChatMessage;
import dev.langchain4j.data.message.ChatMessageDeserializer;
import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.store.memory.chat.ChatMemoryStore;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Redis 实现的 ChatMemoryStore
 * 使用独立的 RedisTemplate<String, String>（String 序列化器）
 * 避免与项目已有的 FastJson2 序列化冲突
 */
public class RedisChatMemoryStore implements ChatMemoryStore {

    private final RedisTemplate<String, String> redisTemplate;
    private final String keyPrefix;
    private static final long TTL_DAYS = 1;

    public RedisChatMemoryStore(RedisConnectionFactory factory, String keyPrefix) {
        this.keyPrefix = keyPrefix;
        this.redisTemplate = new RedisTemplate<>();
        this.redisTemplate.setConnectionFactory(factory);
        this.redisTemplate.setKeySerializer(new StringRedisSerializer());
        this.redisTemplate.setValueSerializer(new StringRedisSerializer());
        this.redisTemplate.afterPropertiesSet();
    }

    @Override
    public List<ChatMessage> getMessages(Object memoryId) {
        String key = keyPrefix + memoryId;
        String json = redisTemplate.opsForValue().get(key);
        if (json == null || json.isEmpty()) {
            return new ArrayList<>();
        }
        try {
            return ChatMessageDeserializer.messagesFromJson(json);
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    @Override
    public void updateMessages(Object memoryId, List<ChatMessage> messages) {
        String key = keyPrefix + memoryId;
        String json = ChatMessageSerializer.messagesToJson(messages);
        redisTemplate.opsForValue().set(key, json, TTL_DAYS, TimeUnit.DAYS);
    }

    @Override
    public void deleteMessages(Object memoryId) {
        redisTemplate.delete(keyPrefix + memoryId);
    }
}
