package com.smartedu.agent;

import com.smartedu.dto.HomeworkDTO;
import com.smartedu.service.HomeworkService;
import com.smartedu.service.QuestionImportService;
import com.smartedu.service.StudentHomeworkService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

/**
 * 作业 Agent 工具箱
 * 提供 AI 可调用的工具方法，用于学生提交作业和教师发布作业
 *
 * @author SmartEdu Team
 */
@Component
public class HomeworkAgentTools {

    private static final Logger logger = LoggerFactory.getLogger(HomeworkAgentTools.class);

    private final StudentHomeworkService studentHomeworkService;
    private final HomeworkService homeworkService;
    private final QuestionImportService questionImportService;

    public HomeworkAgentTools(StudentHomeworkService studentHomeworkService,
                              HomeworkService homeworkService,
                              QuestionImportService questionImportService) {
        this.studentHomeworkService = studentHomeworkService;
        this.homeworkService = homeworkService;
        this.questionImportService = questionImportService;
    }

    /**
     * 学生提交作业的实际执行方法
     * 注意：此方法设计为从 AI Function Calling 直接调用，不依赖 SecurityContext
     * 用户信息需要从外部显式传递
     */
    public Map<String, Object> doSubmitHomework(SubmitHomeworkRequest request) {
        logger.info("AI Agent 调用 submitHomework: homeworkId={}, fileUrl={}, userId={}",
            request.homeworkId, request.fileUrl, request.userId);

        Map<String, Object> result = new HashMap<>();
        try {
            // 使用显式传递的用户 ID
            Long studentId = request.userId;
            if (studentId == null) {
                result.put("success", false);
                result.put("error", "未登录或无法获取用户信息");
                return result;
            }

            // 验证用户角色（确保是学生）
            if (!"STUDENT".equals(request.userRole)) {
                result.put("success", false);
                result.put("error", "只有学生角色才能提交作业");
                return result;
            }

            // 调用服务层提交作业
            studentHomeworkService.submitHomeworkByUrl(request.homeworkId, studentId, request.fileUrl, request.content);

            result.put("success", true);
            result.put("message", "作业提交成功");
            result.put("homeworkId", request.homeworkId);
            result.put("submissionTime", LocalDateTime.now().toString());

            return result;
        } catch (Exception e) {
            logger.error("提交作业失败：homeworkId={}, error={}", request.homeworkId, e.getMessage());
            result.put("success", false);
            result.put("error", "提交失败：" + e.getMessage());
            return result;
        }
    }

    /**
     * 教师发布作业的实际执行方法
     * 注意：此方法设计为从 AI Function Calling 直接调用，不依赖 SecurityContext
     * 用户信息需要从外部显式传递
     */
    public Map<String, Object> doPublishHomework(PublishHomeworkRequest request) {
        logger.info("AI Agent 调用 publishHomework: courseId={}, title={}, fileUrl={}, userId={}",
            request.courseId, request.title, request.fileUrl, request.userId);

        Map<String, Object> result = new HashMap<>();
        try {
            // 使用显式传递的用户 ID
            Long teacherId = request.userId;
            if (teacherId == null) {
                result.put("success", false);
                result.put("error", "未登录或无法获取用户信息");
                return result;
            }

            // 验证用户角色（确保是教师）
            if (!"TEACHER".equals(request.userRole)) {
                result.put("success", false);
                result.put("error", "只有教师角色才能发布作业");
                return result;
            }

            // 解析文档导入题目
            MultipartFile docFile = new UrlBackedMultipartFile(request.fileUrl);
            Map<String, Object> importResult = questionImportService.importQuestions(docFile, request.courseId, teacherId);

            // 创建作业
            HomeworkDTO dto = new HomeworkDTO();
            dto.setTitle(request.title);
            dto.setDescription(request.description != null ? request.description : "自动创建的作业");
            dto.setCourseId(request.courseId);

            if (request.endTime != null && !request.endTime.isEmpty()) {
                try {
                    dto.setEndTime(LocalDateTime.parse(request.endTime.replace("Z", "")));
                } catch (Exception e) {
                    logger.warn("解析截止时间失败，使用默认值：{}", request.endTime);
                }
            }

            if (dto.getEndTime() == null) {
                dto.setEndTime(LocalDateTime.now().plusDays(7));
            }

            Long homeworkId = homeworkService.createHomework(dto, teacherId);

            result.put("success", true);
            result.put("message", "作业发布成功");
            result.put("homeworkId", homeworkId);
            result.put("questionsImported", importResult.get("success"));
            result.put("title", request.title);

            return result;
        } catch (Exception e) {
            logger.error("发布作业失败：courseId={}, title={}, error={}", request.courseId, request.title, e.getMessage());
            result.put("success", false);
            result.put("error", "发布失败：" + e.getMessage());
            return result;
        }
    }

    /**
     * 学生提交作业请求 DTO
     */
    public static class SubmitHomeworkRequest {
        public Long homeworkId;
        public String fileUrl;
        public String content;
        // 显式传递的用户信息（从 Controller 层传递，避免依赖 SecurityContext）
        public Long userId;
        public String userRole;
    }

    /**
     * 教师发布作业请求 DTO
     */
    public static class PublishHomeworkRequest {
        public Long courseId;
        public String title;
        public String fileUrl;
        public String description;
        public String endTime;
        // 显式传递的用户信息（从 Controller 层传递，避免依赖 SecurityContext）
        public Long userId;
        public String userRole;
    }

    /**
     * 内部类：基于 URL 的 MultipartFile 实现
     * 用于将已上传的文件 URL 转换回 MultipartFile 供服务层使用
     */
    private static class UrlBackedMultipartFile implements MultipartFile {
        private final String fileUrl;

        UrlBackedMultipartFile(String fileUrl) {
            this.fileUrl = fileUrl;
        }

        @Override
        public String getName() {
            return "file";
        }

        @Override
        public String getOriginalFilename() {
            // 从 URL 提取文件名
            if (fileUrl != null && fileUrl.contains("/")) {
                return fileUrl.substring(fileUrl.lastIndexOf("/") + 1);
            }
            return "uploaded_file";
        }

        @Override
        public String getContentType() {
            if (fileUrl != null) {
                if (fileUrl.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
                if (fileUrl.endsWith(".doc")) return "application/msword";
                if (fileUrl.endsWith(".pdf")) return "application/pdf";
            }
            return "application/octet-stream";
        }

        @Override
        public boolean isEmpty() {
            return false;
        }

        @Override
        public long getSize() {
            return 0;
        }

        @Override
        public byte[] getBytes() throws IOException {
            // 实际使用时需要从 URL 读取文件内容
            throw new IOException("UrlBackedMultipartFile 不支持直接读取字节，请使用文件路径");
        }

        @Override
        public void transferTo(java.io.File dest) throws IOException {
            // 实际使用时需要从 URL 读取文件内容并写入目标文件
            throw new IOException("UrlBackedMultipartFile 不支持直接传输，请使用文件路径");
        }

        @Override
        public java.io.InputStream getInputStream() throws IOException {
            // 实际使用时需要从 URL 读取文件内容
            throw new IOException("UrlBackedMultipartFile 不支持直接读取流，请使用文件路径");
        }
    }
}
