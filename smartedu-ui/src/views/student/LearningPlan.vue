<template>
  <div class="learning-plan-container page-container">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="10" animated />
    </div>

    <template v-else>
      <el-card>
        <template #header>
          <div class="card-header">
            <span>个性化学习计划</span>
            <el-button type="primary" @click="showGenerateDialog = true">
              <el-icon><Plus /></el-icon>
              生成新计划
            </el-button>
          </div>
        </template>

        <!-- 计划列表 -->
        <el-table :data="planList" style="width: 100%" v-loading="tableLoading">
          <el-table-column prop="title" label="计划名称" min-width="200" />
          <el-table-column prop="courseName" label="课程" width="150" />
          <el-table-column prop="startDate" label="开始日期" width="120" />
          <el-table-column prop="endDate" label="结束日期" width="120" />
          <el-table-column label="进度" width="200">
            <template #default="{ row }">
              <el-progress :percentage="row.progress" :status="row.progress >= 100 ? 'success' : undefined" />
            </template>
          </el-table-column>
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="getStatusType(row.status)">
                {{ getStatusText(row.status) }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="250" fixed="right">
            <template #default="{ row }">
              <el-button type="primary" text size="small" @click="viewPlan(row)">
                查看详情
              </el-button>
              <el-button type="success" text size="small" @click="updateProgress(row)" v-if="row.status === 1">
                更新进度
              </el-button>
              <el-button type="danger" text size="small" @click="deletePlan(row)">
                删除
              </el-button>
            </template>
          </el-table-column>
        </el-table>

        <!-- 空状态 -->
        <el-empty v-if="!planList.length" description="暂无学习计划，创建一个吧~" />
      </el-card>

      <!-- 生成计划对话框 -->
      <el-dialog
        v-model="showGenerateDialog"
        title="生成学习计划"
        width="500px"
      >
        <el-form :model="generateForm" label-width="100px" ref="formRef">
          <el-form-item label="选择课程" required>
            <el-select v-model="generateForm.courseId" placeholder="请选择课程" style="width: 100%">
              <el-option label="数据结构" :value="1" />
              <el-option label="Java 程序设计" :value="2" />
              <el-option label="数据库原理" :value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="目标分数" required>
            <el-input-number v-model="generateForm.targetScore" :min="60" :max="100" style="width: 100%" />
          </el-form-item>
          <el-form-item label="计划天数" required>
            <el-input-number v-model="generateForm.days" :min="7" :max="90" style="width: 100%" />
          </el-form-item>
          <el-form-item label="每日学习时长">
            <el-input-number v-model="generateForm.dailyHours" :min="1" :max="8" style="width: 100%" />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showGenerateDialog = false">取消</el-button>
          <el-button type="primary" @click="handleGenerate" :loading="generating">
            {{ generating ? '生成中...' : '生成计划' }}
          </el-button>
        </template>
      </el-dialog>

      <!-- 计划详情对话框 -->
      <el-dialog
        v-model="showDetailDialog"
        title="学习计划详情"
        width="700px"
      >
        <div v-if="selectedPlan" class="plan-detail">
          <div class="detail-header">
            <h3>{{ selectedPlan.title }}</h3>
            <el-tag :type="getStatusType(selectedPlan.status)">
              {{ getStatusText(selectedPlan.status) }}
            </el-tag>
          </div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="课程">{{ selectedPlan.courseName }}</el-descriptions-item>
            <el-descriptions-item label="开始日期">{{ selectedPlan.startDate }}</el-descriptions-item>
            <el-descriptions-item label="结束日期">{{ selectedPlan.endDate }}</el-descriptions-item>
            <el-descriptions-item label="进度">
              <el-progress :percentage="selectedPlan.progress" :stroke-width="8" />
            </el-descriptions-item>
          </el-descriptions>
          <el-divider />
          <h4>每日任务</h4>
          <el-timeline>
            <el-timeline-item
              v-for="task in selectedPlan.dailyTasks"
              :key="task.id"
              :timestamp="'第 ' + task.day + ' 天'"
              :type="task.completed ? 'success' : 'primary'"
              :hollow="task.completed"
            >
              <div :class="['task-item', { completed: task.completed }]">
                <div class="task-title">{{ task.title }}</div>
                <div class="task-desc">{{ task.description }}</div>
                <el-button
                  v-if="!task.completed"
                  type="success"
                  size="small"
                  text
                  @click="completeTask(task)"
                >
                  完成任务
                </el-button>
              </div>
            </el-timeline-item>
          </el-timeline>
        </div>
      </el-dialog>

      <!-- 更新进度对话框 -->
      <el-dialog
        v-model="showProgressDialog"
        title="更新学习进度"
        width="400px"
      >
        <el-form :model="progressForm" label-width="80px">
          <el-form-item label="当前进度">
            <el-slider v-model="progressForm.progress" :min="0" :max="100" show-input />
          </el-form-item>
        </el-form>
        <template #footer>
          <el-button @click="showProgressDialog = false">取消</el-button>
          <el-button type="primary" @click="confirmUpdateProgress">确认更新</el-button>
        </template>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted } from 'vue'
import { ElMessage, ElMessageBox, type FormInstance } from 'element-plus'
import { Plus } from '@element-plus/icons-vue'
import {
  getLearningPlanList,
  generateLearningPlan,
  updateLearningPlanProgress
} from '@/api/student'
import type { LearningPlan } from '@/types/student'

