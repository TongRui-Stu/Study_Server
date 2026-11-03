<script setup>
import { onMounted, reactive, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { createSubject, deleteSubject, getAdminSubjectPage, updateSubject, updateSubjectStatus } from '@/api/subject'

const loading = ref(false)
const saving = ref(false)
const rows = ref([])
const total = ref(0)
const query = reactive({ current: 1, size: 10, keyword: '' })
const dialogVisible = ref(false)
const editingId = ref(null)
const formRef = ref()
const form = reactive({ name: '', description: '', sortOrder: 0, status: 1 })
const rules = {
  name: [{ required: true, message: '请输入科目名称', trigger: 'blur' }],
  sortOrder: [{ required: true, message: '请输入排序', trigger: 'blur' }]
}

async function load() {
  loading.value = true
  try {
    const response = await getAdminSubjectPage(query)
    rows.value = response.data.records
    total.value = response.data.total
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '获取科目失败')
  } finally { loading.value = false }
}

function openCreate() {
  editingId.value = null
  Object.assign(form, { name: '', description: '', sortOrder: 0, status: 1 })
  dialogVisible.value = true
}

function openEdit(row) {
  editingId.value = row.id
  Object.assign(form, row)
  dialogVisible.value = true
}

async function save() {
  if (saving.value) return
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  saving.value = true
  try {
    if (editingId.value) await updateSubject(editingId.value, form)
    else await createSubject(form)
    ElMessage.success(editingId.value ? '修改成功' : '新增成功')
    dialogVisible.value = false
    load()
  } catch (error) { ElMessage.error(error.response?.data?.message || '保存失败') }
  finally { saving.value = false }
}

async function changeStatus(row) {
  try {
    await updateSubjectStatus(row.id, row.status === 1 ? 0 : 1)
    ElMessage.success('状态已更新')
    load()
  } catch (error) { ElMessage.error(error.response?.data?.message || '状态更新失败') }
}

async function remove(row) {
  try {
    await ElMessageBox.confirm(`确定删除科目“${row.name}”吗？`, '删除确认', { type: 'warning' })
    await deleteSubject(row.id)
    ElMessage.success('删除成功')
    load()
  } catch (error) {
    if (error !== 'cancel' && error !== 'close') ElMessage.error(error.response?.data?.message || '删除失败')
  }
}

onMounted(load)
</script>

<template>
  <el-card shadow="never">
    <div class="toolbar">
      <div><h1>科目管理</h1><span>维护学习科目及启用状态</span></div>
      <el-button type="primary" @click="openCreate">新增科目</el-button>
    </div>
    <el-form inline @submit.prevent="query.current = 1; load()">
      <el-form-item><el-input v-model.trim="query.keyword" placeholder="搜索科目名称" clearable /></el-form-item>
      <el-button type="primary" native-type="submit">查询</el-button>
    </el-form>
    <el-table v-loading="loading" :data="rows" border>
      <el-table-column prop="id" label="ID" width="70" />
      <el-table-column prop="name" label="名称" min-width="150" />
      <el-table-column prop="description" label="描述" min-width="220" show-overflow-tooltip />
      <el-table-column prop="sortOrder" label="排序" width="80" />
      <el-table-column label="状态" width="90"><template #default="{ row }"><el-tag :type="row.status === 1 ? 'success' : 'info'">{{ row.status === 1 ? '启用' : '禁用' }}</el-tag></template></el-table-column>
      <el-table-column label="操作" width="230" fixed="right"><template #default="{ row }">
        <el-button link type="primary" @click="openEdit(row)">编辑</el-button>
        <el-button link :type="row.status === 1 ? 'warning' : 'success'" @click="changeStatus(row)">{{ row.status === 1 ? '禁用' : '启用' }}</el-button>
        <el-button link type="danger" @click="remove(row)">删除</el-button>
      </template></el-table-column>
    </el-table>
    <el-pagination v-model:current-page="query.current" v-model:page-size="query.size" :total="total" layout="total, sizes, prev, pager, next" @change="load" />
  </el-card>

  <el-dialog v-model="dialogVisible" :title="editingId ? '编辑科目' : '新增科目'" width="520px">
    <el-form ref="formRef" :model="form" :rules="rules" label-width="80px">
      <el-form-item label="名称" prop="name"><el-input v-model.trim="form.name" maxlength="100" /></el-form-item>
      <el-form-item label="描述"><el-input v-model="form.description" type="textarea" maxlength="500" show-word-limit /></el-form-item>
      <el-form-item label="排序" prop="sortOrder"><el-input-number v-model="form.sortOrder" :min="0" /></el-form-item>
      <el-form-item label="状态"><el-radio-group v-model="form.status"><el-radio :value="1">启用</el-radio><el-radio :value="0">禁用</el-radio></el-radio-group></el-form-item>
    </el-form>
    <template #footer><el-button :disabled="saving" @click="dialogVisible = false">取消</el-button><el-button type="primary" :loading="saving" :disabled="saving" @click="save">保存</el-button></template>
  </el-dialog>
</template>

<style scoped>
.toolbar { display: flex; justify-content: space-between; align-items: center; margin-bottom: 22px; }
.toolbar h1 { margin: 0 0 6px; }.toolbar span { color: var(--color-text-secondary); }.el-pagination { margin-top: 20px; justify-content: flex-end; }
</style>
