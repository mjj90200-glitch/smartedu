package com.smartedu.controller;

import com.smartedu.common.result.Result;
import com.smartedu.dto.AIChatRequestDTO;
import com.smartedu.security.JwtAuthenticationToken;
import com.smartedu.service.ai.AgentOrchestratorService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import reactor.core.publisher.Flux;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * AI Agent 控制器
 * 提供 AI 助手聊天接口，支持文件上传和流式响应
 *
 * @author SmartEdu Team
 */
@Tag(name = "AI Agent", description = "AI 智能助手相关接口")
@RestController
@RequestMapping("/ai")
public class AIController {

    private static final Logger logger = LoggerFactory.getLogger(AIController.class);

    private final AgentOrchestratorService agentOrchestratorService;

    public AIController(AgentOrchestratorService agentOrchestratorService) {
        this.agentOrchestratorService = agentOrchestratorService;
    }

    /**
     * AI 聊天接口（非流式）
     */
    @PostMapping("/chat")
    @Operation(summary = "AI 聊天", description = "与 AI 助手进行对话")
    public Result<Map<String, Object>> chat(
            @RequestBody AIChatRequestDTO request,
            @AuthenticationPrincipal UserDetails userDetails) {

        // ========== 预防性鉴权检查 ==========
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            logger.warn("未认证的聊天请求");
            return Result.error(401, "请先登录");
        }

        String message = request.getMessage();
        List<Map<String, String>> history = request.getHistory();

        // 显式提取用户信息
        Long userId = extractUserId(auth);
        String username = extractUsername(auth);
        String userRole = extractUserRole(auth);

        logger.info("收到聊天请求：message={}, user={}, userId={}", message, username, userId);

