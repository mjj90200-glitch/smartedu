package com.smartedu.service.ai;

import com.smartedu.agent.HomeworkAgentTools;
import com.smartedu.agent.HomeworkAgentTools.SubmitHomeworkRequest;
import com.smartedu.agent.HomeworkAgentTools.PublishHomeworkRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

import reactor.core.publisher.Flux;
import java.time.Duration;

/**
 * AI Agent 编排服务
 * 作为 AI Agent 的"大脑"，负责：
 * 1. 构建 ChatClient 并注册工具函数
 * 2. 设计 System Prompt 指导 Agent 行为
 * 3. 处理流式响应（SSE）
 * 4. 权限校验和工具调用
 *
 * 修复说明：
 * - 删除了 ThreadLocal<UserContext>，避免异步内存泄露
 * - 使用闭包捕获用户信息，在工具函数中直接使用
 *
 * @author SmartEdu Team
 */
@Service
public class AgentOrchestratorService {

    private static final Logger logger = LoggerFactory.getLogger(AgentOrchestratorService.class);

    // 流式响应超时时间（秒）
    private static final int STREAM_TIMEOUT_SECONDS = 120;

    private static final String SYSTEM_PROMPT =
        "你是智慧教育平台的 AI 助手，名为\"智学助手\"。你的主要职责是帮助学生和教师完成作业相关的操作。\n\n" +
        "## 你的能力\n\n" +
        "你拥有以下工具：\n" +
        "1. submit_student_homework - 帮助学生提交作业\n" +
        "2. publish_teacher_homework - 帮助教师发布作业\n\n" +
        "## 使用原则\n\n" +
        "1. **身份识别**：在调用任何工具前，必须确认用户的身份（学生/教师）\n" +
        "2. **信息收集**：确保收集到必要的信息：\n" +
        "   - 学生提交作业：需要作业 ID、文件 URL\n" +
        "   - 教师发布作业：需要课程 ID、作业标题、文档 URL\n" +
        "3. **权限验证**：学生不能发布作业，教师不能代学生提交作业\n" +
        "4. **自然交互**：用友好、专业的语言与用户交流，避免技术术语\n\n" +
        "## 交互流程\n\n" +
        "### 学生提交作业\n" +
        "1. 确认用户是学生身份\n" +
        "2. 询问或确认作业 ID\n" +
        "3. 确认文件已上传并获取文件 URL\n" +
        "4. 调用 submit_student_homework 工具\n" +
        "5. 告知提交结果\n\n" +
        "### 教师发布作业\n" +
        "1. 确认用户是教师身份\n" +
        "2. 询问课程 ID 和作业标题\n" +
        "3. 确认文档已上传并获取文件 URL\n" +
        "4. 询问截止时间（可选）\n" +
        "5. 调用 publish_teacher_homework 工具\n" +
        "6. 告知发布结果\n\n" +
        "## 注意事项\n\n" +
        "- 如果用户没有提供必要信息，请友好地询问\n" +
        "- 如果工具调用失败，解释原因并提供建议\n" +
        "- 保持回答简洁，重点突出\n" +
        "- 对于敏感操作（如提交作业），请用户确认后再执行\n";

    private final ChatModel chatModel;
    private final HomeworkAgentTools agentTools;

    public AgentOrchestratorService(ChatModel chatModel, HomeworkAgentTools agentTools) {
        this.chatModel = chatModel;
        this.agentTools = agentTools;

        logger.info("AgentOrchestratorService 初始化完成");
    }

    /**
     * 处理聊天请求（非流式）
     *
     * @param userMessage 用户消息
     * @param conversationHistory 对话历史
     * @param userId 用户 ID（显式传递）
     * @param userRole 用户角色（显式传递）
     * @return AI 响应
     */
    public String chat(String userMessage, List<Map<String, String>> conversationHistory,
                       Long userId, String userRole) {
        logger.info("收到聊天请求：{}, userId={}, userRole={}", userMessage, userId, userRole);

        try {
            // 构建消息历史
            StringBuilder promptBuilder = new StringBuilder();

            if (conversationHistory != null && !conversationHistory.isEmpty()) {
                for (Map<String, String> msg : conversationHistory) {
                    String role = msg.get("role");
                    String content = msg.get("content");
                    if (role != null && content != null) {
                        promptBuilder.append(role).append(": ").append(content).append("\n");
                    }
                }
            }

            promptBuilder.append("user: ").append(userMessage);

            // 使用闭包捕获用户信息，创建临时 ChatClient
            ChatClient chatClient = buildChatClientWithContext(userId, userRole);

            // 调用 AI 模型
            String response = chatClient.prompt()
                .user(promptBuilder.toString())
                .call()
                .content();

            logger.info("AI 响应长度：{}", response != null ? response.length() : 0);
            return response;

        } catch (Exception e) {
            logger.error("聊天处理失败", e);
            return "抱歉，处理您的请求时遇到错误，请稍后重试。";
        }
    }

