package com.smartedu.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.smartedu.common.exception.BusinessException;
import com.smartedu.dto.HomeworkDTO;
import com.smartedu.entity.*;
import com.smartedu.mapper.*;
import com.smartedu.vo.HomeworkDetailVO;
import com.smartedu.vo.HomeworkStatisticsVO;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * 作业服务类 - 简化版
 * @author SmartEdu Team
 */
@Service
public class HomeworkService extends ServiceImpl<HomeworkMapper, Homework> {

    private final HomeworkSubmissionMapper submissionMapper;
    private final CourseMapper courseMapper;
    private final UserMapper userMapper;
    private final AIGradeService aiGradeService;
    private final HomeworkAnalysisService homeworkAnalysisService;

    public HomeworkService(
            HomeworkSubmissionMapper submissionMapper,
            CourseMapper courseMapper,
            UserMapper userMapper,
            AIGradeService aiGradeService,
            HomeworkAnalysisService homeworkAnalysisService) {
        this.submissionMapper = submissionMapper;
        this.courseMapper = courseMapper;
        this.userMapper = userMapper;
        this.aiGradeService = aiGradeService;
        this.homeworkAnalysisService = homeworkAnalysisService;
    }

    /**
     * 创建作业
     */
    @Transactional
    public Long createHomework(HomeworkDTO dto, Long teacherId) {
        BigDecimal totalScore = dto.getTotalScore();
        if (totalScore == null || totalScore.compareTo(BigDecimal.ZERO) == 0) {
            totalScore = new BigDecimal("100");
        }

        Homework homework = new Homework();
        homework.setTitle(dto.getTitle());
        homework.setDescription(dto.getDescription() != null ? dto.getDescription() : "");
        homework.setCourseId(dto.getCourseId());
        homework.setTeacherId(teacherId);
        homework.setTotalScore(totalScore);
        homework.setPassScore(dto.getPassScore() != null ? dto.getPassScore() : new BigDecimal("60"));
        homework.setStartTime(dto.getStartTime() != null ? dto.getStartTime() : LocalDateTime.now());
        homework.setEndTime(dto.getEndTime() != null ? dto.getEndTime() : LocalDateTime.now().plusDays(7));
        homework.setSubmitLimit(dto.getSubmitLimit() != null ? dto.getSubmitLimit() : 0);
        homework.setStatus(0);
        homework.setAutoGrade(dto.getAutoGrade() != null ? dto.getAutoGrade() : 1);

        baseMapper.insert(homework);
        return homework.getId();
    }

    /**
     * 更新作业
     */
    @Transactional
    public void updateHomework(Long id, HomeworkDTO dto, Long teacherId) {
        Homework homework = baseMapper.selectById(id);
        if (homework == null) {
            throw new BusinessException("作业不存在");
        }

        if (homework.getStatus() != 0) {
            throw new BusinessException("只有草稿状态的作业才能编辑");
        }

        if (!homework.getTeacherId().equals(teacherId)) {
            throw new BusinessException("无权限修改此作业");
        }

        BeanUtils.copyProperties(homework, dto);
        baseMapper.updateById(homework);
    }

    /**
     * 删除作业（级联删除关联的提交记录）
     */
    @Transactional
    public void deleteHomework(Long id, Long teacherId) {
        Homework homework = baseMapper.selectById(id);
        if (homework == null) {
            throw new BusinessException("作业不存在");
        }

        if (!homework.getTeacherId().equals(teacherId)) {
            throw new BusinessException("无权限删除此作业");
        }

        // 先删除关联的学生提交记录
        LambdaQueryWrapper<HomeworkSubmission> submissionWrapper = new LambdaQueryWrapper<>();
        submissionWrapper.eq(HomeworkSubmission::getHomeworkId, id);
        int submissionCount = submissionMapper.delete(submissionWrapper);

        org.slf4j.LoggerFactory.getLogger(HomeworkService.class)
            .info("删除作业 {} 关联的 {} 条提交记录", id, submissionCount);

        // 再删除作业本身
        baseMapper.deleteById(id);
    }

