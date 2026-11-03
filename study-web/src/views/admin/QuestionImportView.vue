<script setup>
import { computed, ref } from 'vue'
import { ElMessage, ElMessageBox } from 'element-plus'
import { useRouter } from 'vue-router'
import {
  downloadQuestionImportTemplate,
  executeQuestionImport,
  validateQuestionImport
} from '@/api/question'
import { downloadBlobResponse, getDownloadErrorMessage } from '@/utils/download'

const router = useRouter()
const uploadRef = ref()
const fileList = ref([])
const selectedFile = ref(null)
const validation = ref(null)
const validationFileKey = ref('')
const successResult = ref(null)
const downloading = ref(false)
const validating = ref(false)
const importing = ref(false)

const fileKey = computed(() => selectedFile.value
  ? `${selectedFile.value.name}:${selectedFile.value.size}:${selectedFile.value.lastModified}`
  : '')
const canValidate = computed(() => Boolean(selectedFile.value) && !validating.value && !importing.value)
const canExecute = computed(() => Boolean(
  selectedFile.value && validation.value?.valid
  && validationFileKey.value === fileKey.value
  && !validating.value && !importing.value
))
const fileSize = computed(() => selectedFile.value
  ? `${(selectedFile.value.size / 1024).toFixed(1)} KB`
  : '-')

function clearResult() {
  validation.value = null
  validationFileKey.value = ''
  successResult.value = null
}

function validateLocalFile(file) {
  const name = file?.name || ''
  if (!name.toLowerCase().endsWith('.xlsx')) {
    ElMessage.error('只接受 .xlsx 文件，不支持 .xls')
    return false
  }
  if (!file.size) {
    ElMessage.error('不能上传空文件')
    return false
  }
  if (file.size > 5 * 1024 * 1024) {
    ElMessage.error('文件不能超过 5 MB')
    return false
  }
  return true
}

function onFileChange(uploadFile) {
  const raw = uploadFile.raw
  if (!validateLocalFile(raw)) {
    uploadRef.value?.clearFiles()
    fileList.value = []
    selectedFile.value = null
    clearResult()
    return
  }
  fileList.value = [uploadFile]
  selectedFile.value = raw
  clearResult()
}

function onFileRemove() {
  fileList.value = []
  selectedFile.value = null
  clearResult()
}

function onExceed(files) {
  const raw = files[0]
  if (!validateLocalFile(raw)) return
  uploadRef.value?.clearFiles()
  fileList.value = []
  selectedFile.value = null
  clearResult()
  uploadRef.value?.handleStart(raw)
}

async function downloadTemplate() {
  if (downloading.value) return
  downloading.value = true
  try {
    const response = await downloadQuestionImportTemplate()
    downloadBlobResponse(response, '题库批量导入模板.xlsx')
    ElMessage.success('动态模板已下载')
  } catch (error) {
    ElMessage.error(await getDownloadErrorMessage(error, '模板下载失败'))
  } finally {
    downloading.value = false
  }
}

async function validateFile() {
  if (!canValidate.value) return
  validating.value = true
  successResult.value = null
  const currentKey = fileKey.value
  try {
    const response = await validateQuestionImport(selectedFile.value)
    if (currentKey !== fileKey.value) return
    validation.value = response.data
    validationFileKey.value = currentKey
    if (response.data.valid) {
      ElMessage.success(`预校验通过，共 ${response.data.validRows} 道题目可导入`)
    } else {
      ElMessage.error(`预校验未通过，共 ${response.data.invalidRows} 行存在错误`)
    }
  } catch (error) {
    validation.value = null
    validationFileKey.value = ''
    ElMessage.error(error.response?.data?.message || '预校验失败')
  } finally {
    validating.value = false
  }
}

