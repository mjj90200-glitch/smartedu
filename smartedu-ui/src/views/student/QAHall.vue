<template>
  <div class="qa-hall-container page-container">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="10" animated />
    </div>

    <template v-else>
      <el-card>
        <template #header>
          <div class="card-header">
            <span>虚拟答疑大厅</span>
            <el-button type="primary" @click="showAskDialog = true">
              <el-icon><Plus /></el-icon>
              我要提问
            </el-button>
          </div>
        </template>

        <!-- 搜索和筛选 -->
        <div class="filter-bar">
          <el-input
            v-model="searchKeyword"
            placeholder="搜索问题..."
            style="width: 300px"
            clearable
            @clear="searchQuestions"
          >
            <template #prefix>
              <el-icon><Search /></el-icon>
            </template>
          </el-input>

          <el-select v-model="filterCategory" placeholder="问题分类" style="width: 150px" clearable @change="loadQuestions">
            <el-option label="概念理解" value="CONCEPT" />
            <el-option label="习题答疑" value="EXERCISE" />
            <el-option label="其他" value="OTHER" />
          </el-select>

          <el-select v-model="filterStatus" placeholder="状态" style="width: 120px" clearable @change="loadQuestions">
            <el-option label="待回答" :value="0" />
            <el-option label="已回答" :value="1" />
            <el-option label="已采纳" :value="2" />
          </el-select>

          <el-button type="primary" @click="searchQuestions">
            <el-icon><Search /></el-icon>
            搜索
          </el-button>
        </div>

        <!-- 问题列表 -->
        <div class="question-list">
          <div
            v-for="item in questionList"
            :key="item.id"
            class="question-item"
            @click="viewDetail(item.id)"
          >
            <div class="question-header">
              <el-tag :type="getStatusType(item.status)" size="small">
                {{ getStatusText(item.status) }}
              </el-tag>
              <el-tag v-if="item.answerType === 'AI'" type="warning" size="small">
                <el-icon><ChatDotRound /></el-icon>
                AI 回答
              </el-tag>
              <el-tag v-else-if="item.answerType === 'TEACHER'" type="success" size="small">
                <el-icon><User /></el-icon>
                教师回答
              </el-tag>
            </div>
            <div class="question-title">{{ item.title }}</div>
            <div class="question-content">{{ item.content }}</div>
            <div class="question-meta">
              <div class="meta-left">
                <el-avatar :size="24" :src="item.userAvatar" />
                <span class="user-name">{{ item.isAnonymous ? '匿名用户' : item.userName }}</span>
                <span class="course-tag" v-if="item.courseName">{{ item.courseName }}</span>
              </div>
              <div class="meta-right">
                <span class="time">{{ item.createdAt }}</span>
                <span class="stat-item">
                  <el-icon><View /></el-icon>
                  {{ item.viewCount }}
                </span>
                <span class="stat-item">
                  <el-icon><Star /></el-icon>
                  {{ item.likeCount }}
                </span>
                <span class="stat-item">
                  <el-icon><ChatLineRound /></el-icon>
                  {{ item.answerCount }}
                </span>
              </div>
            </div>
          </div>

          <!-- 空状态 -->
          <el-empty v-if="!questionList.length" description="暂无问题" />
        </div>

        <!-- 分页 -->
        <div class="pagination-bar">
          <el-pagination
            v-model:current-page="currentPage"
            v-model:page-size="pageSize"
            :total="total"
            layout="total, prev, pager, next"
            @current-change="loadQuestions"
          />
        </div>
      </el-card>

      <!-- 提问对话框 -->
      <el-dialog
        v-model="showAskDialog"
        title="提问"
        width="600px"
      >
        <el-form :model="askForm" label-width="80px" ref="formRef">
          <el-form-item label="标题" required>
            <el-input v-model="askForm.title" placeholder="请输入问题标题" maxlength="200" show-word-limit />
          </el-form-item>
          <el-form-item label="分类" required>
            <el-radio-group v-model="askForm.category">
              <el-radio value="CONCEPT">概念理解</el-radio>
              <el-radio value="EXERCISE">习题答疑</el-radio>
              <el-radio value="OTHER">其他</el-radio>
            </el-radio-group>
          </el-form-item>
          <el-form-item label="课程">
            <el-select v-model="askForm.courseId" placeholder="选择课程（可选）" style="width: 100%" clearable>
              <el-option label="数据结构" :value="1" />
              <el-option label="Java 程序设计" :value="2" />
              <el-option label="数据库原理" :value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="内容" required>
            <el-input
              v-model="askForm.content"
              type="textarea"
              :rows="6"
              placeholder="请详细描述你的问题，包括相关知识点、你的思考过程等..."
            />
          </el-form-item>
          <el-form-item label="匿名">
            <el-switch v-model="askForm.isAnonymous" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showAskDialog = false">取消</el-button>
          <el-button type="primary" @click="handleAsk" :loading="asking">
            {{ asking ? '发布中...' : '发布问题' }}
          </el-button>
        </template>
      </el-dialog>

      <!-- 问题详情对话框 -->
      <el-dialog
        v-model="showDetailDialog"
        title="问题详情"
        width="800px"
        class="question-detail-dialog"
      >
        <div v-if="selectedQuestion" class="question-detail">
          <div class="detail-header">
            <div class="question-info">
              <h3>{{ selectedQuestion.title }}</h3>
              <div class="tags">
                <el-tag :type="getStatusType(selectedQuestion.status)" size="small">
                  {{ getStatusText(selectedQuestion.status) }}
                </el-tag>
                <el-tag v-if="selectedQuestion.category === 'CONCEPT'" type="info" size="small">概念理解</el-tag>
                <el-tag v-else-if="selectedQuestion.category === 'EXERCISE'" type="info" size="small">习题答疑</el-tag>
                <el-tag v-else type="info" size="small">其他</el-tag>
                <el-tag v-if="selectedQuestion.courseName" type="primary" size="small">{{ selectedQuestion.courseName }}</el-tag>
              </div>
            </div>
          </div>

          <div class="question-content-section">
            <div class="author-info">
              <el-avatar :size="40" :src="selectedQuestion.userAvatar" />
              <div class="author-detail">
                <span class="name">{{ selectedQuestion.isAnonymous ? '匿名用户' : selectedQuestion.userName }}</span>
                <span class="time">{{ selectedQuestion.createdAt }}</span>
              </div>
            </div>
            <div class="content">{{ selectedQuestion.content }}</div>
          </div>

          <el-divider />

          <!-- 回答列表 -->
          <div class="answers-section">
            <h4>回答（{{ answers.length }}）</h4>
            <div v-for="answer in answers" :key="answer.id" class="answer-item">
              <div class="answer-header">
                <el-avatar :size="36" :src="answer.userAvatar" />
                <span class="name">{{ answer.userName }}</span>
                <el-tag v-if="answer.answerType === 'AI'" type="warning" size="small">AI</el-tag>
                <el-tag v-else-if="answer.answerType === 'TEACHER'" type="success" size="small">教师</el-tag>
                <span class="time">{{ answer.createdAt }}</span>
              </div>
              <div class="answer-content">{{ answer.content }}</div>
              <div class="answer-footer">
                <el-button text size="small" @click="likeAnswer(answer)">
                  <el-icon><Goods /></el-icon>
                  {{ answer.likeCount }}
                </el-button>
                <el-button
                  v-if="!answer.isAdopted && selectedQuestion.userId === currentUserId"
                  text
                  size="small"
                  type="success"
                  @click="adoptAnswer(answer)"
                >
                  采纳
                </el-button>
                <el-tag v-if="answer.isAdopted" type="success" size="small">已采纳</el-tag>
              </div>
            </div>
            <el-empty v-if="!answers.length" description="暂无回答" />
          </div>

          <!-- 回答输入框 -->
          <el-divider />
          <div class="answer-input-section">
            <el-input
              v-model="newAnswer"
              type="textarea"
              :rows="3"
              placeholder="写下你的回答..."
            />
            <div class="answer-actions">
              <el-button type="primary" @click="submitAnswer" :loading="answering">发布回答</el-button>
            </div>
          </div>
        </div>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Plus, Search, ChatDotRound, View, Star, ChatLineRound, User, Goods } from '@element-plus/icons-vue'
