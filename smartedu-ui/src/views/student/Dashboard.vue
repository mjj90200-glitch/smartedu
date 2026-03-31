<template>
  <div class="dashboard-container page-container">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="10" animated />
    </div>

    <template v-else>
      <!-- 学习概览 -->
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon" style="background-color: #e3f2fd;">
              <el-icon :size="32" color="#2196f3"><Document /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ stats?.ongoingHomeworkCount || 0 }}</div>
              <div class="stat-label">进行中作业</div>
            </div>
          </el-card>
        </el-col>

        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon" style="background-color: #fff3e0;">
              <el-icon :size="32" color="#ff9800"><Clock /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ stats?.weeklyStudyHours || 0 }}h</div>
              <div class="stat-label">本周学习时长</div>
            </div>
          </el-card>
        </el-col>

        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon" style="background-color: #f3e5f5;">
              <el-icon :size="32" color="#9c27b0"><CircleClose /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ stats?.pendingReviewCount || 0 }}</div>
              <div class="stat-label">待复习错题</div>
            </div>
          </el-card>
        </el-col>

        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-icon" style="background-color: #e8f5e9;">
              <el-icon :size="32" color="#4caf50"><CircleCheck /></el-icon>
            </div>
            <div class="stat-content">
              <div class="stat-value">{{ Math.round(stats?.averageAccuracy || 0) }}%</div>
              <div class="stat-label">平均正确率</div>
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 知识图谱和待办事项 -->
      <el-row :gutter="20" class="mt-20">
        <el-col :span="16">
          <el-card>
            <template #header>
              <div class="card-header">
                <span>我的知识图谱</span>
                <el-button type="primary" text @click="router.push('/student/knowledge-graph')">
                  查看详情
                </el-button>
              </div>
            </template>
            <div class="knowledge-graph-preview">
              <el-row :gutter="16" align="middle">
                <el-col :span="12">
                  <div class="stat-item">
                    <div class="stat-item-label">总知识点数</div>
                    <div class="stat-item-value">{{ stats?.knowledgeGraphPreview?.totalKnowledgePoints || 0 }}</div>
                  </div>
                </el-col>
                <el-col :span="12">
                  <div class="stat-item">
                    <div class="stat-item-label">已掌握</div>
                    <div class="stat-item-value mastered">{{ stats?.knowledgeGraphPreview?.masteredCount || 0 }}</div>
                  </div>
                </el-col>
              </el-row>
              <el-row :gutter="16" align="middle" class="mt-16">
                <el-col :span="12">
                  <div class="stat-item">
                    <div class="stat-item-label">学习中</div>
                    <div class="stat-item-value learning">{{ stats?.knowledgeGraphPreview?.learningCount || 0 }}</div>
                  </div>
                </el-col>
                <el-col :span="12">
                  <div class="stat-item">
                    <div class="stat-item-label">待学习</div>
                    <div class="stat-item-value not-started">{{ stats?.knowledgeGraphPreview?.notStartedCount || 0 }}</div>
                  </div>
                </el-col>
              </el-row>
              <el-divider />
              <div class="weak-points">
                <div class="section-title">薄弱知识点</div>
                <div v-for="item in stats?.knowledgeGraphPreview?.weakPoints" :key="item.id" class="weak-point-item">
                  <span class="point-name">{{ item.name }}</span>
                  <el-progress :percentage="item.mastery" :color="getProgressColor(item.mastery)" :stroke-width="6" />
                </div>
                <div v-if="!stats?.knowledgeGraphPreview?.weakPoints?.length" class="empty-weak-points">
                  <el-empty description="暂无薄弱知识点" :image-size="60" />
                </div>
              </div>
            </div>
          </el-card>
        </el-col>

        <el-col :span="8">
          <el-card>
            <template #header>
              <div class="card-header">
                <span>待办事项</span>
                <el-button type="primary" text @click="refreshData">
                  <el-icon><Refresh /></el-icon>
                </el-button>
              </div>
            </template>
            <el-timeline>
              <el-timeline-item
                v-for="item in stats?.todoItems"
                :key="item.id"
                :timestamp="formatDeadline(item.deadline)"
                :type="getPriorityType(item.priority)"
                :hollow="item.completed"
              >
                <div :class="['todo-item', { completed: item.completed }]">
                  <span class="todo-title">{{ item.title }}</span>
                  <el-tag v-if="item.priority === 'HIGH'" type="danger" size="small">紧急</el-tag>
                  <el-tag v-else-if="item.priority === 'MEDIUM'" type="warning" size="small">重要</el-tag>
                </div>
              </el-timeline-item>
              <el-timeline-item v-if="!stats?.todoItems?.length" type="info">
                <el-empty description="暂无待办事项" :image-size="60" />
              </el-timeline-item>
            </el-timeline>
          </el-card>
        </el-col>
      </el-row>

      <!-- 学习建议 -->
      <el-row :gutter="20" class="mt-20">
        <el-col :span="24">
          <el-card>
            <template #header>
              <div class="card-header">
                <span>AI 学习建议</span>
                <el-button type="primary" text @click="refreshData">
                  <el-icon><Refresh /></el-icon>
                  换一批
                </el-button>
              </div>
            </template>
            <el-alert
              v-for="(item, index) in stats?.suggestions"
              :key="index"
              :title="item.title"
              :type="getAlertType(item.priority)"
              :closable="false"
              show-icon
              class="mb-10"
            >
              <template #default>
                {{ item.content }}
              </template>
            </el-alert>
            <div v-if="!stats?.suggestions?.length" class="empty-suggestions">
              <el-empty description="暂无学习建议" :image-size="60" />
            </div>
          </el-card>
        </el-col>
      </el-row>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, onMounted, computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  Document,
  Clock,
  CircleClose,
  CircleCheck,
  Refresh
} from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { useUserStore } from '@/store/modules/user'
import { getDashboardStats } from '@/api/student'
import type { DashboardStats, LearningSuggestion } from '@/types/student'

