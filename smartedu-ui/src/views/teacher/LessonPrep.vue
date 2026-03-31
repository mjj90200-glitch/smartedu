<template>
  <div class="lesson-prep-container page-container">
    <el-card>
      <template #header>
        <div class="card-header">
          <span>智能备课工作台</span>
        </div>
      </template>

      <!-- 课程选择 -->
      <div class="course-selector">
        <el-form :inline="true">
          <el-form-item label="选择课程">
            <el-select v-model="selectedCourse" placeholder="请选择课程" style="width: 200px">
              <el-option label="数据结构" :value="1" />
              <el-option label="Java 程序设计" :value="2" />
              <el-option label="数据库原理" :value="3" />
            </el-select>
          </el-form-item>
          <el-form-item label="课题/知识点">
            <el-input v-model="lessonTopic" placeholder="输入课题名称" style="width: 300px" />
          </el-form-item>
          <el-form-item>
            <el-button type="primary" @click="recommendResources">
              <el-icon><Search /></el-icon>
              智能推荐资源
            </el-button>
          </el-form-item>
          <el-form-item>
            <el-button type="success" @click="generateLessonPlan">
              <el-icon><Document /></el-icon>
              生成教案
            </el-button>
          </el-form-item>
        </el-form>
      </div>

      <el-divider />

      <!-- 推荐资源 -->
      <div class="section">
        <h3>推荐备课资源</h3>
        <el-row :gutter="20">
          <el-col
            v-for="(resource, index) in resources"
            :key="index"
            :span="8"
          >
            <el-card class="resource-card" shadow="hover">
              <div class="resource-icon">
                <el-icon :size="40" :color="getResourceColor(resource.type)">
                  <component :is="resource.icon" />
                </el-icon>
              </div>
              <div class="resource-info">
                <div class="resource-title">{{ resource.title }}</div>
                <div class="resource-meta">
                  <el-tag size="small" :type="getResourceType(resource.type)">
                    {{ resource.type }}
                  </el-tag>
                  <span class="match-score">匹配度：{{ resource.matchScore }}%</span>
                </div>
                <div class="resource-actions">
                  <el-button type="primary" text size="small">预览</el-button>
                  <el-button type="primary" size="small">使用</el-button>
                </div>
              </div>
            </el-card>
          </el-col>
        </el-row>
      </div>

      <el-divider />

      <!-- 教案生成 -->
      <div class="section">
        <h3>AI 教案生成</h3>
        <el-card class="lesson-plan-card">
          <div class="plan-placeholder">
            <el-empty description="选择课程和知识点后，点击「生成教案」按钮">
              <p>AI 将自动生成包含以下内容的教案：</p>
              <ul>
                <li>教学目标与重难点</li>
                <li>教学过程设计（导入、新授、练习、总结）</li>
                <li>时间分配建议</li>
                <li>课堂活动设计</li>
                <li>课后作业建议</li>
              </ul>
            </el-empty>
          </div>
        </el-card>
      </div>
    </el-card>
  </div>
</template>

<script setup lang="ts">
import { ref } from 'vue'
import { ElMessage } from 'element-plus'
import { Search, Document, Video, Picture, Notebook, Reading } from '@element-plus/icons-vue'

const selectedCourse = ref(1)
const lessonTopic = ref('')

const resources = ref([
  { title: '二叉树遍历动画演示', type: 'VIDEO', icon: 'Video', matchScore: 95 },
  { title: '数据结构课件 PPT', type: 'DOCUMENT', icon: 'Document', matchScore: 92 },
  { title: '链表操作可视化', type: 'SIMULATION', icon: 'Picture', matchScore: 88 },
  { title: '经典例题解析', type: 'EXERCISE', icon: 'Reading', matchScore: 85 },
  { title: '知识点总结文档', type: 'DOCUMENT', icon: 'Notebook', matchScore: 82 },
  { title: '教学案例视频', type: 'VIDEO', icon: 'Video', matchScore: 78 }
])

const getResourceColor = (type: string) => {
  const colors: Record<string, string> = {
    VIDEO: '#f56c6c',
    DOCUMENT: '#409eff',
    SIMULATION: '#67c23a',
    EXERCISE: '#e6a23c'
  }
  return colors[type] || '#909399'
}

const getResourceType = (type: string) => {
  const types: Record<string, any> = {
    VIDEO: 'danger',
    DOCUMENT: 'primary',
    SIMULATION: 'success',
    EXERCISE: 'warning'
  }
  return types[type] || 'info'
}

const recommendResources = () => {
  ElMessage.success('正在搜索相关资源...')
}

const generateLessonPlan = () => {
  ElMessage.success('正在生成教案...')
}
</script>

<style scoped lang="scss">
.lesson-prep-container {
  .card-header {
    display: flex;
    justify-content: space-between;
    align-items: center;
  }

  .course-selector {
    margin-bottom: 10px;
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

  .resource-card {
    margin-bottom: 15px;
    text-align: center;

    .resource-icon {
      margin-bottom: 15px;
    }

    .resource-info {
      .resource-title {
        font-size: 14px;
        font-weight: 500;
        color: #303133;
        margin-bottom: 10px;
      }

      .resource-meta {
        display: flex;
        justify-content: space-between;
        align-items: center;
        margin-bottom: 10px;

        .match-score {
          font-size: 12px;
          color: #67c23a;
        }
      }
    }
  }

  .lesson-plan-card {
    .plan-placeholder {
      min-height: 300px;

      ul {
        text-align: left;
        color: #606266;
        line-height: 2;
      }
    }
  }
}
</style>