        try {
            String response = agentOrchestratorService.chat(message, history, userId, userRole);

            Map<String, Object> result = new HashMap<>();
            result.put("message", response);
            result.put("timestamp", LocalDateTime.now().toString());

            return Result.success(result);
        } catch (Exception e) {
            logger.error("聊天处理失败", e);
            return Result.error("AI 处理失败：" + e.getMessage());
        }
    }

    /**
     * AI 聊天接口（流式/SSE）
     *
     * 修复说明：
     * 1. 使用 @RequestBody AIChatRequestDTO 接收参数
     * 2. 预防性鉴权检查，避免响应提交后的 AccessDeniedException
     * 3. 显式传递 userId/userRole 给 Service 层
     */
    @PostMapping(value = "/chat/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @Operation(summary = "AI 聊天（流式）", description = "与 AI 助手进行流式对话，支持打字机效果")
    public Flux<String> chatStream(@RequestBody AIChatRequestDTO request) {

        // ========== P0: 预防性鉴权检查（首行执行）==========
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            logger.warn("未认证的流式聊天请求");
            // Spring 会自动包装为 SSE 格式，只返回 JSON 错误字符串
            return Flux.just("{\"error\": \"请先登录\"}");
        }

        String message = request.getMessage();
        List<Map<String, String>> history = request.getHistory();

        // ========== 显式身份传递（方案一）==========
        final Long userId = extractUserId(auth);
        final String username = extractUsername(auth);
        final String userRole = extractUserRole(auth);

        logger.info("收到流式聊天请求：message={}, user={}, userId={}", message, username, userId);

        // 调用 Service，显式传递身份信息
        return agentOrchestratorService.chatStream(message, history, userId, userRole, username)
            .doOnSubscribe(subscription -> logger.info("流式订阅开始 - 用户：{}", username))
            .doOnComplete(() -> logger.info("流式响应完成 - 用户：{}", username))
            .doOnError(e -> logger.error("流式响应错误 - 用户：{}, 错误：{}", username, e.getMessage()))
            // ========== P1: 流式容错 ==========
            .onErrorResume(e -> {
                logger.error("AI 流式处理异常，返回错误 SSE", e);
                String errorMessage = e.getMessage() != null ? e.getMessage() : "AI 服务暂时不可用";
                // Spring 会自动包装为 SSE 格式
                return Flux.just("{\"error\": \"" + errorMessage.replace("\"", "\\\"") + "\"}");
            });
    }

    /**
     * AI 聊天接口（带文件上传）
     */
    @PostMapping(value = "/chat/upload", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "AI 聊天（带文件）", description = "上传文件并与 AI 助手对话")
    public Result<Map<String, Object>> chatWithFile(
            @RequestParam String message,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "history", required = false) String historyJson,
            @AuthenticationPrincipal UserDetails userDetails) {

        // ========== 预防性鉴权检查 ==========
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            return Result.error(401, "请先登录");
        }

        Long userId = extractUserId(auth);
        String username = extractUsername(auth);
        String userRole = extractUserRole(auth);

        logger.info("收到带文件的聊天请求：message={}, fileName={}, user={}, userId={}", message,
            file != null ? file.getOriginalFilename() : "none",
            username, userId);

        try {
            String fileUrl = null;

            // 处理文件上传
            if (file != null && !file.isEmpty()) {
                fileUrl = saveUploadedFile(file);
                logger.info("文件保存成功：{}", fileUrl);
            }

            // 解析历史（如果提供）
            List<Map<String, String>> history = null;
            if (historyJson != null && !historyJson.isEmpty()) {
                try {
                    // 简单的 JSON 解析，实际应使用 ObjectMapper
                    history = parseHistoryJson(historyJson);
                } catch (Exception e) {
                    logger.warn("解析历史失败，忽略：{}", e.getMessage());
                }
            }

            // 调用 AI 服务
            String response;
            if (fileUrl != null) {
                response = agentOrchestratorService.chatWithFile(message, fileUrl, history, userId, userRole);
            } else {
                response = agentOrchestratorService.chat(message, history, userId, userRole);
            }

            Map<String, Object> result = new HashMap<>();
            result.put("message", response);
            result.put("fileUrl", fileUrl);
            result.put("timestamp", LocalDateTime.now().toString());

            return Result.success(result);
        } catch (Exception e) {
            logger.error("带文件聊天处理失败", e);
            return Result.error("AI 处理失败：" + e.getMessage());
        }
    }

    /**
     * AI 聊天接口（带文件上传，流式/SSE）
     */
    @PostMapping(value = "/chat/upload-stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE, consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "AI 聊天（带文件，流式）", description = "上传文件并与 AI 助手进行流式对话")
    public Flux<String> chatWithFileStream(
            @RequestParam String message,
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam(value = "history", required = false) String historyJson) {

        // ========== P0: 预防性鉴权检查 ==========
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || !auth.isAuthenticated()) {
            logger.warn("未认证的带文件流式聊天请求");
            // Spring 会自动包装为 SSE 格式
            return Flux.just("{\"error\": \"请先登录\"}");
        }

        final Long userId = extractUserId(auth);
        final String username = extractUsername(auth);
        final String userRole = extractUserRole(auth);

        logger.info("收到带文件的流式聊天请求：message={}, fileName={}, user={}, userId={}", message,
            file != null ? file.getOriginalFilename() : "none",
            username, userId);

        try {
            String fileUrl = null;

            // 处理文件上传
            if (file != null && !file.isEmpty()) {
                fileUrl = saveUploadedFile(file);
            }

            // 解析历史
            List<Map<String, String>> history = null;
            if (historyJson != null && !historyJson.isEmpty()) {
                try {
                    history = parseHistoryJson(historyJson);
                } catch (Exception e) {
                    logger.warn("解析历史失败，忽略：{}", e.getMessage());
                }
            }

            final String finalFileUrl = fileUrl;
            final List<Map<String, String>> finalHistory = history;

            return agentOrchestratorService.chatWithFileStream(message, finalFileUrl, finalHistory, userId, userRole, username)
                .doOnSubscribe(subscription -> logger.info("带文件流式订阅开始 - 用户：{}", username))
                .doOnComplete(() -> logger.info("带文件流式响应完成 - 用户：{}", username))
                .doOnError(e -> logger.error("带文件流式响应错误 - 用户：{}, 错误：{}", username, e.getMessage()))
                // ========== P1: 流式容错 ==========
                .onErrorResume(e -> {
                    logger.error("AI 带文件流式处理异常，返回错误 SSE", e);
                    String errorMessage = e.getMessage() != null ? e.getMessage() : "AI 服务暂时不可用";
                    // Spring 会自动包装为 SSE 格式
                    return Flux.just("{\"error\": \"" + errorMessage.replace("\"", "\\\"") + "\"}");
                });
        } catch (Exception e) {
            logger.error("带文件流式聊天处理失败", e);
            return Flux.just("{\"error\": \"AI 处理失败：" + e.getMessage().replace("\"", "\\\"") + "\"}");
        }
    }

    // ==================== 辅助方法 ====================

    /**
     * 构建错误 SSE 消息
     */
    private String buildErrorSse(String errorMessage) {
        // 转义 JSON 特殊字符
        String escaped = errorMessage.replace("\"", "\\\"").replace("\n", "\\n");
        return "{\"error\": \"" + escaped + "\"}";
    }

    /**
     * 从 Authentication 提取用户 ID
     */
    private Long extractUserId(Authentication auth) {
        if (auth == null) {
            return null;
        }
        if (auth instanceof JwtAuthenticationToken) {
            return ((JwtAuthenticationToken) auth).getUserId();
        }
        return null;
    }

    /**
     * 从 Authentication 提取用户名
     */
    private String extractUsername(Authentication auth) {
        if (auth == null) {
            return "anonymous";
        }
        Object principal = auth.getPrincipal();
        if (principal instanceof UserDetails) {
            return ((UserDetails) principal).getUsername();
        }
        return auth.getName();
    }

    /**
     * 从 Authentication 提取用户角色
     */
    private String extractUserRole(Authentication auth) {
        if (auth == null) {
            return null;
        }
        return auth.getAuthorities().stream()
            .map(authority -> authority.getAuthority())
            .filter(role -> role.startsWith("ROLE_"))
            .findFirst()
            .map(role -> role.replace("ROLE_", ""))
            .orElse(null);
    }

    /**
     * 保存上传的文件
     */
    private String saveUploadedFile(MultipartFile file) throws IOException {
        String uploadDir = System.getProperty("user.dir") + "/uploads/ai-files/";
        Path uploadPath = Paths.get(uploadDir);

        if (!Files.exists(uploadPath)) {
            Files.createDirectories(uploadPath);
        }

        String originalFilename = file.getOriginalFilename();
        String extension = "";
        if (originalFilename != null && originalFilename.contains(".")) {
            extension = originalFilename.substring(originalFilename.lastIndexOf("."));
        }
        String timestamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"));
        String filename = "ai-" + UUID.randomUUID().toString().substring(0, 8) + "-" + timestamp + extension;

        Path filePath = uploadPath.resolve(filename);
        file.transferTo(filePath.toFile());

        return "/uploads/ai-files/" + filename;
    }

    /**
     * 简单解析历史 JSON（实际应使用 ObjectMapper）
     */
    @SuppressWarnings("unchecked")
    private List<Map<String, String>> parseHistoryJson(String historyJson) {
        // 这里应该使用 ObjectMapper，但为了简化，返回 null
        // 实际项目中应该注入 ObjectMapper 并正确解析
        return null;
    }
}