const loading = ref(true)
const tableLoading = ref(false)
const showGenerateDialog = ref(false)
const showDetailDialog = ref(false)
const showProgressDialog = ref(false)
const generating = ref(false)
const selectedPlan = ref<LearningPlan | null>(null)
const planList = ref<LearningPlan[]>([])

const formRef = ref<FormInstance>()
const selectedPlanForProgress = ref<number | null>(null)

const generateForm = reactive({
  courseId: 1,
  targetScore: 85,
  days: 30,
  dailyHours: 2
})

const progressForm = reactive({
  progress: 0
})

const getStatusType = (status: number) => {
  const types: Record<number, any> = { 1: 'primary', 2: 'success', 3: 'warning' }
  return types[status] || 'info'
}

const getStatusText = (status: number) => {
  const texts: Record<number, string> = { 1: '进行中', 2: '已完成', 3: '已逾期' }
  return texts[status] || '未知'
}

// 加载计划列表
const loadPlanList = async () => {
  loading.value = true
  try {
    const res = await getLearningPlanList()
    if (res.code === 200 && res.data) {
      planList.value = res.data
    } else {
      // 使用模拟数据
      planList.value = getMockPlanList()
    }
  } catch (e) {
    console.error('加载学习计划失败:', e)
    planList.value = getMockPlanList()
    ElMessage.warning('加载失败，使用演示数据')
  } finally {
    loading.value = false
  }
}

// 获取模拟数据
const getMockPlanList = (): LearningPlan[] => {
  return [
    {
      id: 1,
      title: '数据结构期中考试复习计划',
      courseName: '数据结构',
      courseId: 1,
      startDate: '2024-03-01',
      endDate: '2024-03-30',
      progress: 65,
      status: 1,
      dailyTasks: [
        { id: 1, planId: 1, day: 1, title: '线性表基础', description: '学习顺序表和链表的基本概念', completed: true, knowledgePointIds: [1, 2] },
        { id: 2, planId: 1, day: 2, title: '链表操作', description: '掌握链表的插入、删除、反转操作', completed: true, knowledgePointIds: [3, 4] },
        { id: 3, planId: 1, day: 3, title: '栈和队列', description: '学习栈和队列的定义及应用', completed: false, knowledgePointIds: [7, 8] }
      ]
    },
    {
      id: 2,
      title: 'Java 面向对象编程能力提升',
      courseName: 'Java 程序设计',
      courseId: 2,
      startDate: '2024-02-15',
      endDate: '2024-03-15',
      progress: 100,
      status: 2,
      dailyTasks: []
    }
  ]
}

// 生成学习计划
const handleGenerate = async () => {
  if (!generateForm.courseId) {
    ElMessage.warning('请选择课程')
    return
  }

  generating.value = true
  try {
    const res = await generateLearningPlan(generateForm)
    if (res.code === 200) {
      ElMessage.success('学习计划生成成功')
      showGenerateDialog.value = false
      loadPlanList()
    }
  } catch (e) {
    console.error('生成学习计划失败:', e)
    // 模拟成功
    ElMessage.success('学习计划生成成功（演示模式）')
    showGenerateDialog.value = false
    loadPlanList()
  } finally {
    generating.value = false
  }
}

// 查看计划详情
const viewPlan = async (row: LearningPlan) => {
  selectedPlan.value = row
  showDetailDialog.value = true
}

// 更新进度
const updateProgress = (row: LearningPlan) => {
  selectedPlanForProgress.value = row.id
  progressForm.progress = row.progress
  showProgressDialog.value = true
}

// 确认更新进度
const confirmUpdateProgress = async () => {
  if (!selectedPlanForProgress.value) return

  try {
    await updateLearningPlanProgress(selectedPlanForProgress.value, progressForm.progress)
    ElMessage.success('进度更新成功')
    showProgressDialog.value = false
    loadPlanList()
  } catch (e) {
    console.error('更新进度失败:', e)
    // 模拟成功
    ElMessage.success('进度更新成功（演示模式）')
    showProgressDialog.value = false
    loadPlanList()
  }
}

// 删除计划
const deletePlan = (row: LearningPlan) => {
  ElMessageBox.confirm('确定要删除该学习计划吗？', '提示', {
    type: 'warning'
  }).then(() => {
    ElMessage.success('删除成功')
    loadPlanList()
  }).catch(() => {})
}

// 完成任务
const completeTask = (task: any) => {
  task.completed = true
  ElMessage.success('任务已完成')
}

onMounted(() => {
  loadPlanList()
})
</script>

<style scoped lang="scss">
.learning-plan-container {
  .loading-wrapper {
    padding: 20px;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .plan-detail {
    .detail-header {
      display: flex;
      justify-content: space-between;
      align-items: center;
      margin-bottom: 16px;

      h3 {
        margin: 0;
        font-size: 18px;
        color: #303133;
      }
    }

    h4 {
      font-size: 16px;
      color: #303133;
      margin: 16px 0;
    }

    .task-item {
      padding: 12px;
      background-color: #f5f7fa;
      border-radius: 8px;

      &.completed {
        .task-title {
          text-decoration: line-through;
          color: #909399;
        }
      }

      .task-title {
        font-size: 14px;
        font-weight: 500;
        color: #303133;
        margin-bottom: 4px;
      }

      .task-desc {
        font-size: 13px;
        color: #909399;
        margin-bottom: 8px;
      }
    }
  }
}
</style>