    /**
     * 分页查询作业列表
     */
    public Page<HomeworkDetailVO> getHomeworkList(Long teacherId, Long courseId, Integer status,
                                                    Integer pageNum, Integer pageSize) {
        Page<Homework> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<Homework> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Homework::getTeacherId, teacherId);
        queryWrapper.eq(courseId != null, Homework::getCourseId, courseId);
        queryWrapper.eq(status != null, Homework::getStatus, status);
        queryWrapper.orderByDesc(Homework::getCreatedAt);

        Page<Homework> homeworkPage = baseMapper.selectPage(page, queryWrapper);

        Page<HomeworkDetailVO> voPage = new Page<>(pageNum, pageSize, homeworkPage.getTotal());
        voPage.setRecords(homeworkPage.getRecords().stream()
                .map(this::convertToDetailVO)
                .collect(Collectors.toList()));

        return voPage;
    }

    /**
     * 获取作业详情
     */
    public HomeworkDetailVO getHomeworkDetail(Long id) {
        Homework homework = baseMapper.selectById(id);
        if (homework == null) {
            return null;
        }
        return convertToDetailVO(homework);
    }

    /**
     * 发布作业
     */
    @Transactional
    public void publishHomework(Long id, Long teacherId) {
        Homework homework = baseMapper.selectById(id);
        if (homework == null) {
            throw new BusinessException("作业不存在");
        }

        if (!homework.getTeacherId().equals(teacherId)) {
            throw new BusinessException("无权限发布此作业");
        }

        homework.setStatus(1);
        baseMapper.updateById(homework);

        // 异步触发 AI 解析生成
        try {
            homeworkAnalysisService.generateAnalysisAsync(id);
            org.slf4j.LoggerFactory.getLogger(HomeworkService.class)
                .info("已触发 AI 解析生成任务，homeworkId={}", id);
        } catch (Exception e) {
            // 解析生成失败不影响作业发布
            org.slf4j.LoggerFactory.getLogger(HomeworkService.class)
                .error("触发 AI 解析生成失败，homeworkId={}, error={}", id, e.getMessage());
        }
    }

    /**
     * 获取作业提交列表
     */
    public Page<Map<String, Object>> getSubmissions(Long homeworkId, Integer gradeStatus,
                                                     Integer pageNum, Integer pageSize) {
        Page<HomeworkSubmission> page = new Page<>(pageNum, pageSize);

        LambdaQueryWrapper<HomeworkSubmission> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(HomeworkSubmission::getHomeworkId, homeworkId);
        queryWrapper.eq(gradeStatus != null, HomeworkSubmission::getGradeStatus, gradeStatus);
        queryWrapper.orderByDesc(HomeworkSubmission::getSubmitTime);

        Page<HomeworkSubmission> submissionPage = submissionMapper.selectPage(page, queryWrapper);

        Page<Map<String, Object>> resultPage = new Page<>(pageNum, pageSize, submissionPage.getTotal());
        List<Map<String, Object>> records = submissionPage.getRecords().stream()
                .map(submission -> {
                    Map<String, Object> map = new HashMap<>();
                    map.put("id", submission.getId());
                    map.put("homeworkId", submission.getHomeworkId());
                    map.put("userId", submission.getUserId());
                    map.put("studentName", submission.getStudentName());
                    map.put("submissionContent", submission.getSubmissionContent());
                    map.put("submissionType", submission.getSubmissionType());
                    map.put("attachmentUrl", submission.getAttachmentUrl());
                    map.put("attachmentName", submission.getAttachmentName());
                    map.put("score", submission.getScore());
                    map.put("aiScore", submission.getAiScore());
                    map.put("aiFeedback", submission.getAiFeedback());
                    map.put("comment", submission.getComment());
                    map.put("gradeStatus", submission.getGradeStatus());
                    map.put("submitTime", submission.getSubmitTime());
                    map.put("isLate", submission.getIsLate());

                    User student = userMapper.selectById(submission.getUserId());
                    if (student != null) {
                        map.put("studentName", student.getRealName());
                        map.put("studentUsername", student.getUsername());
                    }

                    return map;
                })
                .collect(Collectors.toList());

        resultPage.setRecords(records);
        return resultPage;
    }

    /**
     * 获取作业统计信息
     */
    public HomeworkStatisticsVO getHomeworkStatistics(Long homeworkId) {
        Homework homework = baseMapper.selectById(homeworkId);
        if (homework == null) {
            return null;
        }

        HomeworkStatisticsVO vo = new HomeworkStatisticsVO();
        vo.setId(homeworkId);
        vo.setTitle(homework.getTitle());

        LambdaQueryWrapper<HomeworkSubmission> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(HomeworkSubmission::getHomeworkId, homeworkId);
        List<HomeworkSubmission> submissions = submissionMapper.selectList(queryWrapper);

        int submittedCount = submissions.size();
        int gradedCount = (int) submissions.stream()
                .filter(s -> s.getGradeStatus() == 2)
                .count();

        vo.setSubmittedCount(submittedCount);
        vo.setGradedCount(gradedCount);
        vo.setTotalStudents(45);

        if (vo.getTotalStudents() > 0) {
            vo.setSubmissionRate((double) submittedCount / vo.getTotalStudents() * 100);
        }

        List<HomeworkSubmission> gradedSubmissions = submissions.stream()
                .filter(s -> s.getScore() != null)
                .collect(Collectors.toList());

        if (!gradedSubmissions.isEmpty()) {
            BigDecimal avgScore = gradedSubmissions.stream()
                    .map(HomeworkSubmission::getScore)
                    .reduce(BigDecimal.ZERO, BigDecimal::add)
                    .divide(BigDecimal.valueOf(gradedSubmissions.size()), 2, RoundingMode.HALF_UP);
            vo.setAverageScore(avgScore);

            vo.setMaxScore(gradedSubmissions.stream()
                    .map(HomeworkSubmission::getScore)
                    .max(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO));
            vo.setMinScore(gradedSubmissions.stream()
                    .map(HomeworkSubmission::getScore)
                    .min(BigDecimal::compareTo)
                    .orElse(BigDecimal.ZERO));

            BigDecimal passScore = homework.getPassScore() != null ? homework.getPassScore() : new BigDecimal("60");
            int passCount = (int) gradedSubmissions.stream()
                    .filter(s -> s.getScore().compareTo(passScore) >= 0)
                    .count();
            int excellentCount = (int) gradedSubmissions.stream()
                    .filter(s -> s.getScore().compareTo(new BigDecimal("90")) >= 0)
                    .count();

            vo.setPassRate((double) passCount / gradedSubmissions.size() * 100);
            vo.setExcellentRate((double) excellentCount / gradedSubmissions.size() * 100);

            Map<String, Integer> distribution = new LinkedHashMap<>();
            distribution.put("90-100", 0);
            distribution.put("80-89", 0);
            distribution.put("70-79", 0);
            distribution.put("60-69", 0);
            distribution.put("0-59", 0);

            for (HomeworkSubmission s : gradedSubmissions) {
                int score = s.getScore().intValue();
                if (score >= 90) distribution.merge("90-100", 1, Integer::sum);
                else if (score >= 80) distribution.merge("80-89", 1, Integer::sum);
                else if (score >= 70) distribution.merge("70-79", 1, Integer::sum);
                else if (score >= 60) distribution.merge("60-69", 1, Integer::sum);
                else distribution.merge("0-59", 1, Integer::sum);
            }
            vo.setScoreDistribution(distribution);
        }

        int lateCount = (int) submissions.stream()
                .filter(s -> s.getIsLate() != null && s.getIsLate() == 1)
                .count();
        vo.setLateSubmissionCount(lateCount);

        return vo;
    }

    /**
     * 自动批改作业 - 简化版（AI 批改）
     */
    @Transactional
    public Map<String, Object> autoGradeHomework(Long homeworkId, Long teacherId) {
        Homework homework = baseMapper.selectById(homeworkId);
        if (homework == null) {
            throw new BusinessException("作业不存在");
        }

        if (!homework.getTeacherId().equals(teacherId)) {
            throw new BusinessException("无权限批改此作业");
        }

        LambdaQueryWrapper<HomeworkSubmission> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(HomeworkSubmission::getHomeworkId, homeworkId);
        queryWrapper.eq(HomeworkSubmission::getGradeStatus, 1);
        List<HomeworkSubmission> submissions = submissionMapper.selectList(queryWrapper);

        if (submissions.isEmpty()) {
            Map<String, Object> result = new HashMap<>();
            result.put("gradedCount", 0);
            result.put("message", "没有需要批改的提交");
            return result;
        }

        int gradedCount = 0;

        for (HomeworkSubmission submission : submissions) {
            try {
                Map<String, Object> aiResult = aiGradeService.gradeHomework(
                    homework.getTitle(),
                    homework.getDescription(),
                    submission.getSubmissionContent(),
                    submission.getSubmissionType(),
                    homework.getTotalScore().doubleValue()
                );

                Double aiScore = (Double) aiResult.get("score");
                String aiFeedback = (String) aiResult.get("feedback");

                submission.setScore(BigDecimal.valueOf(aiScore != null ? aiScore : 0));
                submission.setAiScore(BigDecimal.valueOf(aiScore != null ? aiScore : 0));
                submission.setAiFeedback(aiFeedback);
                submission.setGradeStatus(2);
                submission.setGradeTime(LocalDateTime.now());
                submission.setGradeUserId(teacherId);
                submissionMapper.updateById(submission);

                gradedCount++;

            } catch (Exception e) {
                org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(HomeworkService.class);
                logger.error("批改作业失败：submissionId={}", submission.getId(), e);
            }
        }

        Map<String, Object> result = new HashMap<>();
        result.put("gradedCount", gradedCount);
        result.put("message", "成功批改 " + gradedCount + " 份作业");
        return result;
    }

    /**
     * 批改单个提交
     */
    @Transactional
    public void gradeSingleSubmission(Long submissionId, Double score, String comment, Long teacherId) {
        HomeworkSubmission submission = submissionMapper.selectById(submissionId);
        if (submission == null) {
            throw new BusinessException("提交记录不存在");
        }

        submission.setScore(BigDecimal.valueOf(score));
        if (comment != null) {
            submission.setComment(comment);
        }
        submission.setGradeStatus(2);
        submission.setGradeTime(LocalDateTime.now());
        submission.setGradeUserId(teacherId);
        submissionMapper.updateById(submission);
    }

    /**
     * 检查并更新作业状态
     */
    private void checkAndUpdateHomeworkStatus(Long homeworkId) {
        LambdaQueryWrapper<HomeworkSubmission> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(HomeworkSubmission::getHomeworkId, homeworkId);
        long totalCount = submissionMapper.selectCount(queryWrapper);

        queryWrapper.eq(HomeworkSubmission::getGradeStatus, 2);
        long gradedCount = submissionMapper.selectCount(queryWrapper);

        if (totalCount > 0 && totalCount == gradedCount) {
            Homework homework = baseMapper.selectById(homeworkId);
            if (homework != null && homework.getStatus() == 1) {
                homework.setStatus(3);
                baseMapper.updateById(homework);
            }
        }
    }

    /**
     * 转换为详情 VO
     */
    private HomeworkDetailVO convertToDetailVO(Homework homework) {
        HomeworkDetailVO vo = new HomeworkDetailVO();
        BeanUtils.copyProperties(homework, vo);

        Course course = courseMapper.selectById(homework.getCourseId());
        if (course != null) {
            vo.setCourseName(course.getCourseName());
        }

        User teacher = userMapper.selectById(homework.getTeacherId());
        if (teacher != null) {
            vo.setTeacherName(teacher.getRealName());
        }

        // 教师端：返回 AI 解析相关字段
        vo.setAiAnalysisStatus(homework.getAiAnalysisStatus());
        vo.setAiAnalysisContent(homework.getAiAnalysisContent());
        vo.setTeacherEditedAnalysis(homework.getTeacherEditedAnalysis());

        return vo;
    }
}
