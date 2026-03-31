<template>
  <div class="class-evaluation-container page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>课堂效果评估</span>
          <el-button type="primary">
            <el-icon><VideoCamera /></el-icon>
            开始录课
          </el-button>
        </div>
      </template>

      <!-- 录课列表 -->
      <div class="section">
        <h3>课堂录像列表</h3>
        <el-table :data="videoList" style="width: 100%">
          <el-table-column prop="title" label="课程标题" min-width="200" />
          <el-table-column prop="courseName" label="课程" width="120" />
          <el-table-column prop="duration" label="时长" width="100" />
          <el-table-column prop="recordTime" label="录制时间" width="160" />
          <el-table-column prop="status" label="状态" width="100">
            <template #default="{ row }">
              <el-tag :type="row.status === 'completed' ? 'success' : 'warning'" size="small">
                {{ row.status === 'completed' ? '已完成' : '分析中' }}
              </el-tag>
            </template>
          </el-table-column>
          <el-table-column label="操作" width="200">
            <template #default="{ row }">
              <el-button type="primary" text size="small" @click="playVideo(row)">
                播放
              </el-button>
              <el-button type="success" text size="small" @click="viewAnalysis(row)">
                查看分析
              </el-button>
            </template>
          </el-table-column>
        </el-table>
      </div>

      <el-divider />

      <!-- 课堂分析概览 -->
      <div class="section">
        <h3>课堂分析概览</h3>
        <el-row :gutter="20">
          <el-col :span="6">
            <el-statistic title="平均专注度" :value="85" suffix="%" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="互动次数" :value="28" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="提问次数" :value="12" />
          </el-col>
          <el-col :span="6">
            <el-statistic title="课堂活跃度" :value="78" suffix="%" />
          </el-col>
        </el-row>

        <div class="chart-placeholder">
          <el-empty description="课堂行为分析图表区域" />
        </div>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { VideoCamera } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'

const videoList = ref([
  { id: 1, title: '二叉树的遍历 - 第 5 章', courseName: '数据结构', duration: '45:00', recordTime: '2024-03-10 10:00', status: 'completed' },
  { id: 2, title: '快速排序算法', courseName: '数据结构', duration: '45:00', recordTime: '2024-03-08 14:00', status: 'completed' },
  { id: 3, title: '面向对象基础', courseName: 'Java 程序设计', duration: '45:00', recordTime: '2024-03-06 10:00', status: 'analyzing' }
])

const playVideo = (row: any) => {
  console.log('播放视频', row)
}

const viewAnalysis = (row: any) => {
  ElMessage.info('查看课堂分析报告')
}
</script>

<style scoped lang="scss">
.class-evaluation-container {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .section {
    margin-bottom: 20px;

    h3 {
      font-size: 16px;
      font-weight: 600;
      color: #303133;
      margin-bottom: 15px;
    }
  }

  .chart-placeholder {
    height: 300px;
    display: flex;
    align-items: center;
    justify-content: center;
    margin-top: 20px;
  }
}
</style>