    /**
     * 处理聊天请求（流式/SSE）
     *
     * @param userMessage 用户消息
     * @param conversationHistory 对话历史
     * @param userId 用户 ID（显式传递）
     * @param userRole 用户角色（显式传递）
     * @param username 用户名（用于日志）
     * @return 流式响应
     */
    public Flux<String> chatStream(String userMessage, List<Map<String, String>> conversationHistory,
                                    Long userId, String userRole, String username) {
        logger.info("收到流式聊天请求：{}, userId={}, userRole={}", userMessage, userId, userRole);

        // 参数校验
        if (userMessage == null || userMessage.trim().isEmpty()) {
            return Flux.just(formatSseError("消息内容不能为空"));
        }

        // 构建消息历史
        StringBuilder promptBuilder = new StringBuilder();
        if (conversationHistory != null && !conversationHistory.isEmpty()) {
            for (Map<String, String> msg : conversationHistory) {
                String role = msg.get("role");
                String content = msg.get("content");
                if (role != null && content != null) {
                    promptBuilder.append(role).append(": ").append(content).append("\n");
                }
            }
        }
        promptBuilder.append("user: ").append(userMessage);

        final String prompt = promptBuilder.toString();

        // 使用闭包捕获用户信息
        ChatClient chatClient = buildChatClientWithContext(userId, userRole);

        try {
            Flux<String> response = chatClient.prompt()
                .user(prompt)
                .stream()
                .content();

            // ========== P0: 全面日志 + 多层容错 ==========
            return response
                .timeout(Duration.ofSeconds(STREAM_TIMEOUT_SECONDS))
                // 每个数据块到达时记录
                .doOnNext(content -> {
                    if (content != null && !content.isEmpty()) {
                        logger.debug("SSE chunk 收到 [userId={}, length={}]: {}",
                            userId, content.length(),
                            content.length() > 50 ? content.substring(0, 50) + "..." : content);
                    }
                })
                // ========== 关键修复：不手动添加 data: 前缀，让 Spring WebFlux 自动处理 ==========
                // 因为 Controller 的 produces = MediaType.TEXT_EVENT_STREAM_VALUE
                // Spring 会自动将 Flux 元素包装为 SSE 格式
                .doOnComplete(() -> {
                    logger.info("✅ 流式响应完成 - userId={}, username={}", userId, username);
                })
                // 错误时详细记录
                .doOnError(e -> {
                    logger.error("❌ 流式响应失败 - userId={}, username={}, errorType={}, message={}",
                        userId, username,
                        e.getClass().getSimpleName(),
                        e.getMessage());
                    // 打印完整堆栈（仅用于调试）
                    if (logger.isDebugEnabled()) {
                        logger.debug("完整错误堆栈:", e);
                    }
                })
                // 终止时清理
                .doOnTerminate(() -> logger.info("🔌 流式连接终止 - userId={}, username={}", userId, username))
                // ========== 第一层容错：单个 chunk 处理失败时记录并跳过 ==========
                .onErrorContinue((Throwable e, Object item) -> {
                    logger.warn("⚠️ 单个 chunk 处理失败，跳过继续 - item type: {}, error: {}",
                        item != null ? item.getClass().getSimpleName() : "null",
                        e.getMessage());
                })
                // ========== 第二层容错：整个流失败时返回错误 JSON ==========
                .onErrorResume(e -> {
                    logger.error("🔴 流完全失败，返回错误 SSE - userId={}", userId, e);
                    String errorMsg = e.getMessage() != null ? e.getMessage() : "AI 服务暂时不可用";
                    // 区分超时和其他错误
                    if (e instanceof java.util.concurrent.TimeoutException) {
                        errorMsg = "AI 响应超时，请稍后重试";
                    }
                    // Spring 会自动包装为 SSE 格式，这里只返回 JSON 错误字符串
                    return Flux.just("{\"error\": \"" + errorMsg.replace("\"", "\\\"") + "\"}");
                });

        } catch (Exception e) {
            logger.error("💥 流式聊天初始化失败 - userId={}, username={}", userId, username, e);
            return Flux.just("{\"error\": \"AI 初始化失败：" + e.getMessage().replace("\"", "\\\"") + "\"}");
        }
    }