async function executeImport() {
  if (!canExecute.value) return
  await ElMessageBox.confirm(
    `将重新解析当前文件，并在一个事务中导入 ${validation.value.validRows} 道题目。确认继续吗？`,
    '确认批量导入',
    { type: 'warning', confirmButtonText: '确认导入', cancelButtonText: '取消' }
  )
  importing.value = true
  try {
    const response = await executeQuestionImport(selectedFile.value)
    successResult.value = response.data
    ElMessage.success('题库批量导入成功')
  } catch (error) {
    ElMessage.error(error.response?.data?.message || '导入失败，数据库未写入')
  } finally {
    importing.value = false
  }
}

function continueImport() {
  uploadRef.value?.clearFiles()
  onFileRemove()
}
</script>

<template>
  <section class="import-page">
    <header class="page-header">
      <div>
        <p>QUESTION BANK IMPORT</p>
        <h1>题库批量导入</h1>
        <span>先下载动态模板并完整预校验；只有零错误时才能执行单事务导入。</span>
      </div>
      <el-button :loading="downloading" @click="downloadTemplate">下载 Excel 模板</el-button>
    </header>

    <el-alert
      title="高风险操作：正式导入会重新读取原始文件并重新校验；任意题目或选项失败时整批回滚。"
      type="warning"
      :closable="false"
      show-icon
    />

    <el-card shadow="never" class="step-card">
      <template #header><strong>1. 选择 .xlsx 文件</strong></template>
      <el-upload
        ref="uploadRef"
        v-model:file-list="fileList"
        drag
        accept=".xlsx"
        :auto-upload="false"
        :limit="1"
        :disabled="validating || importing"
        :on-change="onFileChange"
        :on-remove="onFileRemove"
        :on-exceed="onExceed"
      >
        <div class="upload-copy">
          <strong>拖拽文件到此处，或点击选择</strong>
          <span>仅支持系统模板生成的 .xlsx，最大 5 MB、1000 道有效题目</span>
        </div>
      </el-upload>
      <div v-if="selectedFile" class="file-meta">
        <span><b>文件：</b>{{ selectedFile.name }}</span>
        <span><b>大小：</b>{{ fileSize }}</span>
      </div>
      <div class="action-row">
        <el-button type="primary" :disabled="!canValidate" :loading="validating" @click="validateFile">
          预校验
        </el-button>
        <span>更换或移除文件后，旧校验结果会立即失效。</span>
      </div>
    </el-card>

    <el-card v-if="validation" shadow="never" class="step-card">
      <template #header>
        <div class="result-heading">
          <strong>2. 预校验结果</strong>
          <el-tag :type="validation.valid ? 'success' : 'danger'">
            {{ validation.valid ? '可以导入' : '禁止导入' }}
          </el-tag>
        </div>
      </template>

      <div class="summary-grid">
        <div><span>检测题目</span><strong>{{ validation.totalRows }}</strong></div>
        <div><span>有效题目</span><strong class="success">{{ validation.validRows }}</strong></div>
        <div><span>错误行</span><strong class="danger">{{ validation.invalidRows }}</strong></div>
        <div><span>重复行</span><strong>{{ validation.duplicateRows }}</strong></div>
        <div><span>警告</span><strong>{{ validation.warningCount }}</strong></div>
      </div>

      <template v-if="validation.errors.length">
        <h2>错误明细</h2>
        <el-table :data="validation.errors" border max-height="360">
          <el-table-column prop="rowNumber" label="Excel 行号" width="110">
            <template #default="{ row }">{{ row.rowNumber || '文件级' }}</template>
          </el-table-column>
          <el-table-column prop="field" label="字段" width="130" />
          <el-table-column prop="message" label="原因" min-width="340" />
        </el-table>
        <p v-if="validation.omittedErrorCount" class="table-note">
          另有 {{ validation.omittedErrorCount }} 条错误未展示，请先修正当前错误后重新预校验。
        </p>
      </template>

      <template v-if="validation.warnings.length">
        <h2>警告</h2>
        <el-table :data="validation.warnings" border max-height="260">
          <el-table-column prop="rowNumber" label="Excel 行号" width="110" />
          <el-table-column prop="field" label="字段" width="130" />
          <el-table-column prop="message" label="说明" min-width="340" />
        </el-table>
      </template>

      <template v-if="validation.previewRows.length">
        <h2>规范化预览（最多 100 行）</h2>
        <el-table :data="validation.previewRows" border max-height="460">
          <el-table-column prop="rowNumber" label="行号" width="72" />
          <el-table-column label="层级" min-width="250" show-overflow-tooltip>
            <template #default="{ row }">{{ row.subjectName }} / {{ row.chapterName }} / {{ row.knowledgePointName }}</template>
          </el-table-column>
          <el-table-column prop="type" label="题型" width="100" />
          <el-table-column prop="content" label="题干" min-width="260" show-overflow-tooltip />
          <el-table-column prop="answer" label="答案" width="100" />
          <el-table-column prop="optionCount" label="选项数" width="90" />
        </el-table>
      </template>

      <div class="execute-row">
        <div>
          <strong>3. 正式导入</strong>
          <p>后端不会信任当前页面状态，将再次完整解析和校验同一个文件。</p>
        </div>
        <el-button type="danger" :disabled="!canExecute" :loading="importing" @click="executeImport">
          确认导入
        </el-button>
      </div>
    </el-card>

    <el-result v-if="successResult" icon="success" title="导入成功" sub-title="所有数据已在同一事务中写入">
      <template #extra>
        <p>新增题目 {{ successResult.questionCount }} 道，新增选项 {{ successResult.optionCount }} 个。</p>
        <el-button type="primary" @click="router.push('/admin/questions')">返回题库管理</el-button>
        <el-button @click="continueImport">继续导入</el-button>
      </template>
    </el-result>
  </section>
