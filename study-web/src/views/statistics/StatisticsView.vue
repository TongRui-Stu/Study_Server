<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref, watch } from 'vue'
import { useRouter } from 'vue-router'
import { init, use } from 'echarts/core'
import { BarChart, LineChart } from 'echarts/charts'
import { GridComponent, LegendComponent, TooltipComponent } from 'echarts/components'
import { CanvasRenderer } from 'echarts/renderers'
import { ElMessage } from 'element-plus'
import { exportLearningStatistics, getLearningStatistics } from '@/api/statistics'
import { streamLearningAnalysisMessage } from '@/api/ai'
import AiMarkdown from '@/components/AiMarkdown.vue'
import AiMessageSource from '@/components/AiMessageSource.vue'
import { useTheme } from '@/composables/useTheme'
import { downloadBlobResponse, getDownloadErrorMessage } from '@/utils/download'
import { downloadConversationMarkdown, hasExportableMessages } from '@/utils/markdownConversation'
import { createAiAutoScroll } from '@/utils/aiAutoScroll'
import { createAiStreamSession, finalizeInterruptedAiTurn, isAbortError } from '@/utils/aiStreamSession'

use([BarChart, LineChart, TooltipComponent, LegendComponent, GridComponent, CanvasRenderer])

const router = useRouter()
const { isDark } = useTheme()
const selectedDays = ref(7)
const loading = ref(false)
const exporting = ref(false)
const errorMessage = ref('')
const statistics = ref(null)
const trendChartElement = ref(null)
const analysisMessages = ref([])
const conversationDays = ref(null)
const analysisInput = ref('')
const analysisMessageList = ref(null)
const analysisLoading = ref(false)
const analysisError = ref('')
const analysisStream = createAiStreamSession()
const aiScroll = createAiAutoScroll(() => analysisMessageList.value, { afterUpdate: nextTick })
let activeAnalysisRequest = null
let trendChart = null

const INITIAL_ANALYSIS_MESSAGE = '请根据我的学习数据生成一份完整的学习诊断和复习建议'
const HISTORY_MAX_MESSAGES = 16
const HISTORY_TOTAL_MAX_CHARS = 32000
const ASSISTANT_HISTORY_MAX_CHARS = 12000

const emptySummary = {
  totalAnswerCount: 0,
  correctAnswerCount: 0,
  incorrectAnswerCount: 0,
  accuracyRate: 0,
  answeredQuestionCount: 0,
  wrongQuestionCount: 0,
  favoriteCount: 0,
  studyDays: 0
}

const summary = computed(() => statistics.value?.summary || emptySummary)
const hasLearningData = computed(() => summary.value.totalAnswerCount > 0)
const subjectStatistics = computed(() => statistics.value?.subjectStatistics || [])
const knowledgePointStatistics = computed(() => statistics.value?.knowledgePointStatistics || [])
const weakKnowledgePoints = computed(() => statistics.value?.weakKnowledgePoints || [])
const hasAnalysisConversation = computed(() => analysisMessages.value.some(message => (
  message.role === 'assistant' && typeof message.content === 'string' && message.content.trim()
)))
const canExportAnalysisConversation = computed(() => (
  conversationDays.value !== null && hasExportableMessages(analysisMessages.value)
))
const analysisRangeDays = computed(() => conversationDays.value ?? selectedDays.value)
const analysisButtonText = computed(() => {
  if (!hasAnalysisConversation.value) return '生成 AI 学习建议'
  if (conversationDays.value !== selectedDays.value) return `按最近${selectedDays.value}天新建诊断`
  return '重新生成'
})
const hasSmallSample = computed(() => hasLearningData.value && summary.value.totalAnswerCount < 10)

watch(isDark, async () => {
  await nextTick()
  updateTrendChart()
  resizeChart()
})

onMounted(() => {
  window.addEventListener('resize', resizeChart)
  loadStatistics()
})

onBeforeUnmount(() => {
  analysisStream.cancel('unmounted')
  aiScroll.dispose()
  window.removeEventListener('resize', resizeChart)
  trendChart?.dispose()
  trendChart = null
})

