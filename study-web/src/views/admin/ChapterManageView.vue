<script setup>
import { computed, onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getAdminSubjectPage } from '@/api/subject'
import { createChapter, deleteChapter, getAdminChapterPage, updateChapter, updateChapterStatus } from '@/api/chapter'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const subjects = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, subjectId: null, keyword: '' })
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({ subjectId: null, name: '', description: '', sortOrder: 0, status: 1 })
const rules = {
  subjectId: [{ required: true, message: '请选择科目', trigger: 'change' }],
  name: [{ required: true, message: '请输入章节名称', trigger: 'blur' }]
}
const subjectMap = computed(() => Object.fromEntries(subjects.value.map(item => [item.id, item.name])))

async function loadSubjects() {
  const response = await getAdminSubjectPage({ current: 1, size: 100 })
  subjects.value = response.data.records
}
async function load() {
  loading.value = true
  try {
    const params = { ...query }
    if (!params.subjectId) delete params.subjectId
    const response = await getAdminChapterPage(params)
    rows.value = response.data.records; total.value = response.data.total
  } catch (error) { ElMessage.error(error.response?.data?.message || '获取章节失败') }
  finally { loading.value = false }
}
function openCreate() {
  editingId.value = null
  Object.assign(form, { subjectId: query.subjectId || null, name: '', description: '', sortOrder: 0, status: 1 })
  dialogVisible.value = true
}
function openEdit(row) { editingId.value = row.id; Object.assign(form, row); dialogVisible.value = true }
async function save() {
  if (saving.value) return
  const valid = await formRef.value.validate().catch(() => false); if (!valid) return
  saving.value = true
  try {
    if (editingId.value) await updateChapter(editingId.value, form); else await createChapter(form)
    ElMessage.success(editingId.value ? '修改成功' : '新增成功'); dialogVisible.value = false; load()
  } catch (error) { ElMessage.error(error.response?.data?.message || '保存失败') }
  finally { saving.value = false }
}
async function changeStatus(row) {
  try { await updateChapterStatus(row.id, row.status === 1 ? 0 : 1); ElMessage.success('状态已更新'); load() }
  catch (error) { ElMessage.error(error.response?.data?.message || '状态更新失败') }
}
async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除章节“${row.name}”吗？`, '删除确认', { type: 'warning' })
    await deleteChapter(row.id); ElMessage.success('删除成功'); load()
  } catch (error) { if (error !== 'cancel' && error !== 'close') ElMessage.error(error.response?.data?.message || '删除失败') }
}
onMounted(async () => { try { await loadSubjects(); await load() } catch (error) { ElMessage.error(error.response?.data?.message || '初始化失败') } })
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar"><div><h1>章节管理</h1><span>按科目维护章节</span></div><el-button type="primary" @click="openCreate">新增章节</el-button></div>
    <el-form inline @submit.prevent="query.current = 1; load()">
      <el-form-item label="科目"><el-select v-model="query.subjectId" placeholder="全部科目" clearable style="width: 180px"><el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
      <el-form-item><el-input v-model.trim="query.keyword" placeholder="搜索章节名称" clearable /></el-form-item>
      <el-button type="primary" native-type="submit">查询</el-button>
    </el-form>
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="id" label="ID" width="70" /><el-table-column label="所属科目" width="150"><template #default="{ row }">{{ subjectMap[row.subjectId] || row.subjectId }}</template></el-table-column>
      <el-table-column prop="name" label="章节名称" min-width="160" /><el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip /><el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="230" fixed="right"><template #default="{ row }"><el-button link type="primary" @click="openEdit(row)">编辑</el-button><el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="changeStatus(row)">{{ row.status === 1 ? '禁用' : '启用' }}</el-button><el-button link type="danger" @click="remove(row)">删除</el-button></template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
  </el-card>
  <el-dialog v-model="dialogVisible" :title="editingId ? '编辑章节' : '新增章节'" width="540px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="90px">
      <el-form-item label="所属科目" prop="subjectId"><el-select v-model="form.subjectId" style="width: 100%"><el-option v-for="item in subjects" :key="item.id" :label="item.name" :value="item.id" /></el-select></el-form-item>
      <el-form-item label="名称" prop="name"><el-input v-model.trim="form.name" maxlength="100" /></el-form-item><el-form-item label="描述"><el-input v-model="form.description" type="textarea" maxlength="500" show-word-limit /></el-form-item>
      <el-form-item label="排序"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item><el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio :value="1">启用</el-radio><el-radio :value="0">禁用</el-radio></el-radio-group></el-form-item>
    </el-form><template #footer><el-button :disabled="saving" @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="saving" @click="save">保存</el-button></template>
  </el-dialog>
</template>

<style scoped>.toolbar{display:flex;justify-content:space-between;align-items:center;margin-bottom:22px}.toolbar h1{margin:0 0 6px}.toolbar span{color:var(--color-text-secondary)}.el-pagination{margin-top:20px;justify-content:flex-end}</style>
