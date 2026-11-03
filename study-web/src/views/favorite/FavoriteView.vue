<script setup>
import { computed, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { exportFavorites, getFavorites, removeFavorite } from '@/api/favorite'
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
    const response = await getFavorites({
      page: current.value,
      size: size.value,
      ...activeFilters.value
    })
    if (version !== requestVersion) return
    records.value = response.data.records || []
    total.value = response.data.total || 0
  } catch (error) {
    if (version !== requestVersion) return
    errorMessage.value = error.response?.data?.message || '获取收藏列表失败'
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

function openPractice(item) {
  if (item.questionStatus !== 1) return
  router.push({
    path: '/practice',
    query: {
      ...personalQuestionFilters(activeFilters.value),
      source: 'favorite',
      questionId: item.questionId
    }
  })
}

async function cancelFavorite(item) {
  try {
    await ElMessageBox.confirm(
      '取消后，该题将从收藏题集中移除；需要重新练习并提交后才能再次收藏。确定继续吗？',
      '取消收藏',
      { confirmButtonText: '确定取消', cancelButtonText: '保留收藏', type: 'warning' }
    )
    removingId.value = item.questionId
    await removeFavorite(item.questionId)
    const maxPage = Math.max(1, Math.ceil((total.value - 1) / size.value))
    current.value = Math.min(current.value, maxPage)
    await loadRecords()
    ElMessage.success('已取消收藏')
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.response?.data?.message || '取消收藏失败')
    }
  } finally {
    removingId.value = null
  }
}

function practiceCurrentResults() {
  if (loading.value || total.value === 0 || errorMessage.value) return
  router.push({ path: '/practice', query: {
    ...personalQuestionFilters(activeFilters.value), source: 'favorite'
  } })
}

async function exportRecords() {
  if (exporting.value || total.value === 0) return
  exporting.value = true
  try {
    const response = await exportFavorites(activeFilters.value)
    downloadBlobResponse(response, '我的收藏题目.xlsx')
    ElMessage.success('收藏题目已导出')
  } catch (error) {
    ElMessage.error(await getDownloadErrorMessage(error, '导出收藏题目失败'))
  } finally {
    exporting.value = false
  }
}
</script>

<template>
  <main class="record-page">
    <header class="page-header">
      <div>
        <p class="eyebrow">MY FAVORITES</p>
        <h1>我的收藏</h1>
        <p class="subtitle">收藏的题目不会提前展示答案，进入练习并提交后即可查看解析。</p>
      </div>
      <el-button type="primary" plain :loading="exporting" :disabled="total === 0 || exporting || loading" @click="exportRecords">导出当前结果</el-button>
    </header>

    <PersonalQuestionFilters ref="filtersRef"
      show-favorite-time-order
      :initial-filters="initialFilters" :active="hasActiveFilters"
      :practice-disabled="loading || total === 0 || Boolean(errorMessage)"
      @search="handleSearch" @practice="practiceCurrentResults" />

    <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
    <div v-loading="loading" class="record-list">
      <el-empty
        v-if="!loading && !errorMessage && records.length === 0"
        :description="hasActiveFilters ? '没有符合当前条件的题目' : '暂时没有收藏题目，练习提交后可以收藏重点题目'"
      >
        <el-button v-if="hasActiveFilters" type="primary" @click="resetFilters">重置筛选</el-button>
        <el-button v-else type="primary" @click="router.push('/home')">去选择科目</el-button>
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
          <span class="favorite-time">收藏于 {{ formatDate(item.createdAt) }}</span>
        </div>

        <h2>{{ item.content }}</h2>
        <p class="hierarchy">{{ item.subjectName }} / {{ item.chapterName }} / {{ item.knowledgePointName }}</p>

        <ul v-if="item.options?.length" class="option-list">
          <li v-for="option in item.options" :key="option.optionKey">
            <strong>{{ option.optionKey }}.</strong> {{ option.content }}
          </li>
        </ul>

        <div class="card-actions">
          <el-button type="primary" :disabled="item.questionStatus !== 1" @click="openPractice(item)">去练习</el-button>
          <el-button :loading="removingId === item.questionId" @click="cancelFavorite(item)">取消收藏</el-button>
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
.eyebrow { margin: 0; color: var(--color-warning); font-size: 13px; font-weight: 700; letter-spacing: 1.5px; }
.subtitle, .hierarchy, .favorite-time { color: var(--color-text-secondary); }
.record-list { min-height: 180px; }
.record-card + .record-card { margin-top: 18px; }
.card-heading, .card-actions { display: flex; align-items: center; justify-content: space-between; gap: 16px; }
.tags { display: flex; flex-wrap: wrap; gap: 8px; }
.favorite-time { font-size: 14px; }
.record-card h2 { margin: 18px 0 8px; font-size: 20px; line-height: 1.7; }
.hierarchy { margin: 0 0 16px; }
.option-list { margin: 0 0 18px; padding: 14px 18px 14px 38px; border-radius: var(--radius-sm); background: var(--color-surface-muted); line-height: 1.8; overflow-wrap: anywhere; }
.card-actions { justify-content: flex-end; margin-top: 20px; }
.pagination { justify-content: center; margin-top: 28px; }
@media (max-width: 680px) {
  .page-header, .card-heading { align-items: flex-start; flex-direction: column; }
  .page-header > :deep(.el-button) { width: 100%; }
  .card-actions { align-items: stretch; flex-direction: column; }
  .card-actions :deep(.el-button) { margin-left: 0; }
}
</style>
