<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminSubjectPage } from '@/api/subject'
import { getAdminChapterPage } from '@/api/chapter'
import { getAdminKnowledgePointPage } from '@/api/knowledgePoint'
import {
  createQuestion,
  deleteQuestion,
  exportAdminQuestions,
  getAdminQuestion,
  getAdminQuestionPage,
  updateQuestion,
  updateQuestionStatus
} from '@/api/question'
import { downloadBlobResponse, getDownloadErrorMessage } from '@/utils/download'

const typeLabels = { SINGLE: '单选题', MULTIPLE: '多选题', JUDGE: '判断题' }
const difficultyLabels = { 1: '简单', 2: '中等', 3: '困难' }

const loading = ref(false)
const saving = ref(false)
const exporting = ref(false)
const rows = ref([])
const total = ref(0)
const subjects = ref([])
const chapters = ref([])
const knowledgePoints = ref([])
const query = reactive({
  page: 1,
  size: 10,
  subjectId: null,
  chapterId: null,
  knowledgePointId: null,
  type: '',
  difficulty: null,
  status: null,
  keyword: ''
})

const drawerVisible = ref(false)
const detailVisible = ref(false)
const editingId = ref(null)
const detail = ref(null)
const formRef = ref()
const multipleAnswers = ref([])

function emptyForm() {
  return {
    subjectId: null,
    chapterId: null,
    knowledgePointId: null,
    type: 'SINGLE',
    content: '',
    analysis: '',
    difficulty: 2,
    status: 1,
    answer: '',
    options: [createOption(0), createOption(1)]
  }
}

const form = reactive(emptyForm())
const rules = {
  subjectId: [{ required: true, message: '请选择科目', trigger: 'change' }],
  chapterId: [{ required: true, message: '请选择章节', trigger: 'change' }],
  knowledgePointId: [{ required: true, message: '请选择知识点', trigger: 'change' }],
  type: [{ required: true, message: '请选择题型', trigger: 'change' }],
  content: [{ required: true, message: '请输入题干', trigger: 'blur' }],
  difficulty: [{ required: true, message: '请选择难度', trigger: 'change' }],
  status: [{ required: true, message: '请选择状态', trigger: 'change' }]
}

const queryChapters = computed(() => query.subjectId
  ? chapters.value.filter(item => item.subjectId === query.subjectId)
  : chapters.value)
const queryKnowledgePoints = computed(() => {
  if (query.chapterId) return knowledgePoints.value.filter(item => item.chapterId === query.chapterId)
  if (query.subjectId) {
    const chapterIds = new Set(queryChapters.value.map(item => item.id))
    return knowledgePoints.value.filter(item => chapterIds.has(item.chapterId))
  }
  return knowledgePoints.value
})
const formChapters = computed(() => form.subjectId
  ? chapters.value.filter(item => item.subjectId === form.subjectId)
  : [])
const formKnowledgePoints = computed(() => form.chapterId
  ? knowledgePoints.value.filter(item => item.chapterId === form.chapterId)
  : [])

function createOption(index) {
  return { optionKey: String.fromCharCode(65 + index), content: '', sortOrder: index + 1 }
}

async function initializeOptions() {
  const [subjectResponse, chapterResponse, knowledgeResponse] = await Promise.all([
    getAdminSubjectPage({ current: 1, size: 100 }),
    getAdminChapterPage({ current: 1, size: 100 }),
    getAdminKnowledgePointPage({ current: 1, size: 100 })
  ])
  subjects.value = subjectResponse.data.records
  chapters.value = chapterResponse.data.records
  knowledgePoints.value = knowledgeResponse.data.records
}

function cleanQuery() {
  return Object.fromEntries(Object.entries(query).filter(([, value]) => value !== null && value !== ''))
}

async function load() {
  loading.value = true
  try {
    const response = await getAdminQuestionPage(cleanQuery())
    rows.value = response.data.records
    total.value = response.data.total
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '获取题目失败')
  } finally {
    loading.value = false
  }
}

async function exportQuestions() {
  if (exporting.value) return
  exporting.value = true
  try {
    const filters = cleanQuery()
    delete filters.page
    delete filters.size
    const response = await exportAdminQuestions(filters)
    downloadBlobResponse(response, '题库导出.xlsx')
    ElMessage.success('当前筛选条件下的题库已导出')
  } catch (error) {
    ElMessage.error(await getDownloadErrorMessage(error, '导出题库失败'))
  } finally {
    exporting.value = false
  }
}

function search() {
  query.page = 1
  load()
}