    /**
     * 处理带文件的聊天请求
     */
    public String chatWithFile(String userMessage, String fileUrl, List<Map<String, String>> conversationHistory,
                               Long userId, String userRole) {
        logger.info("收到带文件的聊天请求：{}, 文件 URL: {}, userId={}", userMessage, fileUrl, userId);

        // 将文件信息添加到消息中
        String enhancedMessage = userMessage;
        if (fileUrl != null && !fileUrl.isEmpty()) {
            enhancedMessage = userMessage + "\n\n[附件文件：" + fileUrl + "]";
        }

        return chat(enhancedMessage, conversationHistory, userId, userRole);
    }

    /**
     * 处理带文件的流式聊天请求
     */
    public Flux<String> chatWithFileStream(String userMessage, String fileUrl, List<Map<String, String>> conversationHistory,
                                            Long userId, String userRole, String username) {
        logger.info("收到带文件的流式聊天请求：{}, 文件 URL: {}, userId={}", userMessage, fileUrl, userId);

        String enhancedMessage = userMessage;
        if (fileUrl != null && !fileUrl.isEmpty()) {
            enhancedMessage = userMessage + "\n\n[附件文件：" + fileUrl + "]";
        }

        return chatStream(enhancedMessage, conversationHistory, userId, userRole, username);
    }

    // ==================== 私有辅助方法 ====================

    /**
     * 构建带有用户上下文的 ChatClient
     * 使用闭包捕获用户信息，避免 ThreadLocal 问题
     */
    private ChatClient buildChatClientWithContext(Long userId, String userRole) {
        ChatClient.Builder builder = ChatClient.builder(chatModel)
            .defaultSystem(SYSTEM_PROMPT);

        // 注册学生提交作业工具 - 使用闭包捕获 userId 和 userRole
        final Long finalUserId = userId;
        final String finalUserRole = userRole;

        builder.defaultFunction("submit_student_homework",
            "学生提交作业到指定的作业。需要提供作业 ID 和已上传的文件 URL。系统会自动获取当前登录的学生身份。",
            (Function<SubmitHomeworkRequest, Map<String, Object>>) request -> {
                // 通过闭包访问用户信息，不依赖 ThreadLocal
                request.userId = finalUserId;
                request.userRole = finalUserRole;
                logger.info("调用 submit_student_homework: homeworkId={}, userId={}", request.homeworkId, finalUserId);
                return agentTools.doSubmitHomework(request);
            });

        // 注册教师发布作业工具 - 使用闭包捕获用户信息
        builder.defaultFunction("publish_teacher_homework",
            "教师发布新作业。需要提供课程 ID、作业标题和作业文档 URL。系统会自动解析文档中的题目并创建作业。",
            (Function<PublishHomeworkRequest, Map<String, Object>>) request -> {
                // 通过闭包访问用户信息
                request.userId = finalUserId;
                request.userRole = finalUserRole;
                logger.info("调用 publish_teacher_homework: courseId={}, userId={}", request.courseId, finalUserId);
                return agentTools.doPublishHomework(request);
            });

        return builder.build();
    }

    /**
     * 格式化 SSE 数据消息
     * SSE 协议规范：每条消息以 "data: " 开头，以 "\n\n" 结尾
     * 如果内容包含换行符，需要发送多个 "data:" 行
     *
     * P0 修复：处理可能的转义字符（如 \\n -> \n）
     *
     * @param content 原始内容（不进行 JSON 转义）
     * @return 格式化后的 SSE 消息
     */
    private String formatSseData(String content) {
        if (content == null || content.isEmpty()) {
            return "";
        }

        // ========== P0 修复：取消转义，确保 \n 是真正的换行符 ==========
        // 某些情况下，AI 返回的内容可能包含转义的换行符 \\n
        String unescapedContent = content
            .replace("\\n", "\n")      // 转义的换行符 -> 真正的换行符
            .replace("\\t", "\t")      // 转义的制表符
            .replace("\\r", "\r");     // 转义的回车符

        // 如果内容包含换行符，需要为每一行添加 "data:" 前缀
        // 这是 SSE 协议的标准行为
        StringBuilder sb = new StringBuilder();
        String[] lines = unescapedContent.split("\n", -1);  // -1 保留空行
        for (String line : lines) {
            sb.append("data: ").append(line).append("\n");
        }
        sb.append("\n");  // SSE 消息结束的空行
        return sb.toString();
    }

    /**
     * 格式化 SSE 错误消息
     * 使用特殊的 JSON 格式，前端可以识别为错误
     */
    private String formatSseError(String errorMessage) {
        // 错误消息需要转义，防止 JSON 格式错误
        String escaped = errorMessage
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", " ");  // 错误消息中的换行替换为空格
        return "data: {\"error\": \"" + escaped + "\"}\n\n";
    }
}