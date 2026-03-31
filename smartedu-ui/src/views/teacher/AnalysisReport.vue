<template>
  <div class="analysis-report-container page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>学情分析报告</span>
          <div class="filter-actions">
            <el-select v-model="selectedCourse" placeholder="选择课程" style="width: 200px">
              <el-option label="数据结构" :value="1" />
              <el-option label="Java 程序设计" :value="2" />
              <el-option label="数据库原理" :value="3" />
            </el-select>
            <el-button type="primary" @click="generateReport">
              生成报告
            </el-button>
          </div>
        </div>
      </template>

      <!-- 班级整体情况 -->
      <div class="section">
        <h3>班级整体情况</h3>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-statistic title="平均分" :value="78.5" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="及格率" :value="85" suffix="%" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="优秀率" :value="32" suffix="%" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="最高分" :value="98" />
          </el-col>
        </el-row>
      </div>

      <el-divider />

      <!-- 分数段分布 -->
      <div class="section">
        <h3>分数段分布</h3>
        <div class="chart-placeholder">
          <el-empty description="分数段分布图表区域" />
        </div>
      </div>

      <el-divider />

      <!-- 知识点掌握情况 -->
      <div class="section">
        <h3>知识点掌握情况</h3>
        <el-table :data="knowledgeStats" style="width: 100%">
          <el-table-column prop="name" label="知识点" min-width="200" />
          <el-table-column prop="avgMastery" label="平均掌握度" width="200">
            <template #default="{ row }">
              <el-progress :percentage="row.avgMastery" :color="getProgressColor(row.avgMastery)" />
            </template>
          </el-table-column>
          <el-table-column prop="errorRate" label="错误率" width="120">
            <template #default="{ row }">
              <el-tag :type="getErrorRateType(row.errorRate)" size="small">
                {{ row.errorRate }}%
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="教学建议" min-width="200">
            <template #default="{ row }">
              {{ row.suggestion }}
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-divider />

      <!-- 学生个人分析 -->
      <div class="section">
        <h3>
          学生个人分析
          <el-input
            v-model="searchStudent"
            placeholder="搜索学生姓名"
            style="width: 200px; margin-left: 15px"
            clearable
          />
        </h3>
        <el-table :data="studentList" style="width: 100%">
          <el-table-column prop="studentName" label="姓名" width="100" />
          <el-table-column prop="avgScore" label="平均分" width="100" />
          <el-table-column prop="completedHomework" label="完成作业数" width="100" />
          <el-table-column prop="errorCount" label="错题数" width="100" />
          <el-table-column prop="studyTime" label="学习时长 (h)" width="100" />
          <el-table-column label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'good' ? 'success' : row.status === 'normal' ? 'warning' : 'danger'" size="small">
                {{ row.statusText }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="120">
            <template #default="{ row }">
              <el-button type="primary" text size="small" @click="viewStudentDetail(row)">
                查看详情
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'

const selectedCourse = ref(1)
const searchStudent = ref('')

const knowledgeStats = ref([
  { name: '线性表 - 顺序表', avgMastery: 85, errorRate: 15, suggestion: '掌握良好，继续保持' },
  { name: '线性表 - 链表', avgMastery: 62, errorRate: 38, suggestion: '需要加强练习' },
  { name: '栈和队列', avgMastery: 78, errorRate: 22, suggestion: '基本掌握' },
  { name: '二叉树遍历', avgMastery: 55, errorRate: 45, suggestion: '建议重点讲解' },
  { name: '排序算法', avgMastery: 70, errorRate: 30, suggestion: '适当增加练习' }
])

const studentList = ref([
  { id: 1, studentName: '张三', avgScore: 92, completedHomework: 10, errorCount: 8, studyTime: 35, status: 'good', statusText: '优秀' },
  { id: 2, studentName: '李四', avgScore: 85, completedHomework: 9, errorCount: 15, studyTime: 28, status: 'normal', statusText: '良好' },
  { id: 3, studentName: '王五', avgScore: 72, completedHomework: 8, errorCount: 22, studyTime: 20, status: 'normal', statusText: '中等' },
  { id: 4, studentName: '赵六', avgScore: 58, completedHomework: 6, errorCount: 35, studyTime: 12, status: 'poor', statusText: '需关注' }
])

const getProgressColor = (percentage: number) => {
  if (percentage < 60) return '#f56c6c'
  if (percentage < 80) return '#e6a23c'
  return '#67c23a'
}

const getErrorRateType = (rate: number) => {
  if (rate < 20) return 'success'
  if (rate < 40) return 'warning'
  return 'danger'
}

const generateReport = () => {
  ElMessage.success('报告生成成功')
}

const viewStudentDetail = (row: any) => {
  console.log('查看学生详情', row)
}
</script>

<style scoped lang="scss">
.analysis-report-container {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;

    .filter-actions {
      display: flex;
      gap: 10px;
    }
  }

  .section {
    margin-bottom: 20px;

    h3 {
      font-size: 16px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 15px;
      display: flex;
      align-items: center;
    }
  }

  .chart-placeholder {
    height: 300px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
}
</style>
