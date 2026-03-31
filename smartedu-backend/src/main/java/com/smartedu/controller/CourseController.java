package com.smartedu.controller;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.smartedu.common.result.Result;
import com.smartedu.entity.Course;
import com.smartedu.mapper.CourseMapper;
import com.smartedu.security.JwtAuthenticationToken;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 课程控制器
 * @author SmartEdu Team
 */
@Tag(name = "课程管理", description = "课程查询、管理相关接口")
@RestController
@RequestMapping("/course")
public class CourseController {

    private final CourseMapper courseMapper;

    public CourseController(CourseMapper courseMapper) {
        this.courseMapper = courseMapper;
    }

    @GetMapping("/list")
    @Operation(summary = "获取课程列表", description = "查询所有启用的课程（所有教师可见）")
    public Result<List<Course>> getCourseList() {
        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Course::getStatus, 1);
        queryWrapper.orderByDesc(Course::getCreatedAt);

        List<Course> courses = courseMapper.selectList(queryWrapper);
        return Result.success("获取成功", courses);
    }

    @GetMapping("/teacher")
    @Operation(summary = "获取教师的课程", description = "获取当前登录教师所教授的课程")
    public Result<List<Course>> getTeacherCourses(
            @AuthenticationPrincipal UserDetails userDetails) {

        Long teacherId = getUserId(userDetails);

        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Course::getTeacherId, teacherId);
        queryWrapper.eq(Course::getStatus, 1);
        queryWrapper.orderByDesc(Course::getCreatedAt);

        List<Course> courses = courseMapper.selectList(queryWrapper);
        return Result.success("获取成功", courses);
    }

    @GetMapping("/{id}")
    @Operation(summary = "获取课程详情")
    public Result<Course> getCourseDetail(@PathVariable Long id) {
        Course course = courseMapper.selectById(id);
        if (course == null) {
            return Result.error("课程不存在");
        }
        return Result.success("获取成功", course);
    }

    @PostMapping("/create")
    @Operation(summary = "创建课程", description = "教师创建新课程")
    public Result<Long> createCourse(
            @RequestBody CourseCreateRequest request,
            @AuthenticationPrincipal UserDetails userDetails) {

        Long teacherId = getUserId(userDetails);

        // 检查课程代码是否已存在
        LambdaQueryWrapper<Course> queryWrapper = new LambdaQueryWrapper<>();
        queryWrapper.eq(Course::getCourseCode, request.getCourseCode());
        if (courseMapper.selectCount(queryWrapper) > 0) {
            return Result.error("课程代码已存在");
        }

        Course course = new Course();
        course.setCourseName(request.getCourseName());
        course.setCourseCode(request.getCourseCode());
        course.setDescription(request.getDescription());
        course.setCredit(request.getCredit() != null ? request.getCredit() : new java.math.BigDecimal("3.0"));
        course.setTeacherId(teacherId);
        course.setSemester(request.getSemester());
        course.setGrade(request.getGrade());
        course.setMajor(request.getMajor());
        course.setStatus(1);

        courseMapper.insert(course);
        return Result.success("课程创建成功", course.getId());
    }

    private Long getUserId(UserDetails userDetails) {
        if (userDetails == null) {
            throw new RuntimeException("未登录");
        }
        org.springframework.security.core.Authentication authentication =
            org.springframework.security.core.context.SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken) {
            return ((JwtAuthenticationToken) authentication).getUserId();
        }
        throw new RuntimeException("用户认证信息异常");
    }

    /**
     * 课程创建请求 DTO
     */
    @lombok.Data
    public static class CourseCreateRequest {
        private String courseName;
        private String courseCode;
        private String description;
        private java.math.BigDecimal credit;
        private String semester;
        private String grade;
        private String major;
    }
}