import { qaApi } from '@/api/student'
import type { QAQuestion, QAAnswer } from '@/types/student'

const loading = ref(true)
const showAskDialog = ref(false)
const showDetailDialog = ref(false)
const asking = ref(false)
const answering = ref(false)
const searchKeyword = ref('')
const filterCategory = ref('')
const filterStatus = ref<number | ''>('')
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(50)
const selectedQuestion = ref<QAQuestion | null>(null)
const newAnswer = ref('')
const currentUserId = ref(1) // 当前登录用户 ID

const formRef = ref<FormInstance>()

const questionList = ref<QAQuestion[]>([])
const answers = ref<QAAnswer[]>([])

const askForm = reactive({
  title: '',
  category: 'CONCEPT',
  courseId: null as number | null,
  content: '',
  isAnonymous: false
})

const getStatusType = (status: number) => {
  const types: Record<number, any> = { 0: 'warning', 1: 'info', 2: 'success' }
  return types[status] || 'info'
}

const getStatusText = (status: number) => {
  const texts: Record<number, string> = { 0: '待回答', 1: '已回答', 2: '已采纳' }
  return texts[status] || '未知'
}

// 格式化日期
const formatDate = (date: string | null) => {
  if (!date) return ''
  const d = new Date(date)
  const now = new Date()
  const diff = now.getTime() - d.getTime()

  // 1 分钟内
  if (diff < 60000) return '刚刚'
  // 1 小时内
  if (diff < 3600000) return `${Math.floor(diff / 60000)}分钟前`
  // 24 小时内
  if (diff < 86400000) return `${Math.floor(diff / 3600000)}小时前`
  // 7 天内
  if (diff < 604800000) return `${Math.floor(diff / 86400000)}天前`

  // 超过 7 天显示具体日期
  return d.toLocaleDateString('zh-CN', { year: 'numeric', month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

// 加载问题列表
const loadQuestions = async () => {
  loading.value = true
  try {
    const res = await qaApi.getQAList({
      courseId: filterCategory.value ? Number(filterCategory.value) : undefined,
      status: filterStatus.value !== '' ? Number(filterStatus.value) : undefined,
      pageNum: currentPage.value,
      pageSize: pageSize.value
    })

    console.log('[问答大厅] API 响应:', res)

    if (res.code === 200 && res.data) {
      questionList.value = res.data.records?.map((item: any) => ({
        id: item.id,
        title: item.title,
        content: item.content,
        category: item.category,
        courseId: item.courseId,
        courseName: item.courseName || '未知课程',
        userId: item.userId,
        userName: item.userName || '匿名',
        userAvatar: item.userAvatar || '',
        isAnonymous: item.isAnonymous === 1,
        status: item.status,
        answerType: item.answerType,
        viewCount: item.viewCount || 0,
        likeCount: item.likeCount || 0,
        answerCount: item.answer ? 1 : 0,
        createdAt: formatDate(item.createdAt)
      })) || []
      total.value = res.data.total || 0
      console.log('[问答大厅] 加载问题列表成功:', questionList.value.length, '条')
    } else {
      console.warn('[问答大厅] API 响应异常:', res)
      ElMessage.warning(res.message || '加载失败')
    }
  } catch (e) {
    console.error('[问答大厅] 加载问题列表失败:', e)
    ElMessage.error('加载失败，请检查后端服务')
  } finally {
    loading.value = false
  }
}

// 搜索问题
const searchQuestions = () => {
  currentPage.value = 1
  loadQuestions()
}

// 查看详情
const viewDetail = async (id: number) => {
  console.log('[问答大厅] 查看详情，ID:', id)
  try {
    const res = await qaApi.getQADetail(id)
    console.log('[问答大厅] API 响应:', res)

    if (res.code === 200 && res.data) {
      const item = res.data
      selectedQuestion.value = {
        id: item.id,
        title: item.title,
        content: item.content,
        category: item.category,
        courseId: item.courseId,
        courseName: item.courseName || '未知课程',
        userId: item.userId,
        userName: item.userName || '匿名',
        userAvatar: item.userAvatar || '',
        isAnonymous: item.isAnonymous === 1,
        status: item.status,
        answerType: item.answerType,
        viewCount: item.viewCount || 0,
        likeCount: item.likeCount || 0,
        answerCount: item.answer ? 1 : 0,
        createdAt: formatDate(item.createdAt)
      }

      // 构建回答列表
      answers.value = []
      if (item.answer) {
        answers.value.push({
          id: item.id,
          questionId: item.id,
          content: item.answer,
          answerType: item.answerType || 'TEACHER',
          userId: item.answerUserId || 0,
          userName: item.answerUserName || '老师',
          userAvatar: item.answerUserAvatar || '',
          isAdopted: item.status === 2,
          likeCount: item.likeCount || 0,
          createdAt: formatDate(item.updatedAt)
        })
      }
      console.log('[问答大厅] 打开详情对话框')
      showDetailDialog.value = true
    } else {
      ElMessage.error(res.message || '获取问题详情失败')
    }
  } catch (e) {
    console.error('[问答大厅] 获取问题详情失败:', e)
    ElMessage.error('获取问题详情失败')
  }
}

// 提交问题
const handleAsk = async () => {
  if (!askForm.title || !askForm.content) {
    ElMessage.warning('请填写标题和内容')
    return
  }

  asking.value = true
  try {
    await qaApi.askQuestion({
      title: askForm.title,
      content: askForm.content,
      courseId: askForm.courseId ? Number(askForm.courseId) : undefined,
      category: askForm.category || 'OTHER',
      isAnonymous: askForm.isAnonymous
    })

    ElMessage.success('提问成功，等待回答')
    showAskDialog.value = false
    loadQuestions()
    // 重置表单
    askForm.value = {
      title: '',
      content: '',
      courseId: undefined,
      category: 'CONCEPT',
      isAnonymous: false
    }
  } catch (e) {
    console.error('发布问题失败:', e)
    ElMessage.error('发布失败，请重试')
  } finally {
    asking.value = false
  }
}

// 提交回答
const submitAnswer = async () => {
  if (!newAnswer.value.trim()) {
    ElMessage.warning('请输入回答内容')
    return
  }

  answering.value = true
  try {
    // 获取用户信息
    const userStoreJson = localStorage.getItem('smartedu_user')
    let userInfo = null
    if (userStoreJson) {
      const parsed = JSON.parse(userStoreJson)
      userInfo = parsed.value?.userInfo || parsed.userInfo || null
    }

    if (!userInfo || !selectedQuestion.value) {
      ElMessage.error('用户信息异常')
      return
    }

    // 调用 API 提交回答
    await qaApi.answerQuestion(selectedQuestion.value.id, newAnswer.value)

    // 更新本地回答列表
    answers.value.unshift({
      id: Date.now(),
      questionId: selectedQuestion.value.id,
      content: newAnswer.value,
      answerType: userInfo.role === 'TEACHER' ? 'TEACHER' : 'STUDENT',
      userId: userInfo.id,
      userName: userInfo.realName || '我',
      userAvatar: userInfo.avatar || '',
      isAdopted: false,
      likeCount: 0,
      createdAt: '刚刚'
    })

    newAnswer.value = ''
    ElMessage.success('回答成功')
  } catch (e) {
    console.error('提交回答失败:', e)
    ElMessage.error('提交失败，请重试')
  } finally {
    answering.value = false
  }
}

// 点赞回答
const likeAnswer = (answer: QAAnswer) => {
  answer.likeCount++
  ElMessage.success('已点赞')
}

// 采纳回答
const adoptAnswer = (answer: QAAnswer) => {
  ElMessageBox.confirm('确定采纳该回答吗？', '提示', {
    type: 'success'
  }).then(() => {
    answer.isAdopted = true
    if (selectedQuestion.value) {
      selectedQuestion.value.status = 2
      selectedQuestion.value.answerType = answer.answerType
    }
    ElMessage.success('已采纳')
  }).catch(() => {})
}

onMounted(() => {
  loadQuestions()
})
</script>

<style scoped lang="scss">
.qa-hall-container {
  .loading-wrapper {
    padding: 20px;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .filter-bar {
    display: flex;
    gap: 15px;
    margin-bottom: 20px;
  }

  .question-list {
    .question-item {
      padding: 16px;
      border: 1px solid #ebeef5;
      border-radius: 8px;
      margin-bottom: 15px;
      cursor: pointer;
      transition: box-shadow 0.3s;

      &:hover {
        box-shadow: 0 2px 12px rgba(0, 0, 0, 0.1);
      }

      .question-header {
        display: flex;
        gap: 10px;
        margin-bottom: 10px;
      }

      .question-title {
        font-size: 16px;
        font-weight: 500;
        color: #303133;
        margin-bottom: 8px;
      }

      .question-content {
        font-size: 14px;
        color: #606266;
        margin-bottom: 12px;
        display: -webkit-box;
        -webkit-line-clamp: 2;
        -webkit-box-orient: vertical;
        overflow: hidden;
      }

      .question-meta {
        display: flex;
        justify-content: space-between;
        align-items: center;
        font-size: 13px;
        color: #909399;

        .meta-left {
          display: flex;
          align-items: center;
          gap: 8px;

          .user-name {
            color: #606266;
          }

          .course-tag {
            background-color: #ecf5ff;
            color: #409eff;
            padding: 2px 8px;
            border-radius: 4px;
            font-size: 12px;
          }
        }

        .meta-right {
          display: flex;
          align-items: center;
          gap: 16px;

          .time {
            color: #909399;
          }

          .stat-item {
            display: flex;
            align-items: center;
            gap: 4px;
          }
        }
      }
    }
  }

  .pagination-bar {
    display: flex;
    justify-content: center;
    margin-top: 20px;
  }
}

.question-detail-dialog {
  .question-detail {
    .detail-header {
      margin-bottom: 16px;

      .question-info {
        h3 {
          margin: 0 0 12px 0;
          font-size: 18px;
          color: #303133;
        }

        .tags {
          display: flex;
          gap: 8px;
        }
      }
    }

    .question-content-section {
      padding: 16px;
      background-color: #f5f7fa;
      border-radius: 8px;

      .author-info {
        display: flex;
        align-items: center;
        gap: 12px;
        margin-bottom: 12px;

        .author-detail {
          display: flex;
          flex-direction: column;
          gap: 4px;

          .name {
            font-size: 14px;
            color: #303133;
            font-weight: 500;
          }

          .time {
            font-size: 12px;
            color: #909399;
          }
        }
      }

      .content {
        font-size: 14px;
        color: #303133;
        line-height: 1.8;
        white-space: pre-wrap;
      }
    }

    .answers-section {
      h4 {
        font-size: 16px;
        color: #303133;
        margin: 16px 0;
      }

      .answer-item {
        padding: 16px;
        border: 1px solid #ebeef5;
        border-radius: 8px;
        margin-bottom: 12px;

        .answer-header {
          display: flex;
          align-items: center;
          gap: 10px;
          margin-bottom: 12px;

          .name {
            font-size: 14px;
            color: #303133;
            font-weight: 500;
          }

          .time {
            font-size: 12px;
            color: #909399;
            margin-left: auto;
          }
        }

        .answer-content {
          font-size: 14px;
          color: #303133;
          line-height: 1.8;
          margin-bottom: 12px;
          white-space: pre-wrap;
        }

        .answer-footer {
          display: flex;
          gap: 10px;
        }
      }
    }

    .answer-input-section {
      .answer-actions {
        display: flex;
        justify-content: flex-end;
        margin-top: 10px;
      }
    }
  }
}
</style>
