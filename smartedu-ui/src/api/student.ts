import { get, post } from '@/utils/request'

// ==================== Dashboard 相关接口 ====================

// 获取学生 Dashboard 统计数据
export function getDashboardStats(userId: number) {
  return get('/student/learning/dashboard/stats', { userId })
}

// ==================== 知识图谱相关接口 ====================

// 获取课程知识图谱
export function getKnowledgeGraph(courseId: number) {
  return get('/student/learning/knowledge-graph', { courseId })
}

// ==================== 学习计划相关接口 ====================

// 生成个性化学习计划
export function generateLearningPlan(params: { courseId: number; targetScore?: number; days?: number }) {
  return post('/student/learning/plan/generate', null, { params })
}

// 获取学习计划列表
export function getLearningPlanList(status?: number) {
  return get('/student/learning/plan/list', { status })
}

// 获取学习计划详情
export function getLearningPlanDetail(id: number) {
  return get(`/student/learning/plan/${id}`)
}

// 更新学习计划进度
export function updateLearningPlanProgress(id: number, progress: number) {
  return put(`/student/learning/plan/${id}/progress`, null, { params: { progress } })
}

// ==================== 错题分析相关接口 ====================

// 获取错题分析报告
export function analyzeErrorQuestions(courseId?: number) {
  return get('/student/learning/error-analysis', { courseId })
}

// 获取错题列表
export function getErrorQuestions(params?: { courseId?: number; reviewStatus?: number; pageNum?: number; pageSize?: number }) {
  return get('/student/learning/error-questions', params)
}

// 标记错题为已复习
export function markErrorQuestionAsReviewed(id: number) {
  return post(`/student/learning/error-questions/${id}/review`)
}

// 获取推荐练习题
export function getRecommendQuestions(count?: number) {
  return get('/student/learning/recommend-questions', { count })
}

// ==================== 作业相关接口 ====================

// 获取作业列表
export function getHomeworkList(params?: { courseId?: number; status?: number; pageNum?: number; pageSize?: number }) {
  return get('/student/homework/list', params)
}

// 获取作业详情
export function getHomeworkDetail(id: number) {
  return get(`/student/homework/${id}`)
}

// 提交作业
export function submitHomework(data: { homeworkId: number; answers: { questionId: number; answer: string }[] }) {
  return post('/student/homework/submit', data)
}

// 获取我的提交记录
export function getMySubmission(homeworkId: number) {
  return get(`/student/homework/submission/${homeworkId}`)
}

// ==================== 问答相关接口 ====================

export const qaApi = {
  // 提问
  askQuestion: (data: any) => post('/qa/ask', data),
  // 获取问答列表
  getQAList: (params?: any) => get('/qa/list', params),
  // 获取问答详情
  getQADetail: (id: number) => get(`/qa/${id}`),
  // 回答问题 - 使用 URL 参数传递 content
  answerQuestion: (id: number, content: string) => post(`/qa/${id}/answer?content=${encodeURIComponent(content)}`),
  // 采纳答案
  adoptAnswer: (id: number) => post(`/qa/${id}/adopt`),
  // 获取我的提问
  getMyQuestions: (params?: any) => get('/qa/my-questions', params),
  // 获取热门问题
  getHotQuestions: (params?: any) => get('/qa/hot-questions', params),
  // 点赞
  likeQA: (id: number) => post(`/qa/${id}/like`)
}