async function loadStatistics() {
  loading.value = true
  errorMessage.value = ''
  analysisError.value = ''
  try {
    const response = await getLearningStatistics(selectedDays.value)
    statistics.value = response.data
    await nextTick()
    updateTrendChart()
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '获取学习统计失败'
  } finally {
    loading.value = false
  }
}

function updateTrendChart() {
  if (!hasLearningData.value || !trendChartElement.value) {
    trendChart?.clear()
    return
  }
  if (!trendChart) trendChart = init(trendChartElement.value)
  const trend = statistics.value.dailyTrend || []
  const colors = isDark.value
    ? {
        text: '#cbd5e1', axis: '#475569', split: '#334155', tooltipBackground: '#111827',
        tooltipText: '#e5e7eb', tooltipBorder: '#475569', answers: '#60a5fa', correct: '#4ade80', rate: '#fbbf24'
      }
    : {
        text: '#64748b', axis: '#cbd5e1', split: '#e2e8f0', tooltipBackground: '#ffffff',
        tooltipText: '#1f2937', tooltipBorder: '#e2e8f0', answers: '#3b82f6', correct: '#22c55e', rate: '#d97706'
      }
  trendChart.setOption({
    tooltip: {
      trigger: 'axis',
      backgroundColor: colors.tooltipBackground,
      borderColor: colors.tooltipBorder,
      textStyle: { color: colors.tooltipText },
      formatter(params) {
        const lines = [`日期：${params[0]?.axisValue || '-'}`]
        params.forEach(item => {
          const value = item.seriesName === '正确率' ? `${Number(item.value || 0).toFixed(2)}%` : item.value
          lines.push(`${item.marker}${item.seriesName}：${value}`)
        })
        return lines.join('<br/>')
      }
    },
    legend: { top: 4, data: ['答题数', '正确数', '正确率'], textStyle: { color: colors.text } },
    grid: { left: 28, right: 34, top: 58, bottom: 34, containLabel: true },
    xAxis: {
      type: 'category',
      boundaryGap: true,
      data: trend.map(item => item.date.slice(5)),
      axisLine: { lineStyle: { color: colors.axis } },
      axisLabel: { color: colors.text },
      axisTick: { lineStyle: { color: colors.axis } }
    },
    yAxis: [
      {
        type: 'value', name: '题数', minInterval: 1,
        nameTextStyle: { color: colors.text }, axisLabel: { color: colors.text },
        axisLine: { lineStyle: { color: colors.axis } }, splitLine: { lineStyle: { color: colors.split } }
      },
      {
        type: 'value', name: '正确率', min: 0, max: 100,
        nameTextStyle: { color: colors.text }, axisLabel: { color: colors.text, formatter: '{value}%' },
        axisLine: { lineStyle: { color: colors.axis } }, splitLine: { show: false }
      }
    ],
    series: [
      { name: '答题数', type: 'bar', barMaxWidth: 24, itemStyle: { color: colors.answers }, data: trend.map(item => item.answerCount) },
      { name: '正确数', type: 'bar', barMaxWidth: 24, itemStyle: { color: colors.correct }, data: trend.map(item => item.correctCount) },
      {
        name: '正确率',
        type: 'line',
        smooth: false,
        yAxisIndex: 1,
        itemStyle: { color: colors.rate },
        lineStyle: { color: colors.rate },
        data: trend.map(item => Number(item.accuracyRate || 0))
      }
    ]
  }, true)
}

function resizeChart() {
  trendChart?.resize()
}

function formatRate(value) {
  return `${Number(value || 0).toFixed(2)}%`
}

function analysisHistory() {
  const history = analysisMessages.value
    .filter(message => typeof message.content === 'string' && message.content.trim())
    .slice(-HISTORY_MAX_MESSAGES)
    .map(({ role, content }) => ({
      role,
      content: role === 'assistant' ? content.slice(-ASSISTANT_HISTORY_MAX_CHARS) : content
    }))
  let totalChars = history.reduce((total, message) => total + message.content.length, 0)
  while (history.length > 0 && totalChars > HISTORY_TOTAL_MAX_CHARS) {
    totalChars -= history.shift().content.length
  }
  return history
}

