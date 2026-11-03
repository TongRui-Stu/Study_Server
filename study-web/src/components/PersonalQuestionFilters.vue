<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getSubjectList } from '@/api/subject'
import { getChapterList } from '@/api/chapter'
import { getKnowledgePointList } from '@/api/knowledgePoint'

const props = defineProps({
  showWrongCountOrder: { type: Boolean, default: false },
  showFavoriteTimeOrder: { type: Boolean, default: false },
  initialFilters: { type: Object, default: () => ({}) },
  active: { type: Boolean, default: false },
  practiceDisabled: { type: Boolean, default: true }
})
const emit = defineEmits(['search', 'practice'])

const subjects = ref([])
const chapters = ref([])
const knowledgePoints = ref([])
const initializing = ref(true)
const loadingSubjects = ref(false)
const loadingChapters = ref(false)
const loadingKnowledgePoints = ref(false)
let chapterRequestVersion = 0
let knowledgeRequestVersion = 0

const filters = reactive({
  subjectId: null,
  chapterId: null,
  knowledgePointId: null,
  keyword: '',
  wrongCountOrder: '',
  favoriteTimeOrder: ''
})

onMounted(initialize)

async function initialize() {
  await loadSubjects()
  const initial = props.initialFilters
  const subjectId = Number(initial.subjectId)
  if (subjects.value.some(item => item.id === subjectId)) {
    filters.subjectId = subjectId
    await handleSubjectChange(subjectId)
    const chapterId = Number(initial.chapterId)
    if (chapters.value.some(item => item.id === chapterId)) {
      filters.chapterId = chapterId
      await handleChapterChange(chapterId)
      const pointId = Number(initial.knowledgePointId)
      if (knowledgePoints.value.some(item => item.id === pointId)) filters.knowledgePointId = pointId
    }
  }
  filters.keyword = typeof initial.keyword === 'string' ? initial.keyword.slice(0, 100) : ''
  filters.wrongCountOrder = props.showWrongCountOrder && ['ASC', 'DESC'].includes(initial.wrongCountOrder)
    ? initial.wrongCountOrder : ''
  filters.favoriteTimeOrder = props.showFavoriteTimeOrder && initial.favoriteTimeOrder === 'ASC'
    ? 'ASC' : ''
  initializing.value = false
  query()
}

async function loadSubjects() {
  loadingSubjects.value = true
  try {
    const response = await getSubjectList()
    subjects.value = response.data || []
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '获取科目列表失败')
  } finally {
    loadingSubjects.value = false
  }
}

async function handleSubjectChange(subjectId) {
  filters.chapterId = null
  filters.knowledgePointId = null
  chapters.value = []
  knowledgePoints.value = []
  chapterRequestVersion += 1
  knowledgeRequestVersion += 1
  loadingChapters.value = false
  loadingKnowledgePoints.value = false
  if (!subjectId) return

  const requestVersion = chapterRequestVersion
  loadingChapters.value = true
  try {
    const response = await getChapterList(subjectId)
    if (requestVersion === chapterRequestVersion) chapters.value = response.data || []
  } catch (error) {
    if (requestVersion === chapterRequestVersion) {
      ElMessage.error(error.response?.data?.message || '获取章节列表失败')
    }
  } finally {
    if (requestVersion === chapterRequestVersion) loadingChapters.value = false
  }
}

async function handleChapterChange(chapterId) {
  filters.knowledgePointId = null
  knowledgePoints.value = []
  knowledgeRequestVersion += 1
  loadingKnowledgePoints.value = false
  if (!chapterId) return

  const requestVersion = knowledgeRequestVersion
  loadingKnowledgePoints.value = true
  try {
    const response = await getKnowledgePointList(chapterId)
    if (requestVersion === knowledgeRequestVersion) knowledgePoints.value = response.data || []
  } catch (error) {
    if (requestVersion === knowledgeRequestVersion) {
      ElMessage.error(error.response?.data?.message || '获取知识点列表失败')
    }
  } finally {
    if (requestVersion === knowledgeRequestVersion) loadingKnowledgePoints.value = false
  }
}