function onQuerySubjectChange() {
  query.chapterId = null
  query.knowledgePointId = null
}

function onQueryChapterChange() {
  query.knowledgePointId = null
}

function onFormSubjectChange() {
  form.chapterId = null
  form.knowledgePointId = null
}

function onFormChapterChange() {
  form.knowledgePointId = null
}

function onTypeChange(type) {
  form.answer = ''
  multipleAnswers.value = []
  if (type === 'JUDGE') {
    form.options = []
    form.answer = 'TRUE'
  } else if (form.options.length < 2) {
    form.options = [createOption(0), createOption(1)]
  }
}

function addOption() {
  if (form.options.length >= 26) {
    ElMessage.warning('最多支持26个选项')
    return
  }
  form.options.push(createOption(form.options.length))
}

function removeOption(index) {
  if (form.options.length <= 2) {
    ElMessage.warning('选择题至少保留2个选项')
    return
  }
  form.options.splice(index, 1)
  form.options.forEach((option, optionIndex) => {
    option.optionKey = String.fromCharCode(65 + optionIndex)
    option.sortOrder = optionIndex + 1
  })
  form.answer = ''
  multipleAnswers.value = []
}

function openCreate() {
  editingId.value = null
  Object.assign(form, emptyForm(), {
    subjectId: query.subjectId || null,
    chapterId: query.chapterId || null,
    knowledgePointId: query.knowledgePointId || null
  })
  multipleAnswers.value = []
  drawerVisible.value = true
}

async function openEdit(row) {
  try {
    const response = await getAdminQuestion(row.id)
    const item = response.data
    editingId.value = item.id
    Object.assign(form, {
      subjectId: item.subjectId,
      chapterId: item.chapterId,
      knowledgePointId: item.knowledgePointId,
      type: item.type,
      content: item.content,
      analysis: item.analysis || '',
      difficulty: item.difficulty,
      status: item.status,
      answer: item.type === 'MULTIPLE' ? '' : item.answer,
      options: item.options.map(option => ({
        optionKey: option.optionKey,
        content: option.content,
        sortOrder: option.sortOrder
      }))
    })
    multipleAnswers.value = item.type === 'MULTIPLE' ? item.answer.split(',') : []
    drawerVisible.value = true
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '获取题目详情失败')
  }
}

async function openDetail(row) {
  try {
    const response = await getAdminQuestion(row.id)
    detail.value = response.data
    detailVisible.value = true
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '获取题目详情失败')
  }
}

function buildPayload() {
  if (form.type !== 'JUDGE') {
    if (form.options.length < 2 || form.options.some(option => !option.content.trim())) {
      throw new Error('请至少填写2个完整选项')
    }
    if (form.type === 'SINGLE' && !form.answer) {
      throw new Error('请选择单选题正确答案')
    }
    if (form.type === 'MULTIPLE' && multipleAnswers.value.length < 2) {
      throw new Error('多选题至少选择2个正确答案')
    }
  }
  if (form.type === 'JUDGE' && !form.answer) {
    throw new Error('请选择判断题正确答案')
  }
  return {
    knowledgePointId: form.knowledgePointId,
    type: form.type,
    content: form.content,
    answer: form.type === 'MULTIPLE' ? multipleAnswers.value.join(',') : form.answer,
    analysis: form.analysis,
    difficulty: form.difficulty,
    status: form.status,
    options: form.type === 'JUDGE' ? [] : form.options.map(option => ({ ...option }))
  }
}

async function save() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  let payload
  try {
    payload = buildPayload()
  } catch (error) {
    ElMessage.warning(error.message)
    return
  }
  saving.value = true
  try {
    if (editingId.value) await updateQuestion(editingId.value, payload)
    else await createQuestion(payload)
    ElMessage.success(editingId.value ? '修改成功' : '新增成功')
    drawerVisible.value = false
    await load()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '保存失败')
  } finally {
    saving.value = false
  }
}

async function changeStatus(row) {
  try {
    await updateQuestionStatus(row.id, row.status === 1 ? 0 : 1)
    ElMessage.success('状态已更新')
    await load()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '状态更新失败')
  }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm('确定删除这道题目吗？删除后无法恢复。', '删除确认', { type: 'warning' })
    await deleteQuestion(row.id)
    ElMessage.success('删除成功')
    await load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') {
      ElMessage.error(error.response?.data?.message || '删除失败')
    }
  }
}

function formatTime(value) {
  return value ? value.replace('T', ' ') : '-'
}