function scrollAnalysisToBottom(force = false) {
  return aiScroll.request(force)
}

async function generateAnalysis() {
  if (analysisLoading.value || !hasLearningData.value) return
  const days = selectedDays.value
  analysisMessages.value = []
  conversationDays.value = days
  analysisInput.value = ''
  await runAnalysisStream(days, INITIAL_ANALYSIS_MESSAGE, [])
}

async function sendAnalysisFollowUp() {
  const content = analysisInput.value.trim()
  if (!content || content.length > 2000 || analysisLoading.value || !hasAnalysisConversation.value) return

  const history = analysisHistory()
  analysisInput.value = ''
  await runAnalysisStream(conversationDays.value, content, history)
}

async function runAnalysisStream(days, content, history) {
  analysisMessages.value.push({ role: 'user', content })
  const assistantIndex = analysisMessages.value.length
  analysisMessages.value.push({ role: 'assistant', content: '', streaming: true })
  analysisError.value = ''
  analysisLoading.value = true
  await scrollAnalysisToBottom(true)
  const updateAssistant = patch => {
    const message = analysisMessages.value[assistantIndex]
    if (message) analysisMessages.value[assistantIndex] = { ...message, ...patch }
  }
  const session = analysisStream.begin({
    onSource: event => updateAssistant({ provider: event.provider, model: event.model }),
    onDelta: delta => {
      const current = analysisMessages.value[assistantIndex]
      if (current) updateAssistant({ content: current.content + delta })
      scrollAnalysisToBottom()
    },
    onDone: async () => {
      activeAnalysisRequest = null
      updateAssistant({ streaming: false })
      analysisLoading.value = false
      await scrollAnalysisToBottom()
    },
    onError: async message => {
      activeAnalysisRequest = null
      finalizeInterruptedAiTurn(analysisMessages.value, assistantIndex)
      analysisError.value = message
      analysisLoading.value = false
      await scrollAnalysisToBottom()
    },
    onCancel: reason => {
      activeAnalysisRequest = null
      finalizeInterruptedAiTurn(analysisMessages.value, assistantIndex)
      analysisLoading.value = false
      if (reason === 'user') analysisError.value = '已取消本次生成'
    }
  })
  activeAnalysisRequest = session
  try {
    await streamLearningAnalysisMessage({
      days,
      message: content,
      history
    }, {
      signal: session.signal,
      onEvent: session.handleEvent
    })
  } catch (error) {
    if (!isAbortError(error) && session.isCurrent()) {
      session.cancel('failed')
      if (content !== INITIAL_ANALYSIS_MESSAGE && !analysisInput.value) analysisInput.value = content
      analysisError.value = error.message || 'AI 学习诊断暂时不可用，请稍后重试'
    }
  } finally {
    if (activeAnalysisRequest === session && !session.isCurrent()) activeAnalysisRequest = null
  }
}

function cancelAnalysisGeneration() {
  activeAnalysisRequest?.cancel('user')
}

async function exportStatistics() {
  if (exporting.value) return
  exporting.value = true
  try {
    const response = await exportLearningStatistics(selectedDays.value)
    downloadBlobResponse(response, '学习统计报告.xlsx')
    ElMessage.success('学习统计已导出')
  } catch (error) {
    ElMessage.error(await getDownloadErrorMessage(error, '导出学习统计失败'))
  } finally {
    exporting.value = false
  }
}

function exportAnalysisConversation() {
  if (!canExportAnalysisConversation.value || analysisLoading.value) return
  try {
    const rangeLabel = `最近${conversationDays.value}天`
    downloadConversationMarkdown({
      title: '在线学习平台 - AI 学习诊断',
      scene: '学习统计页 AI 学习诊断',
      metadata: { 诊断范围: rangeLabel },
      messages: analysisMessages.value,
      filenamePrefix: `AI学习诊断_${rangeLabel}`
    })
    ElMessage.success('Markdown 对话已导出')
  } catch (error) {
    ElMessage.error(error.message || 'Markdown 对话导出失败，请稍后重试')
  }
}
</script>

