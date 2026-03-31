<template>
  <div class="knowledge-graph-container page-container">
    <!-- 加载状态 -->
    <div v-if="loading" class="loading-wrapper">
      <el-skeleton :rows="10" animated />
    </div>

    <template v-else>
      <el-card>
        <template #header>
          <div class="card-header">
            <span>课程知识图谱</span>
            <div class="header-actions">
              <el-select v-model="selectedCourse" placeholder="选择课程" style="width: 200px" @change="loadKnowledgeGraph">
                <el-option label="数据结构" :value="1" />
                <el-option label="Java 程序设计" :value="2" />
                <el-option label="数据库原理" :value="3" />
              </el-select>
              <el-button type="primary" @click="loadKnowledgeGraph">
                <el-icon><Refresh /></el-icon>
                刷新
              </el-button>
            </div>
          </div>
        </template>

        <div class="graph-content">
          <el-row :gutter="20">
            <el-col :span="18">
              <div class="graph-visualization" ref="graphContainer">
                <!-- ECharts 知识图谱可视化区域 -->
              </div>
            </el-col>

            <el-col :span="6">
              <el-card class="legend-card">
                <template #header>
                  <span>图例说明</span>
                </template>
                <div class="legend">
                  <div class="legend-item">
                    <span class="legend-dot mastered"></span>
                    <span>已掌握（正确率 ≥ 80%）</span>
                  </div>
                  <div class="legend-item">
                    <span class="legend-dot learning"></span>
                    <span>学习中（50% ≤ 正确率 < 80%）</span>
                  </div>
                  <div class="legend-item">
                    <span class="legend-dot not-started"></span>
                    <span>未开始</span>
                  </div>
                  <div class="legend-item">
                    <span class="legend-dot weak"></span>
                    <span>薄弱知识点（正确率 < 50%）</span>
                  </div>
                </div>

                <el-divider />

                <div class="stats">
                  <el-statistic title="总知识点数" :value="graphStats.total" />
                  <el-statistic title="已掌握" :value="graphStats.mastered" value-style="color: #67c23a" />
                  <el-statistic title="学习中" :value="graphStats.learning" value-style="color: #409eff" />
                  <el-statistic title="待学习" :value="graphStats.notStarted" value-style="color: #909399" />
                </div>

                <el-divider />

                <div class="interaction-tips">
                  <h4>操作提示</h4>
                  <ul>
                    <li>拖拽节点可调整位置</li>
                    <li>滚轮可缩放图谱</li>
                    <li>点击节点查看详情</li>
                    <li>双击节点可展开/收起关联</li>
                  </ul>
                </div>
              </el-card>
            </el-col>
          </el-row>
        </div>
      </el-card>

      <!-- 知识点详情对话框 -->
      <el-dialog
        v-model="showDetailDialog"
        title="知识点详情"
        width="600px"
      >
        <div v-if="selectedNode" class="node-detail">
          <div class="detail-header">
            <h3>{{ selectedNode.name }}</h3>
            <el-tag :type="getNodeStatusType(selectedNode.status)">
              {{ getNodeStatusText(selectedNode.status) }}
            </el-tag>
          </div>
          <el-descriptions :column="2" border>
            <el-descriptions-item label="掌握度">
              <el-progress :percentage="selectedNode.mastery" :color="getProgressColor(selectedNode.mastery)" />
            </el-descriptions-item>
            <el-descriptions-item label="关联题目数">
              {{ selectedNode.questionCount || 0 }}
            </el-descriptions-item>
            <el-descriptions-item label="正确率">
              {{ selectedNode.accuracy || 0 }}%
            </el-descriptions-item>
            <el-descriptions-item label="学习时长">
              {{ selectedNode.studyMinutes || 0 }} 分钟
            </el-descriptions-item>
          </el-descriptions>
          <el-divider />
          <div class="detail-section">
            <h4>前置知识</h4>
            <el-tag v-for="prereq in selectedNode.prerequisites" :key="prereq" class="mr-8">{{ prereq }}</el-tag>
            <el-empty v-if="!selectedNode.prerequisites?.length" description="无前置知识" :image-size="60" />
          </div>
          <el-divider />
          <div class="detail-section">
            <h4>推荐学习资源</h4>
            <el-button type="primary" link @click="goToPractice">开始练习</el-button>
            <el-button type="success" link @click="goToVideo">观看视频</el-button>
            <el-button type="info" link @click="goToDoc">查看文档</el-button>
          </div>
        </div>
      </el-dialog>
    </template>
  </div>
</template>

<script setup lang="ts">
import { ref, reactive, onMounted, onUnmounted } from 'vue'
import { ElMessage } from 'element-plus'
import { Refresh } from '@element-plus/icons-vue'
import * as echarts from 'echarts'
import type { EChartsOption } from 'echarts'
import { getKnowledgeGraph } from '@/api/student'
import type { KnowledgeGraphData, KnowledgeGraphNode } from '@/types/student'