onMounted(async () => {
  try {
    await initializeOptions()
    await load()
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '初始化失败')
  }
})
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <div><h1>题库管理</h1><span>维护题目、标准答案和选项</span></div>
      <div class="toolbar-actions">
        <el-button :loading="exporting" @click="exportQuestions">导出题库</el-button>
        <el-button @click="$router.push('/admin/question-import')">批量导入</el-button>
        <el-button type="primary" @click="openCreate">新增题目</el-button>
      </div>
    </div>

    <el-form class="filters" inline @submit.prevent="search">
      <el-form-item label="科目">
        <el-select v-model="query.subjectId" clearable filterable placeholder="全部科目" @change="onQuerySubjectChange">
          <el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="章节">
        <el-select v-model="query.chapterId" clearable filterable placeholder="全部章节" @change="onQueryChapterChange">
          <el-option v-for="item in queryChapters" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="知识点">
        <el-select v-model="query.knowledgePointId" clearable filterable placeholder="全部知识点">
          <el-option v-for="item in queryKnowledgePoints" :key="item.id" :label="item.name" :value="item.id" />
        </el-select>
      </el-form-item>
      <el-form-item label="题型">
        <el-select v-model="query.type" clearable placeholder="全部题型">
          <el-option v-for="(label, value) in typeLabels" :key="value" :label="label" :value="value" />
        </el-select>
      </el-form-item>
      <el-form-item label="难度">
        <el-select v-model="query.difficulty" clearable placeholder="全部难度">
          <el-option v-for="(label, value) in difficultyLabels" :key="value" :label="label" :value="Number(value)" />
        </el-select>
      </el-form-item>
      <el-form-item label="状态">
        <el-select v-model="query.status" clearable placeholder="全部状态">
          <el-option label="启用" :value="1" /><el-option label="禁用" :value="0" />
        </el-select>
      </el-form-item>
      <el-form-item><el-input v-model.trim="query.keyword" clearable placeholder="搜索题干" /></el-form-item>
      <el-button type="primary" native-type="submit">查询</el-button>
    </el-form>

    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="content" label="题干" min-width="260" show-overflow-tooltip />
      <el-table-column label="知识点" min-width="180" show-overflow-tooltip>
        <template #default="{ row }">{{ row.subjectName }} / {{ row.chapterName }} / {{ row.knowledgePointName }}</template>
      </el-table-column>
      <el-table-column label="题型" width="90"><template #default="{ row }">{{ typeLabels[row.type] }}</template></el-table-column>
      <el-table-column label="难度" width="80"><template #default="{ row }">{{ difficultyLabels[row.difficulty] }}</template></el-table-column>
      <el-table-column prop="answer" label="答案" width="100" />
      <el-table-column label="状态" width="80"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag></template></el-table-column>
      <el-table-column label="创建时间" width="170"><template #default="{ row }">{{ formatTime(row.createdAt) }}</template></el-table-column>
      <el-table-column label="操作" width="250" fixed="right">
        <template #default="{ row }">
          <el-button link @click="openDetail(row)">查看</el-button>
          <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
          <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="changeStatus(row)">{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
          <el-button link type="danger" @click="remove(row)">删除</el-button>
        </template>
      </el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.page" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
  </el-card>

  <el-drawer v-model="drawerVisible" :title="editingId ? '编辑题目' : '新增题目'" size="min(92vw, 760px)" destroy-on-close>
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <div class="form-grid">
        <el-form-item label="科目" prop="subjectId"><el-select v-model="form.subjectId" filterable @change="onFormSubjectChange"><el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="章节" prop="chapterId"><el-select v-model="form.chapterId" filterable @change="onFormChapterChange"><el-option v-for="item in formChapters" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="知识点" prop="knowledgePointId"><el-select v-model="form.knowledgePointId" filterable><el-option v-for="item in formKnowledgePoints" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
        <el-form-item label="题型" prop="type"><el-select v-model="form.type" @change="onTypeChange"><el-option v-for="(label, value) in typeLabels" :key="value" :label="label" :value="value" /></el-select></el-form-item>
        <el-form-item label="难度" prop="difficulty"><el-radio-group v-model="form.difficulty"><el-radio :value="1">简单</el-radio><el-radio :value="2">中等</el-radio><el-radio :value="3">困难</el-radio></el-radio-group></el-form-item>
        <el-form-item label="状态" prop="status"><el-radio-group v-model="form.status"><el-radio :value="1">启用</el-radio><el-radio :value="0">禁用</el-radio></el-radio-group></el-form-item>
      </div>
      <el-form-item label="题干" prop="content"><el-input v-model="form.content" type="textarea" :rows="4" maxlength="10000" show-word-limit /></el-form-item>
      <el-form-item label="题目解析"><el-input v-model="form.analysis" type="textarea" :rows="3" maxlength="10000" show-word-limit /></el-form-item>

      <template v-if="form.type !== 'JUDGE'">
        <div class="option-header"><strong>选项与正确答案</strong><el-button link type="primary" @click="addOption">添加选项</el-button></div>
        <el-checkbox-group v-if="form.type === 'MULTIPLE'" v-model="multipleAnswers">
          <div v-for="(option, index) in form.options" :key="option.optionKey" class="option-row">
            <el-checkbox :value="option.optionKey">正确</el-checkbox><span class="option-key">{{ option.optionKey }}</span>
            <el-input v-model="option.content" :placeholder="`请输入选项 ${option.optionKey}`" />
            <el-button link type="danger" @click="removeOption(index)">删除</el-button>
          </div>
        </el-checkbox-group>
        <div v-else>
          <div v-for="(option, index) in form.options" :key="option.optionKey" class="option-row">
            <el-radio v-model="form.answer" :value="option.optionKey">正确</el-radio><span class="option-key">{{ option.optionKey }}</span>
            <el-input v-model="option.content" :placeholder="`请输入选项 ${option.optionKey}`" />
            <el-button link type="danger" @click="removeOption(index)">删除</el-button>
          </div>
        </div>
      </template>
      <el-form-item v-else label="正确答案">
        <el-radio-group v-model="form.answer"><el-radio value="TRUE">正确</el-radio><el-radio value="FALSE">错误</el-radio></el-radio-group>
      </el-form-item>
    </el-form>
    <template #footer><el-button @click="drawerVisible = false">取消</el-button><el-button type="primary" :loading="saving" @click="save">保存</el-button></template>
  </el-drawer>

  <el-dialog v-model="detailVisible" title="题目详情" width="720px">
    <el-descriptions v-if="detail" :column="2" border>
      <el-descriptions-item label="所属位置" :span="2">{{ detail.subjectName }} / {{ detail.chapterName }} / {{ detail.knowledgePointName }}</el-descriptions-item>
      <el-descriptions-item label="题型">{{ typeLabels[detail.type] }}</el-descriptions-item><el-descriptions-item label="难度">{{ difficultyLabels[detail.difficulty] }}</el-descriptions-item>
      <el-descriptions-item label="题干" :span="2">{{ detail.content }}</el-descriptions-item>
      <el-descriptions-item label="正确答案">{{ detail.answer }}</el-descriptions-item><el-descriptions-item label="创建者ID">{{ detail.createdBy }}</el-descriptions-item>
      <el-descriptions-item label="选项" :span="2"><div v-for="option in detail.options" :key="option.id">{{ option.optionKey }}. {{ option.content }}</div><span v-if="!detail.options.length">无</span></el-descriptions-item>
      <el-descriptions-item label="解析" :span="2">{{ detail.analysis || '暂无解析' }}</el-descriptions-item>
      <el-descriptions-item label="创建时间">{{ formatTime(detail.createdAt) }}</el-descriptions-item><el-descriptions-item label="更新时间">{{ formatTime(detail.updatedAt) }}</el-descriptions-item>
    </el-descriptions>
  </el-dialog>
</template>

<style scoped>
.toolbar{display:flex;justify-content:space-between;align-items:center;gap:16px;margin-bottom:22px}.toolbar h1{margin:0 0 6px}.toolbar span{color:var(--color-text-secondary)}.toolbar-actions{display:flex;flex-wrap:wrap;gap:8px}.toolbar-actions :deep(.el-button + .el-button){margin-left:0}.filters :deep(.el-select){width:150px}.filters :deep(.el-input){width:190px}.el-pagination{margin-top:20px;justify-content:flex-end}.form-grid{display:grid;grid-template-columns:1fr 1fr;column-gap:16px}.form-grid :deep(.el-select){width:100%}.option-header{display:flex;justify-content:space-between;align-items:center;margin:8px 0 12px 90px}.option-row{display:grid;grid-template-columns:62px 34px 1fr 42px;align-items:center;gap:8px;margin:10px 0 10px 90px}.option-key{display:grid;place-items:center;width:30px;height:30px;border:1px solid var(--color-border);border-radius:4px;font-weight:700}@media(max-width:900px){.toolbar{align-items:flex-start;flex-direction:column}.form-grid{grid-template-columns:1fr}.option-row{margin-left:0}.option-header{margin-left:0}}
</style>