<template>
  <main class="statistics-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">LEARNING STATISTICS</p>
        <h1>学习统计</h1>
        <p class="subtitle">基于历史答题记录计算，帮助你了解练习情况和薄弱知识点。</p>
      </div>
      <el-button type="primary" plain :loading="exporting" :disabled="exporting" @click="exportStatistics">导出 Excel</el-button>
    </header>

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />

    <div v-loading="loading" class="statistics-content">
      <el-empty v-if="!loading && !errorMessage && !hasLearningData" description="还没有学习记录，先去完成一些练习吧">
        <el-button type="primary" @click="router.push('/home')">开始练习</el-button>
      </el-empty>

      <template v-else-if="hasLearningData">
        <section class="summary-grid">
          <el-card shadow="never"><span>总答题次数</span><strong>{{ summary.totalAnswerCount }}</strong></el-card>
          <el-card shadow="never"><span>正确次数</span><strong>{{ summary.correctAnswerCount }}</strong></el-card>
          <el-card shadow="never"><span>总正确率</span><strong>{{ formatRate(summary.accuracyRate) }}</strong></el-card>
          <el-card shadow="never"><span>已练习题目</span><strong>{{ summary.answeredQuestionCount }}</strong></el-card>
          <el-card shadow="never"><span>当前错题</span><strong>{{ summary.wrongQuestionCount }}</strong></el-card>
          <el-card shadow="never"><span>学习天数</span><strong>{{ summary.studyDays }}</strong></el-card>
          <el-card shadow="never"><span>我的收藏</span><strong>{{ summary.favoriteCount }}</strong></el-card>
        </section>

        <el-card class="section-card ai-analysis-card" shadow="never">
          <template #header>
            <div class="section-heading">
              <div>
                <h2>AI 学习诊断</h2>
                <p v-if="hasAnalysisConversation">
                  当前对话基于最近{{ analysisRangeDays }}天趋势；当前图表为最近{{ selectedDays }}天
                </p>
                <p v-else>综合全部历史学习数据与最近{{ selectedDays }}天趋势生成建议</p>
              </div>
              <div class="section-actions">
                <el-button
                  plain
                  title="导出当前 AI 学习诊断对话为 Markdown"
                  aria-label="导出当前 AI 学习诊断对话为 Markdown"
                  :disabled="!canExportAnalysisConversation || analysisLoading"
                  @click="exportAnalysisConversation"
                >导出 MD</el-button>
                <el-button
                  v-if="analysisLoading"
                  @click="cancelAnalysisGeneration"
                >取消生成</el-button>
                <el-button
                  v-else
                  type="primary"
                  @click="generateAnalysis"
                >{{ analysisButtonText }}</el-button>
              </div>
            </div>
          </template>
          <el-alert
            v-if="hasSmallSample"
            class="sample-tip"
            title="当前答题样本较少，AI 诊断结论仅供参考"
            type="info"
            :closable="false"
            show-icon
          />
          <div
            ref="analysisMessageList" @scroll.passive="aiScroll.onScroll"
            class="analysis-message-list"
            aria-live="polite"
            aria-label="AI 学习诊断对话"
          >
            <el-empty
              v-if="analysisMessages.length === 0 && !analysisLoading"
              :image-size="70"
              description="点击按钮后综合全部历史数据与当前趋势范围生成建议"
            />
            <div
              v-for="(message, index) in analysisMessages"
              :key="index"
              :class="['analysis-message', message.role]"
            >
              <span class="analysis-role">{{ message.role === 'user' ? '我' : 'AI' }}</span>
              <div class="analysis-bubble">
                <AiMarkdown v-if="message.role === 'assistant' && message.content" :content="message.content" :streaming="message.streaming" />
                <template v-else-if="message.role === 'assistant'">{{ message.streaming ? '正在生成...' : '未生成内容' }}</template>
                <template v-else>{{ message.content }}</template>
                <AiMessageSource :message="message" />
                <div v-if="message.streaming && message.content" class="streaming-status">正在生成...</div>
              </div>
            </div>
          </div>

          <el-alert
            v-if="analysisError"
            class="analysis-error"
            :title="analysisError"
            type="warning"
            :closable="false"
            show-icon
          />

          <div v-if="hasAnalysisConversation" class="analysis-composer">
            <el-input
              v-model="analysisInput"
              type="textarea"
              :rows="3"
              maxlength="2000"
              show-word-limit
              resize="none"
              placeholder="继续追问，Enter 发送，Shift+Enter 换行"
              :disabled="analysisLoading"
              @keydown.enter.exact.prevent="sendAnalysisFollowUp"
            />
            <el-button v-if="analysisLoading" @click="cancelAnalysisGeneration">取消生成</el-button>
            <el-button v-else type="primary" :disabled="!analysisInput.trim()" @click="sendAnalysisFollowUp">发送追问</el-button>
          </div>
        </el-card>

        <el-card class="section-card" shadow="never">
          <template #header>
            <div class="section-heading">
              <div><h2>最近答题趋势</h2><p>每日答题量、正确数和正确率</p></div>
              <el-radio-group v-model="selectedDays" size="small" @change="loadStatistics">
                <el-radio-button :value="7">最近7天</el-radio-button>
                <el-radio-button :value="30">最近30天</el-radio-button>
              </el-radio-group>
            </div>
          </template>
          <div ref="trendChartElement" class="trend-chart" role="img" aria-label="最近答题趋势图"></div>
        </el-card>

        <el-card class="section-card" shadow="never">
          <template #header><div class="section-heading"><div><h2>科目统计</h2><p>按科目汇总全部历史答题记录</p></div></div></template>
          <el-table :data="subjectStatistics" stripe>
            <el-table-column prop="subjectName" label="科目" min-width="160" />
            <el-table-column prop="answerCount" label="答题数" width="100" />
            <el-table-column prop="correctCount" label="正确数" width="100" />
            <el-table-column prop="incorrectCount" label="错误数" width="100" />
            <el-table-column label="正确率" width="120">
              <template #default="scope">{{ formatRate(scope.row.accuracyRate) }}</template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card class="section-card" shadow="never">
          <template #header><div class="section-heading"><div><h2>知识点统计</h2><p>每次重复作答均计入统计</p></div></div></template>
          <el-table :data="knowledgePointStatistics" stripe>
            <el-table-column prop="subjectName" label="科目" min-width="110" />
            <el-table-column prop="chapterName" label="章节" min-width="150" />
            <el-table-column prop="knowledgePointName" label="知识点" min-width="140" />
            <el-table-column prop="answerCount" label="答题数" width="90" />
            <el-table-column prop="correctCount" label="正确数" width="90" />
            <el-table-column prop="incorrectCount" label="错误数" width="90" />
            <el-table-column label="正确率" width="110">
              <template #default="scope">{{ formatRate(scope.row.accuracyRate) }}</template>
            </el-table-column>
          </el-table>
        </el-card>

        <el-card class="section-card weak-card" shadow="never">
          <template #header>
            <div class="section-heading"><div><h2>薄弱知识点</h2><p>根据历史答题正确率计算，至少作答2次后参与排序</p></div></div>
          </template>
          <el-empty
            v-if="weakKnowledgePoints.length === 0"
            :image-size="80"
            description="数据不足，继续练习后将生成薄弱知识点分析"
          />
          <div v-else class="weak-list">
            <div v-for="(item, index) in weakKnowledgePoints" :key="item.knowledgePointId" class="weak-item">
              <span class="rank">{{ index + 1 }}</span>
              <div>
                <strong>{{ item.knowledgePointName }}</strong>
                <p>{{ item.subjectName }} / {{ item.chapterName }} · 作答 {{ item.answerCount }} 次</p>
              </div>
              <el-tag type="warning">{{ formatRate(item.accuracyRate) }}</el-tag>
            </div>
          </div>
        </el-card>
      </template>
    </div>
  </main>