const router = useRouter()
const userStore = useUserStore()

const loading = ref(true)
const stats = ref<DashboardStats | null>(null)

// 获取进度条颜色
const getProgressColor = (percentage: number) => {
  if (percentage < 50) return '#f56c6c'
  if (percentage < 70) return '#e6a23c'
  return '#67c23a'
}

// 获取优先级类型
const getPriorityType = (priority: string) => {
  const types: Record<string, any> = { HIGH: 'danger', MEDIUM: 'warning', LOW: 'info' }
  return types[priority] || 'info'
}

// 获取提醒类型
const getAlertType = (priority: string) => {
  const types: Record<string, any> = { HIGH: 'error', MEDIUM: 'warning', LOW: 'info' }
  return types[priority] || 'info'
}

// 格式化截止时间
const formatDeadline = (deadline: string) => {
  const date = new Date(deadline)
  const now = new Date()
  const diff = date.getTime() - now.getTime()
  const days = Math.floor(diff / (1000 * 60 * 60 * 24))

  if (days < 0) return '已过期'
  if (days === 0) return '今天 ' + date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  if (days === 1) return '明天 ' + date.toLocaleTimeString('zh-CN', { hour: '2-digit', minute: '2-digit' })
  return date.toLocaleDateString('zh-CN', { month: 'short', day: 'numeric' })
}

// 加载 Dashboard 数据
const loadDashboardData = async () => {
  loading.value = true
  try {
    const userId = userStore.userInfo?.id
    if (!userId) {
      ElMessage.error('用户信息未加载')
      return
    }

    const res = await getDashboardStats(userId)
    if (res.code === 200 && res.data) {
      stats.value = res.data
    } else {
      ElMessage.error(res.message || '加载失败')
    }
  } catch (e) {
    console.error('加载 Dashboard 数据失败:', e)
    ElMessage.error('加载失败，请重试')
  } finally {
    loading.value = false
  }
}

// 刷新数据
const refreshData = () => {
  loadDashboardData()
}

// 生命周期
onMounted(() => {
  loadDashboardData()
})
</script>

<style scoped lang="scss">
.dashboard-container {
  .loading-wrapper {
    padding: 20px;
  }

  .stat-card {
    display: flex;
    align-items: center;
    padding: 10px;
    transition: transform 0.2s;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    }

    .stat-icon {
      width: 80px;
      height: 80px;
      border-radius: 12px;
      display: flex;
      align-items: center;
      justify-content: center;
      margin-right: 15px;
    }

    .stat-content {
      flex: 1;

      .stat-value {
        font-size: 24px;
        font-weight: bold;
        color: #303133;
      }

      .stat-label {
        font-size: 14px;
        color: #909399;
        margin-top: 5px;
      }
    }
  }

  .mt-20 {
    margin-top: 20px;
  }

  .mt-16 {
    margin-top: 16px;
  }

  .mb-10 {
    margin-bottom: 10px;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .knowledge-graph-preview {
    padding: 10px 0;

    .stat-item {
      display: flex;
      justify-content: space-between;
      align-items: center;

      .stat-item-label {
        font-size: 14px;
        color: #909399;
      }

      .stat-item-value {
        font-size: 28px;
        font-weight: bold;
        color: #303133;

        &.mastered {
          color: #67c23a;
        }

        &.learning {
          color: #409eff;
        }

        &.not-started {
          color: #909399;
        }
      }
    }

    .weak-points {
      .section-title {
        font-size: 14px;
        font-weight: 500;
        color: #303133;
        margin-bottom: 12px;
      }

      .weak-point-item {
        margin-bottom: 12px;

        .point-name {
          font-size: 13px;
          color: #606266;
          display: block;
          margin-bottom: 6px;
        }
      }

      .empty-weak-points {
        padding: 20px 0;
      }
    }
  }

  .todo-item {
    display: flex;
    justify-content: space-between;
    align-items: center;

    &.completed {
      .todo-title {
        text-decoration: line-through;
        color: #909399;
      }
    }

    .todo-title {
      font-size: 14px;
      color: #303133;
    }
  }

  .empty-suggestions {
    padding: 20px 0;
  }
}
</style>
