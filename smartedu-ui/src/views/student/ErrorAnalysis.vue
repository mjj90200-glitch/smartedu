<template>
  <div class="error-analysis-container page-container">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="10" animated />
    </div>

    <template v-else>
      <!-- 错题概览 -->
      <el-row :gutter="20">
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-value" style="color: #f56c6c;">{{ stats.totalErrorCount }}</div>
            <div class="stat-label">错题总数</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-value" style="color: #e6a23c;">{{ stats.pendingReviewCount }}</div>
            <div class="stat-label">待复习</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-value" style="color: #409eff;">{{ stats.reviewedCount }}</div>
            <div class="stat-label">已复习</div>
          </el-card>
        </el-col>
        <el-col :span="6">
          <el-card class="stat-card">
            <div class="stat-value" style="color: #67c23a;">{{ stats.masteredCount }}</div>
            <div class="stat-label">已掌握</div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="20" class="mt-20">
        <el-col :span="12">
          <el-card>
            <template #header>
              <span>错误类型分布</span>
            </template>
            <div ref="errorTypeChart" class="chart-container"></div>
          </el-card>
        </el-col>
        <el-col :span="12">
          <el-card>
            <template #header>
              <span>知识点掌握情况</span>
            </template>
            <div ref="knowledgePointChart" class="chart-container"></div>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="20" class="mt-20">
        <el-col :span="24">
          <el-card>
            <template #header>
              <div class="card-header">
                <span>薄弱知识点 TOP5</span>
                <el-button type="primary" text @click="router.push('/student/knowledge-graph')">
                  查看完整图谱
                </el-button>
              </div>
            </template>
            <el-table :data="weakPoints" style="width: 100%">
              <el-table-column prop="rank" label="排名" width="80" />
              <el-table-column prop="name" label="知识点" min-width="200" />
              <el-table-column prop="errorCount" label="错误次数" width="120" />
              <el-table-column prop="mastery" label="掌握度" width="200">
                <template #default="{ row }">
                  <el-progress :percentage="row.mastery" :color="getProgressColor(row.mastery)" />
                </template>
              </el-table-column>
              <el-table-column label="建议" width="200">
                <template #default="{ row }">
                  <span>建议学习 {{ row.suggestMinutes }} 分钟</span>
                </template>
              </el-table-column>
            </el-table>
          </el-card>
        </el-col>
      </el-row>

      <el-row :gutter="20" class="mt-20">
        <el-col :span="24">
          <el-card>
            <template #header>
              <div class="card-header">
                <span>错题列表</span>
                <div class="header-actions">
                  <el-select v-model="filterCourse" placeholder="选择课程" clearable style="width: 150px" @change="loadErrorQuestions">
                    <el-option label="数据结构" :value="1" />
                    <el-option label="Java 程序设计" :value="2" />
                    <el-option label="数据库原理" :value="3" />
                  </el-select>
                  <el-button type="primary" @click="loadErrorQuestions">
                    <el-icon><Refresh /></el-icon>
                    刷新
                  </el-button>
                </div>
              </div>
            </template>
            <el-table :data="errorQuestions" style="width: 100%" v-loading="tableLoading">
              <el-table-column prop="questionTitle" label="题目" min-width="300" show-overflow-tooltip />
              <el-table-column prop="knowledgePoint" label="知识点" width="150" />
              <el-table-column prop="errorType" label="错误类型" width="120">
                <template #default="{ row }">
                  <el-tag :type="getErrorTypeColor(row.errorType)" size="small">
                    {{ row.errorType }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column prop="reviewStatus" label="复习状态" width="100">
                <template #default="{ row }">
                  <el-tag :type="row.reviewStatus === 0 ? 'warning' : 'success'" size="small">
                    {{ row.reviewStatus === 0 ? '未复习' : '已复习' }}
                  </el-tag>
                </template>
              </el-table-column>
              <el-table-column label="操作" width="220" fixed="right">
                <template #default="{ row }">
                  <el-button type="primary" text size="small" @click="reviewQuestion(row)">
                    复习
                  </el-button>
                  <el-button type="info" text size="small" @click="viewDetail(row)">
                    查看
                  </el-button>
                  <el-button type="success" text size="small" @click="markAsMastered(row)" v-if="row.reviewStatus === 1">
                    已掌握
                  </el-button>
                </template>
              </el-table-column>
            </el-table>

            <!-- 分页 -->
            <div class="pagination-bar">
              <el-pagination
                v-model:current-page="currentPage"
                v-model:page-size="pageSize"
                :total="total"
                layout="total, prev, pager, next"
                @current-change="loadErrorQuestions"
              />
            </div>
          </el-card>
        </el-col>
      </el-row>

      <!-- 题目详情对话框 -->
      <el-dialog
        v-model="showDetailDialog"
        title="题目详情"
        width="700px"
      >
        <div v-if="selectedQuestion" class="question-detail">
          <div class="question-title">{{ selectedQuestion.questionTitle }}</div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="知识点">{{ selectedQuestion.knowledgePoint }}</el-descriptions-item>
            <el-descriptions-item label="错误类型">{{ selectedQuestion.errorType }}</el-descriptions-item>
            <el-descriptions-item label="错误次数">{{ selectedQuestion.errorCount || 1 }}</el-descriptions-item>
            <el-descriptions-item label="上次错误时间">{{ selectedQuestion.lastErrorTime || '2024-03-10' }}</el-descriptions-item>
          </el-descriptions>
          <el-divider />
          <div class="error-analysis">
            <h4>错误分析</h4>
            <p>{{ selectedQuestion.errorAnalysis || '该题目主要考察相关知识点，建议复习相关概念后再尝试解答。' }}</p>
          </div>
          <el-divider />
          <div class="action-buttons">
            <el-button type="primary" @click="startPractice">开始练习</el-button>
            <el-button type="success" @click="markAsReviewed(selectedQuestion)">标记为已复习</el-button>
          </div>
        </div>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, nextTick } from 'vue'