const selectedCourse = ref<number>(1)
const graphContainer = ref<HTMLElement>()
const loading = ref(true)
const showDetailDialog = ref(false)
const selectedNode = ref<any>(null)

// 图谱统计数据
const graphStats = reactive({
  total: 0,
  mastered: 0,
  learning: 0,
  notStarted: 0
})

// ECharts 实例
let chartInstance: echarts.ECharts | null = null

// 节点颜色配置
const nodeColors = {
  mastered: '#67c23a',
  learning: '#409eff',
  notStarted: '#909399',
  weak: '#f56c6c'
}

// 获取节点状态类型
const getNodeStatusType = (status: string) => {
  const types: Record<string, any> = {
    mastered: 'success',
    learning: 'primary',
    notStarted: 'info',
    weak: 'danger'
  }
  return types[status] || 'info'
}

// 获取节点状态文本
const getNodeStatusText = (status: string) => {
  const texts: Record<string, string> = {
    mastered: '已掌握',
    learning: '学习中',
    notStarted: '未开始',
    weak: '薄弱'
  }
  return texts[status] || '未知'
}

// 获取进度条颜色
const getProgressColor = (percentage: number) => {
  if (percentage < 50) return nodeColors.weak
  if (percentage < 70) return nodeColors.learning
  return nodeColors.mastered
}

// 获取节点状态
const getNodeStatus = (mastery: number): string => {
  if (mastery >= 80) return 'mastered'
  if (mastery >= 50) return 'learning'
  if (mastery > 0) return 'weak'
  return 'notStarted'
}

// 加载知识图谱数据
const loadKnowledgeGraph = async () => {
  loading.value = true
  try {
    const res = await getKnowledgeGraph(selectedCourse.value)
    if (res.code === 200 && res.data) {
      const graphData = res.data as KnowledgeGraphData
      initChart(graphData)
      updateStats(graphData.nodes)
    } else {
      // 使用模拟数据演示
      const mockData = generateMockData()
      initChart(mockData)
      updateStats(mockData.nodes)
      ElMessage.info('使用演示数据')
    }
  } catch (e) {
    console.error('加载知识图谱失败:', e)
    // 使用模拟数据演示
    const mockData = generateMockData()
    initChart(mockData)
    updateStats(mockData.nodes)
    ElMessage.warning('加载失败，使用演示数据')
  } finally {
    loading.value = false
  }
}

// 更新统计数据
const updateStats = (nodes: KnowledgeGraphNode[]) => {
  graphStats.total = nodes.length
  graphStats.mastered = nodes.filter(n => n.status === 'mastered').length
  graphStats.learning = nodes.filter(n => n.status === 'learning').length
  graphStats.notStarted = nodes.filter(n => n.status === 'not-started').length
}

// 生成模拟数据
const generateMockData = (): KnowledgeGraphData => {
  const nodes: KnowledgeGraphNode[] = [
    { id: 1, name: '线性表', mastery: 85, status: 'mastered', x: 300, y: 100 },
    { id: 2, name: '顺序表', mastery: 90, status: 'mastered', x: 150, y: 200 },
    { id: 3, name: '链表', mastery: 75, status: 'learning', x: 250, y: 200 },
    { id: 4, name: '单链表', mastery: 70, status: 'learning', x: 150, y: 300 },
    { id: 5, name: '双向链表', mastery: 60, status: 'learning', x: 250, y: 300 },
    { id: 6, name: '循环链表', mastery: 45, status: 'weak', x: 350, y: 300 },
    { id: 7, name: '栈', mastery: 80, status: 'mastered', x: 450, y: 200 },
    { id: 8, name: '队列', mastery: 70, status: 'learning', x: 550, y: 200 },
    { id: 9, name: '树', mastery: 40, status: 'weak', x: 450, y: 100 },
    { id: 10, name: '二叉树', mastery: 35, status: 'weak', x: 400, y: 50 },
    { id: 11, name: '图', mastery: 0, status: 'not-started', x: 600, y: 100 },
    { id: 12, name: '排序', mastery: 65, status: 'learning', x: 550, y: 300 },
    { id: 13, name: '查找', mastery: 55, status: 'learning', x: 650, y: 300 }
  ]

  const links = [
    { source: 1, target: 2, relation: '包含' },
    { source: 1, target: 3, relation: '包含' },
    { source: 3, target: 4, relation: '包含' },
    { source: 3, target: 5, relation: '包含' },
    { source: 3, target: 6, relation: '包含' },
    { source: 1, target: 7, relation: '前置' },
    { source: 1, target: 8, relation: '前置' },
    { source: 7, target: 9, relation: '前置' },
    { source: 9, target: 10, relation: '包含' },
    { source: 9, target: 11, relation: '前置' },
    { source: 10, target: 12, relation: '应用' },
    { source: 10, target: 13, relation: '应用' }
  ]

  return { nodes, links }
}