</template>

<style scoped>
.import-page{max-width:1180px;margin:0 auto}.page-header{display:flex;align-items:center;justify-content:space-between;gap:20px;margin-bottom:18px;padding:24px 26px;border:1px solid var(--color-hero-border);border-radius:var(--radius-lg);background:var(--gradient-hero)}.page-header p{margin:0 0 6px;color:var(--color-primary);font-size:12px;font-weight:800;letter-spacing:1.5px}.page-header h1{margin:0 0 8px;font-size:30px}.page-header span,.action-row span{color:var(--color-text-secondary)}.step-card{margin-top:18px}.upload-copy{display:flex;flex-direction:column;gap:8px;color:var(--color-text-secondary)}.upload-copy strong{color:var(--color-text);font-size:16px}.file-meta{display:flex;flex-wrap:wrap;gap:24px;margin-top:14px;padding:12px 14px;border-radius:var(--radius-sm);background:var(--color-surface-muted)}.action-row,.execute-row,.result-heading{display:flex;align-items:center;justify-content:space-between;gap:16px}.action-row{justify-content:flex-start;margin-top:16px}.summary-grid{display:grid;grid-template-columns:repeat(5,minmax(0,1fr));gap:12px;margin-bottom:24px}.summary-grid>div{display:flex;flex-direction:column;gap:7px;padding:15px;border:1px solid var(--color-border);border-radius:var(--radius-sm);background:var(--color-surface-muted)}.summary-grid span{color:var(--color-text-secondary);font-size:13px}.summary-grid strong{font-size:24px}.success{color:var(--color-success)}.danger{color:var(--color-danger)}h2{margin:24px 0 12px;font-size:17px}.table-note{color:var(--color-warning)}.execute-row{margin-top:24px;padding-top:20px;border-top:1px solid var(--color-border)}.execute-row p{margin:6px 0 0;color:var(--color-text-secondary)}@media(max-width:760px){.page-header,.execute-row{align-items:flex-start;flex-direction:column}.page-header{padding:20px 18px}.page-header h1{font-size:26px}.summary-grid{grid-template-columns:repeat(2,minmax(0,1fr))}.action-row{align-items:flex-start;flex-direction:column}.execute-row .el-button{width:100%}}
</style>