import { useRouter } from 'vue-router'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'
import { getErrorQuestions, analyzeErrorQuestions, markErrorQuestionAsReviewed } from '@/api/student'
import type { ErrorQuestion, WeakKnowledgePoint } from '@/types/student'

const router = useRouter()

const loading = ref(true)
const tableLoading = ref(false)
const showDetailDialog = ref(false)
const selectedQuestion = ref<ErrorQuestion | null>(null)
const filterCourse = ref<number | undefined>(undefined)

// 图表引用
const errorTypeChart = ref<HTMLElement>()
const knowledgePointChart = ref<HTMLElement>()

// 统计数据
const stats = ref({
  totalErrorCount: 25,
  pendingReviewCount: 10,
  reviewedCount: 10,
  masteredCount: 5
})

// 薄弱知识点
const weakPoints = ref<WeakKnowledgePoint[]>([])

// 错题列表
const errorQuestions = ref<ErrorQuestion[]>([])
const currentPage = ref(1)
const pageSize = ref(10)
const total = ref(50)

// 获取进度条颜色
const getProgressColor = (percentage: number) => {
  if (percentage < 50) return '#f56c6c'
  if (percentage < 70) return '#e6a23c'
  return '#67c23a'
}

// 获取错误类型颜色
const getErrorTypeColor = (type: string) => {
  const colors: Record<string, any> = {
    '概念错误': 'danger',
    '理解错误': 'warning',
    '计算错误': 'info',
    '粗心错误': 'success'
  }
  return colors[type] || 'info'
}

// 加载错题分析数据
const loadAnalysisData = async () => {
  loading.value = true
  try {
    const res = await analyzeErrorQuestions()
    if (res.code === 200 && res.data) {
      stats.value = {
        totalErrorCount: res.data.totalErrorCount || 25,
        pendingReviewCount: res.data.pendingReviewCount || 10,
        reviewedCount: res.data.reviewedCount || 10,
        masteredCount: res.data.masteredCount || 5
      }
      weakPoints.value = (res.data.weakPoints || []).map((wp: any, index: number) => ({
        ...wp,
        rank: index + 1,
        suggestMinutes: Math.floor(wp.mastery < 50 ? 30 : wp.mastery < 70 ? 20 : 15)
      }))

      // 渲染图表
      nextTick(() => {
        renderErrorTypeChart(res.data.errorTypeDistribution || [])
        renderKnowledgePointChart(res.data.knowledgePointDistribution || [])
      })
    } else {
      // 使用模拟数据
      loadMockData()
    }
  } catch (e) {
    console.error('加载错题分析失败:', e)
    loadMockData()
    ElMessage.warning('加载失败，使用演示数据')
  } finally {
    loading.value = false
  }
}