function query() {
  emit('search', {
    subjectId: filters.subjectId || undefined,
    chapterId: filters.chapterId || undefined,
    knowledgePointId: filters.knowledgePointId || undefined,
    keyword: filters.keyword.trim() || undefined,
    wrongCountOrder: props.showWrongCountOrder && filters.wrongCountOrder
      ? filters.wrongCountOrder
      : undefined,
    favoriteTimeOrder: props.showFavoriteTimeOrder && filters.favoriteTimeOrder
      ? filters.favoriteTimeOrder
      : undefined
  })
}

function reset() {
  chapterRequestVersion += 1
  knowledgeRequestVersion += 1
  Object.assign(filters, {
    subjectId: null,
    chapterId: null,
    knowledgePointId: null,
    keyword: '',
    wrongCountOrder: '',
    favoriteTimeOrder: ''
  })
  chapters.value = []
  knowledgePoints.value = []
  loadingChapters.value = false
  loadingKnowledgePoints.value = false
  query()
}

defineExpose({ reset })
</script>

<template>
  <el-card class="filter-card" shadow="never">
    <el-form class="filter-form" :disabled="initializing" label-position="top" @submit.prevent>
      <el-form-item label="科目">
        <el-select
          v-model="filters.subjectId"
          clearable
          filterable
          placeholder="全部科目"
          :loading="loadingSubjects"
          @change="handleSubjectChange"
        >
          <el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="章节">
        <el-select
          v-model="filters.chapterId"
          clearable
          filterable
          placeholder="全部章节"
          :disabled="!filters.subjectId"
          :loading="loadingChapters"
          @change="handleChapterChange"
        >
          <el-option v-for="item in chapters" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="知识点">
        <el-select
          v-model="filters.knowledgePointId"
          clearable
          filterable
          placeholder="全部知识点"
          :disabled="!filters.chapterId"
          :loading="loadingKnowledgePoints"
        >
          <el-option v-for="item in knowledgePoints" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="题干关键字" class="keyword-field">
        <el-input
          v-model="filters.keyword"
          clearable
          maxlength="100"
          placeholder="输入题干关键字"
          @keyup.enter="query"
        />
      </el-form-item>
      <el-form-item v-if="showWrongCountOrder" label="排序">
        <el-select v-model="filters.wrongCountOrder" placeholder="最近做错">
          <el-option label="最近做错" value="" />
          <el-option label="累计错误：从多到少" value="DESC" />
          <el-option label="累计错误：从少到多" value="ASC" />
        </el-select>
      </el-form-item>
      <el-form-item v-if="showFavoriteTimeOrder" label="排序">
        <el-select v-model="filters.favoriteTimeOrder" placeholder="收藏时间：从新到旧">
          <el-option label="收藏时间：从新到旧" value="" />
          <el-option label="收藏时间：从旧到新" value="ASC" />
        </el-select>
      </el-form-item>
      <div class="filter-actions">
        <el-button type="primary" @click="query">查询</el-button>
        <el-button @click="reset">重置</el-button>
        <el-button type="primary" plain :disabled="practiceDisabled" @click="emit('practice')">{{ active ? '练习筛选结果' : '练习全部' }}</el-button>
      </div>
    </el-form>
  </el-card>
</template>

<style scoped>
.filter-card { margin-bottom: 22px; background: var(--color-surface); }
.filter-form { display: grid; grid-template-columns: repeat(3, minmax(0, 1fr)); gap: 14px 16px; align-items: end; }
.filter-form :deep(.el-form-item) { min-width: 0; margin-bottom: 0; }
.filter-form :deep(.el-select), .filter-form :deep(.el-input) { width: 100%; }
.keyword-field { grid-column: span 2; }
.filter-actions { display: flex; flex-wrap: wrap; gap: 10px; align-items: flex-end; grid-column: 1 / -1; }
.filter-actions :deep(.el-button) { min-width: 88px; margin-left: 0; }
@media (max-width: 760px) {
  .filter-form { grid-template-columns: 1fr; }
  .keyword-field { grid-column: auto; }
  .filter-actions { display: grid; grid-template-columns: repeat(auto-fit, minmax(110px, 1fr)); }
  .filter-actions :deep(.el-button) { width: 100%; }
}
</style>
