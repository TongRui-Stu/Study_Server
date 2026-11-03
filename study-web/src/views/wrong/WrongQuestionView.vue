<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { exportWrongQuestions, getWrongQuestions, removeWrongQuestion } from '@/api/wrongQuestion'
import { downloadBlobResponse, getDownloadErrorMessage } from '@/utils/download'
import PersonalQuestionFilters from '@/components/PersonalQuestionFilters.vue'
import { personalQuestionFilters } from '@/utils/personalQuestionFilters'

const router = useRouter()
const route = useRoute()
const initialFilters = personalQuestionFilters(route.query)
const records = ref([])
const total = ref(0)
const current = ref(1)
const size = ref(10)
const loading = ref(false)
const errorMessage = ref('')
const removingId = ref(null)
const exporting = ref(false)
const activeFilters = ref({})
const filtersRef = ref(null)
let requestVersion = 0
const hasActiveFilters = computed(() => Object.values(activeFilters.value).some(value => value !== undefined))

const typeLabels = { SINGLE: '单选题', MULTIPLE: '多选题', JUDGE: '判断题' }
const difficultyLabels = { 1: '简单', 2: '中等', 3: '困难' }


async function loadRecords() {
  const version = ++requestVersion
  loading.value = true
  errorMessage.value = ''
  records.value = []
  total.value = 0
  try {
    const response = await getWrongQuestions({
      page: current.value,
      size: size.value,
      ...activeFilters.value
    })
    if (version !== requestVersion) return
    records.value = response.data.records || []
    total.value = response.data.total || 0
  } catch (error) {
    if (version !== requestVersion) return
    errorMessage.value = error.response?.data?.message || '获取错题本失败'
  } finally {
    if (version === requestVersion) loading.value = false
  }
}

function handleSearch(filters) {
  activeFilters.value = filters
  router.replace({ path: route.path, query: personalQuestionFilters(filters) })
  current.value = 1
  loadRecords()
}

function resetFilters() {
  filtersRef.value?.reset()
}

function formatDate(value) {
  return value ? new Date(value).toLocaleString('zh-CN', { hour12: false }) : '-'
}

function displayAnswer(answer) {
  if (answer === 'TRUE') return '正确'
  if (answer === 'FALSE') return '错误'
  return answer
}

function openPractice(item) {
  if (item.questionStatus !== 1) return
  router.push({
    path: '/practice',
    query: {
      ...personalQuestionFilters(activeFilters.value),
      source: 'wrong',
      questionId: item.questionId
    }
  })
}

function practiceCurrentResults() {
  if (loading.value || total.value === 0 || errorMessage.value) return
  router.push({ path: '/practice', query: {
    ...personalQuestionFilters(activeFilters.value), source: 'wrong'
  } })
}

async function exportRecords() {
  if (exporting.value || total.value === 0) return
  exporting.value = true
  try {
    const response = await exportWrongQuestions(activeFilters.value)
    downloadBlobResponse(response, '我的错题本.xlsx')
    ElMessage.success('错题本已导出')
  } catch (error) {
    ElMessage.error(await getDownloadErrorMessage(error, '导出错题本失败'))
  } finally {
    exporting.value = false
  }
}

async function removeItem(item) {
  try {
    await ElMessageBox.confirm('移出后，该题下次答错时会重新进入错题本。确定继续吗？', '移出错题本', {
      confirmButtonText: '确定移出',
      cancelButtonText: '取消',
      type: 'warning'
    })
    removingId.value = item.questionId
    await removeWrongQuestion(item.questionId)
    const maxPage = Math.max(1, Math.ceil((total.value - 1) / size.value))
    current.value = Math.min(current.value, maxPage)
    await loadRecords()
    ElMessage.success('已移出错题本')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.response?.data?.message || '移出错题本失败')
    }
  } finally {
    removingId.value = null
  }
}
</script>

