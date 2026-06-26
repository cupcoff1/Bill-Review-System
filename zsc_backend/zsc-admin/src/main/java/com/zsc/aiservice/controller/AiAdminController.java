package com.zsc.aiservice.controller;

import com.zsc.aiservice.AdminQueryAiService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;



/**
 * 管理员 AI 助手控制器
 * 提供基于自然语言的系统数据查询和分析接口（SSE 流式输出）
 */
@Slf4j
@Tag(name = "管理员AI助手")
@RestController
@RequestMapping("/ai")
public class AiAdminController {

    @Autowired
    private AdminQueryAiService aiService;

    /**
     * 管理员 AI 对话（SSE 流式）
     * 示例：GET /ai/admin/query?memoryId=admin001&message=今天系统情况怎么样？
     */
    @Operation(
            summary = "管理员AI对话",
            description = "用自然语言查询系统数据和分析。示例：" +
                    "\"今天系统情况怎么样？\"、" +
                    "\"审核员工作量排名\"、" +
                    "\"分析一下张三\"、" +
                    "\"本月提交趋势\""
    )
    @PreAuthorize("@ss.hasPermi('biz:admin:list')")
    @GetMapping(value = "/admin/query", produces = "text/event-stream;charset=utf-8")
    public Flux<String> query(
            @RequestParam String memoryId,
            @RequestParam String message) {
        log.info("AI query: memoryId={}, message={}", memoryId, message);
        return aiService.query(memoryId, message)
                .doOnError(e -> log.error("AI query error", e));
    }
}
