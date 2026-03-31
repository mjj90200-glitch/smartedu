<template>
  <div class="teacher-dashboard-container page-container">
    <!-- 统计概览 -->
    <el-row :gutter="20">
      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-icon" style="background-color: #e3f2fd;">
            <el-icon :size="32" color="#2196f3"><Document /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">12</div>
            <div class="stat-label">布置作业</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-icon" style="background-color: #fff3e0;">
            <el-icon :size="32" color="#ff9800"><User /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">145</div>
            <div class="stat-label">所教学生</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-icon" style="background-color: #f3e5f5;">
            <el-icon :size="32" color="#9c27b0"><Edit /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">28</div>
            <div class="stat-label">待批改作业</div>
          </div>
        </el-card>
      </el-col>

      <el-col :span="6">
        <el-card class="stat-card">
          <div class="stat-icon" style="background-color: #e8f5e9;">
            <el-icon :size="32" color="#4caf50"><TrendCharts /></el-icon>
          </div>
          <div class="stat-content">
            <div class="stat-value">78.5</div>
            <div class="stat-label">班级平均分</div>
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 待批改作业和学情监控 -->
    <el-row :gutter="20" class="mt-20">
      <el-col :span="12">
        <el-card>
          <template #header>
            <div class="card-header">
              <span>待批改作业</span>
              <el-button type="primary" text @click="router.push('/teacher/homework')">
                查看全部
              </el-button>
            </div>
          </template>
          <el-table :data="pendingGrades" style="width: 100%">
            <el-table-column prop="studentName" label="学生" width="100" />
            <el-table-column prop="homeworkTitle" label="作业" min-width="180" show-overflow-tooltip />
            <el-table-column prop="submitTime" label="提交时间" width="160" />
            <el-table-column label="操作" width="80">
              <template #default="{ row }">
                <el-button type="primary" text size="small" @click="gradeHomework(row)">
                  批改
                </el-button>
              </template>
            </el-table-column>
          </el-table>
        </el-card>
      </el-col>

      <el-col :span="12">
        <el-card>
          <template #header>
            <span>学情监控</span>
          </template>
          <div class="chart-placeholder">
            <el-empty description="学情监控图表区域" />
          </div>
        </el-card>
      </el-col>
    </el-row>

    <!-- 教学建议 -->
    <el-row :gutter="20" class="mt-20">
      <el-col :span="24">
        <el-card>
          <template #header>
            <span>AI 教学建议</span>
          </template>
          <el-alert
            v-for="(item, index) in suggestions"
            :key="index"
            :title="item.title"
            :type="item.type"
            :closable="false"
            show-icon
            class="mb-10"
          >
            <template #default>
              {{ item.content }}
            </template>
          </el-alert>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { ref } from 'vue'
import { Document, User, Edit, TrendCharts } from '@element-plus/icons-vue'

const router = useRouter()

const pendingGrades = ref([
  { id: 1, studentName: '张三', homeworkTitle: '第一章 线性表 练习题', submitTime: '2024-03-10 22:30' },
  { id: 2, studentName: '李四', homeworkTitle: '第一章 线性表 练习题', submitTime: '2024-03-10 21:15' },
  { id: 3, studentName: '王五', homeworkTitle: 'Java 面向对象编程', submitTime: '2024-03-10 20:45' },
  { id: 4, studentName: '赵六', homeworkTitle: '第一章 线性表 练习题', submitTime: '2024-03-10 19:30' }
])

const suggestions = ref([
  { title: '薄弱知识点提醒', type: 'warning', content: '班级在「链表」相关知识点上的错误率较高，建议在课堂上重点讲解。' },
  { title: '作业批改建议', type: 'info', content: '有 5 份作业已提交超过 24 小时未批改，建议及时处理以给予学生反馈。' },
  { title: '优秀学生表扬', type: 'success', content: '张三同学连续 3 次作业获得优秀，建议在全班进行表扬。' }
])

const gradeHomework = (row: any) => {
  console.log('批改作业', row)
}
</script>

<style scoped lang="scss">
.teacher-dashboard-container {
  .stat-card {
    display: flex;
    align-items: center;
    padding: 10px;

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

  .mb-10 {
    margin-bottom: 10px;
  }

  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .chart-placeholder {
    height: 250px;
    display: flex;
    align-items: center;
    justify-content: center;
  }
}
</style>