<template>
  <main class="record-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">WRONG QUESTIONS</p>
        <h1>错题本</h1>
        <p class="subtitle">保留曾经做错的题目和累计错误次数，掌握后可手动移出。</p>
      </div>
      <el-button type="primary" plain :loading="exporting" :disabled="total === 0 || exporting || loading" @click="exportRecords">导出当前结果</el-button>
    </header>

    <PersonalQuestionFilters ref="filtersRef" show-wrong-count-order
      :initial-filters="initialFilters" :active="hasActiveFilters"
      :practice-disabled="loading || total === 0 || Boolean(errorMessage)"
      @search="handleSearch" @practice="practiceCurrentResults" />

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
    <div v-loading="loading" class="record-list">
      <el-empty
        v-if="!loading && !errorMessage && records.length === 0"
        :description="hasActiveFilters ? '没有符合当前条件的题目' : '错题本还是空的，继续保持'"
      >
        <el-button v-if="hasActiveFilters" type="primary" @click="resetFilters">重置筛选</el-button>
        <el-button v-else type="primary" @click="router.push('/home')">继续练习</el-button>
      </el-empty>

      <el-card v-for="item in records" :key="item.questionId" class="record-card" shadow="never">
        <div class="card-heading">
          <div class="tags">
            <el-tag>{{ typeLabels[item.type] || item.type }}</el-tag>
            <el-tag type="info">{{ difficultyLabels[item.difficulty] || '-' }}</el-tag>
            <el-tag :type="item.questionStatus === 1 ? 'success' : 'danger'">
              {{ item.questionStatus === 1 ? '可练习' : '已禁用' }}
            </el-tag>
          </div>
          <span class="wrong-count">累计错误 {{ item.wrongCount }} 次</span>
        </div>

        <h2>{{ item.content }}</h2>
        <p class="hierarchy">{{ item.subjectName }} / {{ item.chapterName }} / {{ item.knowledgePointName }}</p>

        <ul v-if="item.options?.length" class="option-list">
          <li v-for="option in item.options" :key="option.optionKey">
            <strong>{{ option.optionKey }}.</strong> {{ option.content }}
          </li>
        </ul>

        <el-descriptions class="answer-panel" :column="1" :label-width="112" border>
          <el-descriptions-item label="正确答案">{{ displayAnswer(item.correctAnswer) }}</el-descriptions-item>
          <el-descriptions-item label="题目解析">{{ item.analysis || '暂无解析' }}</el-descriptions-item>
          <el-descriptions-item label="最近做错">{{ formatDate(item.lastWrongAt) }}</el-descriptions-item>
        </el-descriptions>

        <div class="card-actions">
          <el-button type="primary" :disabled="item.questionStatus !== 1" @click="openPractice(item)">重新练习</el-button>
          <el-button type="danger" plain :loading="removingId === item.questionId" @click="removeItem(item)">移出错题本</el-button>
        </div>
      </el-card>
    </div>

    <el-pagination
      v-if="total > size"
      v-model:current-page="current"
      class="pagination"
      background
      layout="prev, pager, next, total"
      :page-size="size"
      :total="total"
      @current-change="loadRecords"
    />
  </main>
</template>

<style scoped>
.record-page { max-width: 1000px; margin: 0 auto; padding: 36px var(--page-padding) 64px; }
.page-header { display: flex; align-items: flex-start; justify-content: space-between; gap: 24px; margin-bottom: 30px; }
.page-header h1 { margin: 4px 0 8px; font-size: 34px; }
.eyebrow { margin: 0; color: var(--color-primary); font-size: 13px; font-weight: 700; letter-spacing: 1.5px; }
.subtitle, .hierarchy { color: var(--color-text-secondary); }
.record-list { min-height: 180px; }
.record-card + .record-card { margin-top: 18px; }
.card-heading, .card-actions { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.tags { display: flex; flex-wrap: wrap; gap: 8px; }
.wrong-count { color: var(--color-danger); font-weight: 700; }
.record-card h2 { margin: 18px 0 8px; font-size: 20px; line-height: 1.7; }
.hierarchy { margin: 0 0 16px; }
.option-list { margin: 0 0 18px; padding: 14px 18px 14px 38px; border-radius: var(--radius-sm); background: var(--color-surface-muted); line-height: 1.8; overflow-wrap: anywhere; }
.answer-panel { margin-top: 16px; }
.answer-panel :deep(.el-descriptions__label) { white-space: nowrap; }
.answer-panel :deep(.el-descriptions__content) { overflow-wrap: anywhere; }
.card-actions { justify-content: flex-end; margin-top: 20px; }
.pagination { justify-content: center; margin-top: 28px; }
@media (max-width: 680px) {
  .page-header, .card-heading { align-items: flex-start; flex-direction: column; }
  .page-header > :deep(.el-button) { width: 100%; }
  .card-actions { align-items: stretch; flex-direction: column; }
  .card-actions :deep(.el-button) { margin-left: 0; }
}
</style>