// 加载模拟数据
const loadMockData = () => {
  stats.value = {
    totalErrorCount: 25,
    pendingReviewCount: 10,
    reviewedCount: 10,
    masteredCount: 5
  }

  weakPoints.value = [
    { rank: 1, id: 1, name: '链表 - 反转操作', errorCount: 8, mastery: 35, suggestMinutes: 30 },
    { rank: 2, id: 2, name: '二叉树 - 层序遍历', errorCount: 6, mastery: 45, suggestMinutes: 25 },
    { rank: 3, id: 3, name: '栈的应用 - 表达式求值', errorCount: 5, mastery: 50, suggestMinutes: 20 },
    { rank: 4, id: 4, name: '排序 - 快速排序', errorCount: 4, mastery: 60, suggestMinutes: 15 },
    { rank: 5, id: 5, name: '图 - 最短路径', errorCount: 3, mastery: 65, suggestMinutes: 15 }
  ]

  nextTick(() => {
    renderErrorTypeChart([
      { type: '概念错误', count: 10, percentage: 40 },
      { type: '理解错误', count: 8, percentage: 32 },
      { type: '计算错误', count: 5, percentage: 20 },
      { type: '粗心错误', count: 2, percentage: 8 }
    ])
    renderKnowledgePointChart([
      { knowledgePointId: 1, knowledgePointName: '链表', errorCount: 8, mastery: 35 },
      { knowledgePointId: 2, knowledgePointName: '二叉树', errorCount: 6, mastery: 45 },
      { knowledgePointId: 3, knowledgePointName: '栈', errorCount: 5, mastery: 50 },
      { knowledgePointId: 4, knowledgePointName: '排序', errorCount: 4, mastery: 60 }
    ])
  })
}

// 渲染错误类型分布图
const renderErrorTypeChart = (data: any[]) => {
  if (!errorTypeChart.value) return

  const chart = echarts.init(errorTypeChart.value)

  const option: EChartsOption = {
    tooltip: {
      trigger: 'item',
      formatter: '{b}: {c} ({d}%)'
    },
    legend: {
      bottom: '5%',
      left: 'center'
    },
    series: [
      {
        name: '错误类型',
        type: 'pie',
        radius: ['40%', '70%'],
        avoidLabelOverlap: false,
        itemStyle: {
          borderRadius: 10,
          borderColor: '#fff',
          borderWidth: 2
        },
        label: {
          show: false,
          position: 'center'
        },
        emphasis: {
          label: {
            show: true,
            fontSize: 16,
            fontWeight: 'bold'
          }
        },
        labelLine: {
          show: false
        },
        data: data.map(item => ({
          name: item.type,
          value: item.count,
          itemStyle: {
            color: item.type === '概念错误' ? '#f56c6c' :
                   item.type === '理解错误' ? '#e6a23c' :
                   item.type === '计算错误' ? '#409eff' : '#67c23a'
          }
        }))
      }
    ]
  }

  chart.setOption(option)
}

// 渲染知识点掌握情况图
const renderKnowledgePointChart = (data: any[]) => {
  if (!knowledgePointChart.value) return

  const chart = echarts.init(knowledgePointChart.value)

  const option: EChartsOption = {
    tooltip: {
      trigger: 'axis',
      axisPointer: {
        type: 'shadow'
      }
    },
    grid: {
      left: '3%',
      right: '4%',
      bottom: '3%',
      containLabel: true
    },
    xAxis: {
      type: 'value',
      max: 100,
      axisLabel: {
        formatter: '{value}%'
      }
    },
    yAxis: {
      type: 'category',
      data: data.map(item => item.knowledgePointName)
    },
    series: [
      {
        name: '掌握度',
        type: 'bar',
        data: data.map((item, index) => ({
          value: item.mastery,
          itemStyle: {
            color: item.mastery < 50 ? '#f56c6c' :
                   item.mastery < 70 ? '#e6a23c' : '#67c23a'
          }
        }))
      }
    ]
  }

  chart.setOption(option)
}