// 初始化 ECharts 图表
const initChart = (data: KnowledgeGraphData) => {
  if (!graphContainer.value) return

  if (chartInstance) {
    chartInstance.dispose()
  }

  chartInstance = echarts.init(graphContainer.value)

  const nodes = data.nodes.map(node => ({
    ...node,
    symbolSize: 40 + node.mastery * 0.3,
    itemStyle: {
      color: nodeColors[node.status] || nodeColors.notStarted
    },
    label: {
      show: true,
      position: 'right',
      fontSize: 12,
      color: '#333'
    }
  }))

  const links = data.links.map(link => ({
    ...link,
    lineStyle: {
      color: '#999',
      curveness: 0.2
    }
  }))

  const option: EChartsOption = {
    tooltip: {
      trigger: 'item',
      formatter: (params: any) => {
        if (params.dataType === 'node') {
          return `
            <div style="padding: 8px;">
              <div style="font-weight: bold; margin-bottom: 4px;">${params.name}</div>
              <div>掌握度：${params.data.mastery}%</div>
              <div>状态：${getNodeStatusText(params.data.status)}</div>
              <div>题目数：${params.data.questionCount || 0}</div>
            </div>
          `
        }
        return params.data.relation || ''
      }
    },
    legend: [{
      data: ['已掌握', '学习中', '未开始', '薄弱'],
      bottom: 10,
      textStyle: {
        color: '#666'
      }
    }],
    series: [{
      type: 'graph',
      layout: 'force',
      data: nodes,
      links: links,
      roam: true,
      draggable: true,
      force: {
        repulsion: 200,
        edgeLength: [80, 150],
        gravity: 0.1
      },
      edgeSymbol: ['none', 'arrow'],
      edgeSymbolSize: 8,
      emphasis: {
        focus: 'adjacency',
        lineStyle: {
          width: 4
        }
      },
      lineStyle: {
        color: 'source',
        width: 1.5
      }
    }]
  }

  chartInstance.setOption(option)

  // 监听点击事件
  chartInstance.on('click', (params: any) => {
    if (params.dataType === 'node') {
      showNodeDetail(params.data)
    }
  })

  // 窗口大小变化时重新调整大小
  window.addEventListener('resize', handleResize)
}

// 显示节点详情
const showNodeDetail = (node: KnowledgeGraphNode) => {
  selectedNode.value = {
    ...node,
    status: getNodeStatus(node.mastery),
    questionCount: Math.floor(Math.random() * 20) + 5,
    accuracy: Math.round(node.mastery),
    studyMinutes: Math.floor(node.mastery * 2),
    prerequisites: node.id === 3 ? ['线性表', '顺序表'] : [],
    accuracy: node.mastery
  }
  showDetailDialog.value = true
}

// 处理窗口大小变化
const handleResize = () => {
  chartInstance?.resize()
}

// 跳转函数
const goToPractice = () => {
  ElMessage.info('跳转到练习页面')
}

const goToVideo = () => {
  ElMessage.info('跳转到视频页面')
}

const goToDoc = () => {
  ElMessage.info('跳转到文档页面')
}

// 生命周期
onMounted(() => {
  loadKnowledgeGraph()
})

onUnmounted(() => {
  if (chartInstance) {
    chartInstance.dispose()
    window.removeEventListener('resize', handleResize)
  }
})
</script>

<style scoped lang="scss">
.knowledge-graph-container {
  .loading-wrapper {
    padding: 20px;
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

  .graph-content {
    .graph-visualization {
      height: 550px;
      background-color: #fafafa;
      border-radius: 8px;
    }

    .legend-card {
      .legend {
        .legend-item {
          display: flex;
          align-items: center;
          margin-bottom: 12px;
          font-size: 13px;
          color: #606266;

          .legend-dot {
            width: 12px;
            height: 12px;
            border-radius: 50%;
            margin-right: 10px;
            flex-shrink: 0;

            &.mastered {
              background-color: #67c23a;
            }

            &.learning {
              background-color: #409eff;
            }

            &.not-started {
              background-color: #909399;
            }

            &.weak {
              background-color: #f56c6c;
            }
          }
        }
      }

      .stats {
        .el-statistic {
          margin-bottom: 12px;
        }
      }

      .interaction-tips {
        h4 {
          font-size: 14px;
          color: #303133;
          margin-bottom: 8px;
        }

        ul {
          margin: 0;
          padding-left: 20px;
          font-size: 13px;
          color: #909399;

          li {
            margin-bottom: 4px;
          }
        }
      }
    }
  }

  .node-detail {
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

    .detail-section {
      margin: 16px 0;

      h4 {
        font-size: 14px;
        color: #303133;
        margin-bottom: 12px;
      }

      .mr-8 {
        margin-right: 8px;
      }
    }
  }
}
</style>