</template>

<style scoped>
.statistics-page { max-width: 1180px; margin: 0 auto; padding: 36px var(--page-padding) 64px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin-bottom: 30px; }
.page-header h1 { margin: 4px 0 8px; font-size: 34px; }
.eyebrow { margin: 0; color: var(--color-primary); font-size: 13px; font-weight: 700; letter-spacing: 1.5px; }
.subtitle, .section-heading p, .weak-item p { color: var(--color-text-secondary); }
.statistics-content { min-height: 260px; }
.summary-grid { display: grid; grid-template-columns: repeat(auto-fit, minmax(145px, 1fr)); gap: 14px; }
.summary-grid :deep(.el-card__body) { display: flex; flex-direction: column; gap: 10px; }
.summary-grid span { color: var(--color-text-secondary); font-size: 14px; }
.summary-grid strong { color: var(--color-text); font-size: 28px; }
.section-card { margin-top: 20px; }
.section-heading { display: flex; align-items: center; justify-content: space-between; gap: 18px; }
.section-actions { display: flex; align-items: center; justify-content: flex-end; gap: 10px; flex-wrap: wrap; }
.section-actions .el-button { margin: 0; }
.section-heading h2 { margin: 0 0 5px; font-size: 20px; }
.section-heading p { margin: 0; font-size: 14px; }
.trend-chart { width: 100%; height: 360px; }
.sample-tip { margin-bottom: 14px; }
.ai-analysis-card :deep(.el-card__body) { display: grid; gap: 14px; }
.analysis-message-list { overflow-anchor: none;
  height: clamp(360px, 52vh, 500px);
  padding: 16px;
  overflow-y: auto;
  overscroll-behavior: contain;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
}
.analysis-message { display: flex; align-items: flex-start; gap: 10px; margin-bottom: 16px; }
.analysis-message:last-child { margin-bottom: 0; }
.analysis-message.user { flex-direction: row-reverse; }
.analysis-role {
  display: grid;
  flex: 0 0 32px;
  width: 32px;
  height: 32px;
  place-items: center;
  border-radius: 50%;
  background: var(--color-primary);
  color: white;
  font-size: 12px;
  font-weight: 700;
}
.analysis-message.user .analysis-role { background: var(--color-success); }
.analysis-bubble {
  min-width: 0;
  max-width: min(84%, 860px);
  padding: 11px 14px;
  border: 1px solid var(--color-border);
  border-radius: 12px;
  background: var(--color-surface);
  color: var(--color-text);
  line-height: 1.75;
  overflow-wrap: anywhere;
}
.analysis-message.user .analysis-bubble { background: var(--color-primary-soft); white-space: pre-wrap; }
.analysis-bubble :deep(.ai-markdown) { min-width: 0; }
.analysis-error { margin: 0; }
.streaming-status { margin-top: 8px; color: var(--color-text-secondary); font-size: 12px; }
.analysis-composer { display: grid; grid-template-columns: minmax(0, 1fr) auto; align-items: end; gap: 12px; }
.analysis-composer .el-button { min-width: 104px; }
.weak-list { display: grid; gap: 12px; }
.weak-item { display: grid; grid-template-columns: 36px 1fr auto; align-items: center; gap: 14px; padding: 14px; border-radius: var(--radius-sm); background: var(--color-surface-muted); }
.weak-item p { margin: 5px 0 0; font-size: 14px; }
.rank { display: grid; width: 32px; height: 32px; place-items: center; border-radius: 50%; background: var(--color-warning); color: white; font-weight: 700; }
@media (max-width: 700px) {
  .page-header, .section-heading { align-items: flex-start; flex-direction: column; }
  .section-heading .el-button { width: 100%; }
  .section-actions { display: grid; width: 100%; grid-template-columns: 1fr; }
  .analysis-message-list { overflow-anchor: none; height: min(440px, 56vh); padding: 12px; }
  .analysis-bubble { max-width: 88%; }
  .analysis-composer { grid-template-columns: 1fr; }
  .analysis-composer .el-button { width: 100%; }
  .trend-chart { height: 300px; }
}
</style>