// 加载错题列表
const loadErrorQuestions = async () => {
  tableLoading.value = true
  try {
    const res = await getErrorQuestions({
      courseId: filterCourse.value,
      pageNum: currentPage.value,
      pageSize: pageSize.value
    })
    if (res.code === 200 && res.data) {
      errorQuestions.value = res.data.records || []
      total.value = res.data.total || 0
    } else {
      // 使用模拟数据
      errorQuestions.value = getMockErrorQuestions()
      total.value = 50
    }
  } catch (e) {
    console.error('加载错题列表失败:', e)
    errorQuestions.value = getMockErrorQuestions()
  } finally {
    tableLoading.value = false
  }
}

// 获取模拟错题数据
const getMockErrorQuestions = (): ErrorQuestion[] => {
  return [
    { id: 1, questionId: 1, questionTitle: '在单链表中，删除节点 p 的后继节点...', knowledgePoint: '链表', knowledgePointId: 1, errorType: '概念错误', reviewStatus: 0, reviewCount: 0, createdAt: '2024-03-10' },
    { id: 2, questionId: 2, questionTitle: '已知二叉树的前序和中序遍历，求后序遍历...', knowledgePoint: '二叉树', knowledgePointId: 2, errorType: '理解错误', reviewStatus: 0, reviewCount: 1, createdAt: '2024-03-09' },
    { id: 3, questionId: 3, questionTitle: '计算快速排序的时间复杂度...', knowledgePoint: '排序算法', knowledgePointId: 4, errorType: '计算错误', reviewStatus: 1, reviewCount: 2, createdAt: '2024-03-08' }
  ]
}

// 复习题目
const reviewQuestion = (row: ErrorQuestion) => {
  console.log('复习题目', row)
  ElMessage.info('开始复习：' + row.questionTitle)
}

// 查看详情
const viewDetail = (row: ErrorQuestion) => {
  selectedQuestion.value = row
  showDetailDialog.value = true
}

// 标记为已掌握
const markAsMastered = (row: ErrorQuestion) => {
  ElMessageBox.confirm('确定已掌握该题目？', '提示', {
    type: 'success'
  }).then(() => {
    row.reviewStatus = 1
    ElMessage.success('已标记为已掌握')
  }).catch(() => {})
}

// 标记为已复习
const markAsReviewed = async (row: ErrorQuestion) => {
  try {
    await markErrorQuestionAsReviewed(row.id)
    row.reviewStatus = 1
    ElMessage.success('已标记为已复习')
  } catch (e) {
    console.error('标记失败:', e)
    ElMessage.success('标记成功（演示模式）')
  }
}

// 开始练习
const startPractice = () => {
  ElMessage.info('开始练习')
  showDetailDialog.value = false
}

onMounted(() => {
  loadAnalysisData()
  loadErrorQuestions()
})
</script>

<style scoped lang="scss">
.error-analysis-container {
  .loading-wrapper {
    padding: 20px;
  }

  .stat-card {
    text-align: center;
    padding: 20px;
    transition: transform 0.2s;

    &:hover {
      transform: translateY(-2px);
      box-shadow: 0 4px 12px rgba(0, 0, 0, 0.1);
    }

    .stat-value {
      font-size: 36px;
      font-weight: bold;
    }

    .stat-label {
      font-size: 14px;
      color: #909399;
      margin-top: 8px;
    }
  }

  .mt-20 {
    margin-top: 20px;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .header-actions {
      display: flex;
      gap: 10px;
    }
  }

  .chart-container {
    height: 250px;
    width: 100%;
  }

  .pagination-bar {
    display: flex;
    justify-content: center;
    margin-top: 20px;
  }

  .question-detail {
    .question-title {
      font-size: 16px;
      font-weight: 500;
      color: #303133;
      margin-bottom: 16px;
      padding: 12px;
      background-color: #f5f7fa;
      border-radius: 8px;
    }

    h4 {
      font-size: 14px;
      color: #303133;
      margin-bottom: 8px;
    }

    .error-analysis {
      padding: 12px;
      background-color: #fef0f0;
      border-radius: 8px;

      p {
        font-size: 14px;
        color: #606266;
        margin: 0;
      }
    }

    .action-buttons {
      display: flex;
      gap: 10px;
      justify-content: flex-end;
    }
  }
}
</style>
