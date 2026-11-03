<script setup>
import { computed, nextTick, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ElMessage, ElMessageBox } from 'element-plus'
import { getPracticeQuestions, submitPracticeAnswer } from '@/api/practice'
import { addFavorite, removeFavorite } from '@/api/favorite'
import { streamQuestionAiChat } from '@/api/ai'
import AiMarkdown from '@/components/AiMarkdown.vue'
import AiMessageSource from '@/components/AiMessageSource.vue'
import MathText from '@/components/MathText.vue'
import QuestionAnswerCard from '@/components/QuestionAnswerCard.vue'
import { personalQuestionFilters } from '@/utils/personalQuestionFilters'
import { downloadConversationMarkdown, hasExportableMessages } from '@/utils/markdownConversation'
import { createAiAutoScroll } from '@/utils/aiAutoScroll'
import { createAiStreamSession, finalizeInterruptedAiTurn, isAbortError } from '@/utils/aiStreamSession'

const route = useRoute()
const router = useRouter()
const loading = ref(true)
const errorMessage = ref('')
const knowledgePointName = ref('知识点练习')
const activeSource = ref('KNOWLEDGE_POINT')
const questions = ref([])
const currentIndex = ref(0)
const draftAnswers = ref({})
const submittedResults = ref({})
const submittingQuestionIds = ref(new Set())
const favoritePendingQuestionIds = ref(new Set())
const aiMessages = ref([])
const aiInput = ref('')
const aiLoading = ref(false)
const aiError = ref('')
const aiMessageList = ref(null)
const questionCard = ref(null)
const aiStream = createAiStreamSession()
const aiScroll = createAiAutoScroll(() => aiMessageList.value, { afterUpdate: nextTick })
let activeAiRequest = null
let aiSessionVersion = 0
let questionLoadVersion = 0
let practiceSessionVersion = 0

const currentQuestion = computed(() => questions.value[currentIndex.value] || null)
const currentQuestionId = computed(() => currentQuestion.value?.id)
const currentDraft = computed(() => currentQuestionId.value == null
  ? null
  : draftAnswers.value[currentQuestionId.value])
const singleAnswer = computed({
  get: () => typeof currentDraft.value === 'string' ? currentDraft.value : '',
  set: value => saveDraft(currentQuestionId.value, value)
})
const multipleAnswer = computed({
  get: () => Array.isArray(currentDraft.value) ? currentDraft.value : [],
  set: value => saveDraft(currentQuestionId.value, [...value])
})
const result = computed(() => currentQuestionId.value == null
  ? null
  : submittedResults.value[currentQuestionId.value] || null)
const submitted = computed(() => Boolean(result.value))
const submitting = computed(() => currentQuestionId.value != null
  && submittingQuestionIds.value.has(currentQuestionId.value))
const favoritePending = computed(() => currentQuestionId.value != null
  && favoritePendingQuestionIds.value.has(currentQuestionId.value))
const progressText = computed(() => questions.value.length ? `${currentIndex.value + 1} / ${questions.value.length}` : '')
const difficultyLabel = computed(() => ({ 1: '简单', 2: '中等', 3: '困难' })[currentQuestion.value?.difficulty] || '-')
const typeLabel = computed(() => ({ SINGLE: '单选题', MULTIPLE: '多选题', JUDGE: '判断题' })[currentQuestion.value?.type] || '-')
const hasAnswer = computed(() => currentQuestion.value?.type === 'MULTIPLE'
  ? multipleAnswer.value.length > 0
  : Boolean(singleAnswer.value))
const questionStates = computed(() => Object.fromEntries(questions.value.map(question => {
  const questionResult = submittedResults.value[question.id]
  return [question.id, {
    submitted: Boolean(questionResult),
    correct: questionResult?.isCorrect === true
  }]
})))
const unansweredCount = computed(() => questions.value.reduce((count, question) => (
  submittedResults.value[question.id] ? count : count + 1
), 0))
const canExportAiConversation = computed(() => (
  submitted.value && hasExportableMessages(aiMessages.value)
))
const requestedSource = computed(() => route.path === '/practice'
  ? String(route.query.source || '').trim().toUpperCase()
  : 'KNOWLEDGE_POINT')
const personalFilters = computed(() => personalQuestionFilters(route.query))
const returnLocation = computed(() => ({ path: sourceMeta.value.backPath, query: personalFilters.value }))
const emptyPracticeText = computed(() => activeSource.value !== 'KNOWLEDGE_POINT'
  && Object.keys(personalFilters.value).length ? '当前条件下暂无可练习题目' : sourceMeta.value.emptyText)
const sourceMeta = computed(() => ({
  WRONG: {
    label: '错题专项练习',
    breadcrumb: '错题本',
    backPath: '/wrong-questions',
    backLabel: '返回错题本',
    emptyText: '当前没有可练习的错题'
  },
  FAVORITE: {
    label: '收藏专项练习',
    breadcrumb: '我的收藏',
    backPath: '/favorites',
    backLabel: '返回我的收藏',
    emptyText: '当前没有可练习的收藏题目'
  },
  KNOWLEDGE_POINT: {
    label: '知识点练习',
    breadcrumb: '',
    backPath: '',
    backLabel: '返回知识点',
    emptyText: '该知识点暂无可用题目'
  }
})[activeSource.value] || {
  label: '专项练习', breadcrumb: '', backPath: '/home', backLabel: '返回首页',
  emptyText: '当前没有可练习题目'
})

async function loadQuestions() {
  const loadVersion = ++questionLoadVersion
  loading.value = true
  errorMessage.value = ''
  currentIndex.value = 0
  questions.value = []
  resetPracticeSession()
  activeSource.value = requestedSource.value || 'KNOWLEDGE_POINT'
  try {
    const params = { source: activeSource.value }
    if (activeSource.value === 'KNOWLEDGE_POINT') {
      params.knowledgePointId = route.params.knowledgePointId
    } else {
      Object.assign(params, personalFilters.value)
    }
    const response = await getPracticeQuestions(params)
    if (loadVersion !== questionLoadVersion) return
    activeSource.value = response.data.source
    knowledgePointName.value = response.data.knowledgePointName
    questions.value = response.data.questions || []
    if (route.query.questionId && questions.value.length) {
      const targetIndex = questions.value.findIndex(question => question.id === Number(route.query.questionId))
      if (targetIndex >= 0) {
        currentIndex.value = targetIndex
      } else {
        const scope = activeSource.value === 'WRONG' ? '错题集合'
          : activeSource.value === 'FAVORITE' ? '收藏集合' : '本知识点'
        ElMessage.warning(`指定题目已不在当前${scope}，已从第一题开始`)
      }
    }
  } catch (error) {
    if (loadVersion !== questionLoadVersion) return
    errorMessage.value = error.response?.data?.message || '获取练习题目失败'
  } finally {
    if (loadVersion === questionLoadVersion) loading.value = false
  }
}

watch(() => route.fullPath, loadQuestions, { immediate: true })
onBeforeUnmount(() => {
  aiStream.cancel('unmounted')
  aiScroll.dispose()
})

async function toggleFavorite() {
  if (!submitted.value || !result.value || favoritePending.value) return
  const questionId = currentQuestion.value.id
  const wasFavorited = result.value.favorited
  const sessionVersion = practiceSessionVersion
  setPending(favoritePendingQuestionIds, questionId, true)
  try {
    if (wasFavorited) {
      await removeFavorite(questionId)
      if (sessionVersion !== practiceSessionVersion) return
      updateSubmittedResult(questionId, { favorited: false })
      ElMessage.success('已取消收藏')
    } else {
      await addFavorite(questionId)
      if (sessionVersion !== practiceSessionVersion) return
      updateSubmittedResult(questionId, { favorited: true })
      ElMessage.success('收藏成功')
    }
  } catch (error) {
    if (sessionVersion === practiceSessionVersion) {
      ElMessage.error(error.response?.data?.message || '收藏操作失败')
    }
  } finally {
    if (sessionVersion === practiceSessionVersion) {
      setPending(favoritePendingQuestionIds, questionId, false)
    }
  }
}

async function submitAnswer() {
  if (submitting.value || submitted.value || !hasAnswer.value) return
  const question = currentQuestion.value
  const questionId = question.id
  const answer = answerForQuestion(question)
  const sessionVersion = practiceSessionVersion
  setPending(submittingQuestionIds, questionId, true)
  try {
    const response = await submitPracticeAnswer({
      questionId,
      answer
    })
    if (sessionVersion !== practiceSessionVersion) return
    submittedResults.value = {
      ...submittedResults.value,
      [questionId]: response.data
    }
  } catch (error) {
    if (sessionVersion === practiceSessionVersion) {
      ElMessage.error(error.response?.data?.message || '提交答案失败')
    }
  } finally {
    if (sessionVersion === practiceSessionVersion) {
      setPending(submittingQuestionIds, questionId, false)
    }
  }
}

function saveDraft(questionId, answer) {
  if (questionId == null) return
  draftAnswers.value = {
    ...draftAnswers.value,
    [questionId]: Array.isArray(answer) ? [...answer] : answer
  }
}

function answerForQuestion(question) {
  const answer = draftAnswers.value[question.id]
  if (question.type === 'MULTIPLE') {
    return [...(Array.isArray(answer) ? answer : [])].sort().join(',')
  }
  return typeof answer === 'string' ? answer : ''
}

function setPending(pendingRef, questionId, pending) {
  const next = new Set(pendingRef.value)
  if (pending) next.add(questionId)
  else next.delete(questionId)
  pendingRef.value = next
}

function updateSubmittedResult(questionId, patch) {
  const existing = submittedResults.value[questionId]
  if (!existing) return
  submittedResults.value = {
    ...submittedResults.value,
    [questionId]: { ...existing, ...patch }
  }
}

function resetPracticeSession() {
  practiceSessionVersion += 1
  draftAnswers.value = {}
  submittedResults.value = {}
  submittingQuestionIds.value = new Set()
  favoritePendingQuestionIds.value = new Set()
  resetAiState()
}

function resetAiState() {
  aiStream.cancel('question-changed')
  activeAiRequest = null
  aiSessionVersion += 1
  aiMessages.value = []
  aiInput.value = ''
  aiLoading.value = false
  aiError.value = ''
}

function questionHistory() {
  return aiMessages.value
    .filter(message => typeof message.content === 'string' && message.content.trim())
    .slice(-16)
    .map(({ role, content }) => ({ role, content }))
}

function scrollAiMessagesToBottom(force = false) {
  return aiScroll.request(force)
}

async function askQuestionAi(message = aiInput.value) {
  const content = message.trim()
  if (!submitted.value || !result.value?.answerRecordId || !content || aiLoading.value || content.length > 2000) return

  const questionId = currentQuestionId.value
  const answerRecordId = result.value.answerRecordId
  const sessionVersion = aiSessionVersion
  const history = questionHistory()
  aiMessages.value.push({ role: 'user', content })
  const assistantIndex = aiMessages.value.length
  aiMessages.value.push({ role: 'assistant', content: '', streaming: true })
  aiInput.value = ''
  aiError.value = ''
  aiLoading.value = true
  await scrollAiMessagesToBottom(true)
  const updateAssistant = patch => {
    if (sessionVersion !== aiSessionVersion || currentQuestionId.value !== questionId) return
    const message = aiMessages.value[assistantIndex]
    if (message) aiMessages.value[assistantIndex] = { ...message, ...patch }
  }
  const session = aiStream.begin({
    onSource: event => updateAssistant({ provider: event.provider, model: event.model }),
    onDelta: delta => {
      const current = aiMessages.value[assistantIndex]
      if (current) updateAssistant({ content: current.content + delta })
      scrollAiMessagesToBottom()
    },
    onDone: async () => {
      activeAiRequest = null
      updateAssistant({ streaming: false })
      aiLoading.value = false
      await scrollAiMessagesToBottom()
    },
    onError: async errorMessage => {
      activeAiRequest = null
      finalizeInterruptedAiTurn(aiMessages.value, assistantIndex)
      aiError.value = errorMessage
      aiLoading.value = false
      await scrollAiMessagesToBottom()
    },
    onCancel: reason => {
      activeAiRequest = null
      finalizeInterruptedAiTurn(aiMessages.value, assistantIndex)
      aiLoading.value = false
      if (reason === 'user') aiError.value = '已取消本次生成'
    }
  })
  activeAiRequest = session
  try {
    await streamQuestionAiChat({
      answerRecordId,
      message: content,
      history
    }, {
      signal: session.signal,
      onEvent: session.handleEvent
    })
  } catch (error) {
    if (sessionVersion !== aiSessionVersion || currentQuestionId.value !== questionId) return
    if (!isAbortError(error) && session.isCurrent()) {
      session.cancel('failed')
      aiInput.value = content === '请详细讲解这道题' ? '' : content
      aiError.value = error.message || 'AI 题目辅导暂时不可用，请稍后重试'
    }
  } finally {
    if (activeAiRequest === session && !session.isCurrent()) activeAiRequest = null
  }
}

function cancelAiGeneration() {
  activeAiRequest?.cancel('user')
}

function previousQuestion() {
  navigateToQuestion(currentIndex.value - 1)
}

function nextQuestion() {
  navigateToQuestion(currentIndex.value + 1)
}

function navigateToQuestion(targetIndex) {
  if (!questions.value.length || !Number.isInteger(targetIndex)) return
  const boundedIndex = Math.min(Math.max(targetIndex, 0), questions.value.length - 1)
  if (boundedIndex === currentIndex.value) return
  currentIndex.value = boundedIndex
  resetAiState()
  scrollQuestionToTop()
}

async function scrollQuestionToTop() {
  await nextTick()
  const element = questionCard.value?.$el || questionCard.value
  element?.scrollIntoView({ behavior: 'smooth', block: 'start' })
}

async function finishPractice() {
  if (unansweredCount.value > 0) {
    try {
      await ElMessageBox.confirm(
        `还有 ${unansweredCount.value} 道题未作答，确定结束本次练习吗？`,
        '完成答题',
        {
          confirmButtonText: '确定结束',
          cancelButtonText: '继续答题',
          type: 'warning'
        }
      )
    } catch (error) {
      if (error === 'cancel' || error === 'close') return
      throw error
    }
  }
  backFromPractice()
}

function backFromPractice() {
  if (sourceMeta.value.backPath) {
    router.push(returnLocation.value)
    return
  }
  if (route.query.chapterId) {
    router.push({
      path: `/chapters/${route.query.chapterId}`,
      query: {
        name: route.query.chapterName,
        subjectId: route.query.subjectId,
        subjectName: route.query.subjectName
      }
    })
  } else {
    router.push('/home')
  }
}

function exportAiConversation() {
  if (!canExportAiConversation.value || aiLoading.value) return
  try {
    downloadConversationMarkdown({
      title: '在线学习平台 - AI 题目辅导对话',
      scene: '答题后题目辅导',
      messages: aiMessages.value,
      filenamePrefix: 'AI题目辅导对话'
    })
    ElMessage.success('Markdown 对话已导出')
  } catch (error) {
    ElMessage.error(error.message || 'Markdown 对话导出失败，请稍后重试')
  }
}
</script>

<template>
  <main class="practice-page">
    <el-breadcrumb separator="/" class="practice-breadcrumb">
      <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item v-if="activeSource !== 'KNOWLEDGE_POINT'" :to="returnLocation">{{ sourceMeta.breadcrumb }}</el-breadcrumb-item>
      <el-breadcrumb-item v-if="activeSource === 'KNOWLEDGE_POINT' && route.query.subjectId" :to="{ path: `/subjects/${route.query.subjectId}`, query: { name: route.query.subjectName } }">{{ route.query.subjectName }}</el-breadcrumb-item>
      <el-breadcrumb-item v-if="activeSource === 'KNOWLEDGE_POINT' && route.query.chapterId" :to="{ path: `/chapters/${route.query.chapterId}`, query: { name: route.query.chapterName, subjectId: route.query.subjectId, subjectName: route.query.subjectName } }">{{ route.query.chapterName }}</el-breadcrumb-item>
      <el-breadcrumb-item>练习</el-breadcrumb-item>
    </el-breadcrumb>

    <el-skeleton v-if="loading" :rows="7" animated />
    <el-result v-else-if="errorMessage" icon="warning" title="暂时无法开始练习" :sub-title="errorMessage">
      <template #extra><el-button type="primary" @click="backFromPractice">{{ sourceMeta.backLabel }}</el-button></template>
    </el-result>
    <el-result v-else-if="questions.length === 0" icon="info" title="暂无可练习题目" :sub-title="emptyPracticeText">
      <template #extra><el-button type="primary" @click="backFromPractice">{{ sourceMeta.backLabel }}</el-button></template>
    </el-result>
    <template v-else-if="currentQuestion">
      <header class="practice-header">
        <div>
          <p>{{ sourceMeta.label }}</p>
          <h1>{{ activeSource === 'KNOWLEDGE_POINT' ? knowledgePointName : sourceMeta.label }}</h1>
        </div>
        <el-tag size="large" effect="plain">第 {{ progressText }} 题</el-tag>
      </header>

      <div class="practice-layout">
      <QuestionAnswerCard
        class="answer-card-panel"
        :questions="questions"
        :current-index="currentIndex"
        :question-states="questionStates"
        @select="navigateToQuestion"
      />

      <el-card ref="questionCard" class="question-card" shadow="never">
        <div class="question-meta">
          <el-tag>{{ typeLabel }}</el-tag>
          <el-tag type="info">{{ difficultyLabel }}</el-tag>
        </div>
        <div class="question-title" role="heading" aria-level="2">
          <MathText :content="currentQuestion.content" />
        </div>

        <el-radio-group v-if="currentQuestion.type === 'SINGLE'" v-model="singleAnswer" class="answer-list" :disabled="submitted">
          <el-radio v-for="option in currentQuestion.options" :key="option.optionKey" :value="option.optionKey" border>
            <strong>{{ option.optionKey }}.</strong>
            <MathText class="option-content" :content="option.content" inline />
          </el-radio>
        </el-radio-group>

        <el-checkbox-group v-else-if="currentQuestion.type === 'MULTIPLE'" v-model="multipleAnswer" class="answer-list" :disabled="submitted">
          <el-checkbox v-for="option in currentQuestion.options" :key="option.optionKey" :value="option.optionKey" border>
            <strong>{{ option.optionKey }}.</strong>
            <MathText class="option-content" :content="option.content" inline />
          </el-checkbox>
        </el-checkbox-group>

        <el-radio-group v-else v-model="singleAnswer" class="answer-list judge-list" :disabled="submitted">
          <el-radio value="TRUE" border>正确</el-radio>
          <el-radio value="FALSE" border>错误</el-radio>
        </el-radio-group>

        <el-button
          class="submit-button"
          type="primary"
          size="large"
          :loading="submitting"
          :disabled="!hasAnswer || submitting || submitted"
          @click="submitAnswer"
        >提交答案</el-button>

        <el-alert
          v-if="result"
          class="result-alert"
          :title="result.isCorrect ? '回答正确' : '回答错误'"
          :type="result.isCorrect ? 'success' : 'error'"
          :closable="false"
          show-icon
        >
          <p>你的答案：{{ result.userAnswer }}</p>
          <p>正确答案：{{ result.correctAnswer }}</p>
          <div class="analysis-row">
            <strong>题目解析：</strong>
            <MathText class="analysis-content" :content="result.analysis || '暂无解析'" />
          </div>
        </el-alert>

        <div class="question-footer">
          <el-button v-if="submitted" :loading="favoritePending" @click="toggleFavorite">
            {{ result?.favorited ? '取消收藏' : '收藏本题' }}
          </el-button>
          <div class="question-navigation">
            <el-button :disabled="currentIndex === 0" @click="previousQuestion">上一题</el-button>
            <el-button v-if="currentIndex < questions.length - 1" type="primary" @click="nextQuestion">下一题</el-button>
            <el-button v-else type="primary" @click="finishPractice">完成答题</el-button>
          </div>
        </div>
      </el-card>

      <aside class="ai-tutor-panel" aria-label="AI 题目辅导">
        <el-card class="ai-tutor-card" shadow="never">
          <template #header>
            <div class="ai-tutor-heading">
              <strong>AI 题目辅导</strong>
              <div class="ai-tutor-actions">
                <el-tag v-if="!submitted" type="info" effect="plain">未解锁</el-tag>
                <el-tag v-else type="success" effect="plain">已解锁</el-tag>
                <el-button
                  size="small"
                  plain
                  title="导出当前题目的 AI 辅导对话为 Markdown"
                  aria-label="导出当前题目的 AI 辅导对话为 Markdown"
                  :disabled="!canExportAiConversation || aiLoading"
                  @click="exportAiConversation"
                >导出 MD</el-button>
              </div>
            </div>
          </template>

          <div v-if="!submitted" class="ai-locked">
            <span class="lock-icon">🔒</span>
            <p>提交答案后可使用 AI 讲解与追问</p>
          </div>

          <template v-else>
            <p v-if="aiMessages.length === 0" class="ai-intro">AI 会基于本次答题记录和数据库中的正确答案进行讲解。</p>
            <el-button
              v-if="aiMessages.length === 0"
              class="explain-button"
              type="primary"
              :loading="aiLoading"
              @click="askQuestionAi('请详细讲解这道题')"
            >生成 AI 讲解</el-button>

            <div v-else ref="aiMessageList" @scroll.passive="aiScroll.onScroll" class="ai-message-list" aria-live="polite">
              <div v-for="(message, index) in aiMessages" :key="index" :class="['ai-message', message.role]">
                <span>{{ message.role === 'user' ? '我' : 'AI' }}</span>
                <div>
                  <AiMarkdown v-if="message.role === 'assistant' && message.content" :content="message.content" :streaming="message.streaming" />
                  <template v-else-if="message.role === 'assistant'">{{ message.streaming ? '正在生成...' : '未生成内容' }}</template>
                  <template v-else>{{ message.content }}</template>
                  <AiMessageSource :message="message" />
                  <div v-if="message.streaming && message.content" class="streaming-status">正在生成...</div>
                </div>
              </div>
            </div>

            <el-alert v-if="aiError" :title="aiError" type="warning" :closable="false" show-icon />

            <template v-if="aiMessages.some(message => message.role === 'assistant')">
              <el-input
                v-model="aiInput"
                type="textarea"
                :rows="3"
                maxlength="2000"
                show-word-limit
                resize="none"
                placeholder="继续追问，Enter 发送，Shift+Enter 换行"
                :disabled="aiLoading"
                @keydown.enter.exact.prevent="askQuestionAi()"
              />
              <el-button v-if="aiLoading" class="ask-button" @click="cancelAiGeneration">取消生成</el-button>
              <el-button v-else class="ask-button" type="primary" :disabled="!aiInput.trim()" @click="askQuestionAi()">发送追问</el-button>
            </template>
          </template>
        </el-card>
      </aside>
      </div>
    </template>
  </main>
</template>

<style scoped>
.practice-page { max-width: 1240px; margin: 0 auto; padding: 32px var(--page-padding) 60px; }
.practice-breadcrumb { margin-bottom: 24px; }
.practice-header { display: flex; justify-content: space-between; align-items: flex-end; gap: 20px; margin: 28px 0 24px; }
.practice-header p { margin: 0 0 6px; color: var(--color-primary); font-weight: 700; }
.practice-header h1 { margin: 0; font-size: 32px; }
.practice-layout {
  display: grid;
  grid-template-columns: minmax(0, 1.85fr) minmax(320px, 1fr);
  grid-template-areas: "question answer-card" "question ai-tutor";
  gap: 22px;
  align-items: start;
}
.answer-card-panel { grid-area: answer-card; }
.question-card { grid-area: question; min-width: 0; padding: 10px; scroll-margin-top: 84px; }
.question-meta { display: flex; gap: 10px; margin-bottom: 18px; }
.question-title { margin: 0 0 26px; font-size: 21px; font-weight: 700; line-height: 1.7; }
.answer-list { display: grid; gap: 12px; width: 100%; }
.answer-list :deep(.el-radio), .answer-list :deep(.el-checkbox) { width: 100%; height: auto; min-height: 48px; margin: 0; padding: 12px 16px; white-space: normal; }
.answer-list :deep(.el-radio__label), .answer-list :deep(.el-checkbox__label) { min-width: 0; line-height: 1.6; white-space: normal; }
.option-content { margin-left: .35em; }
.judge-list { grid-template-columns: repeat(2, minmax(0, 1fr)); }
.submit-button { margin-top: 26px; min-width: 130px; }
.result-alert { margin-top: 24px; }
.result-alert p { margin: 8px 0; line-height: 1.6; }
.analysis-row { display: grid; gap: 4px; margin: 8px 0; line-height: 1.6; }
.analysis-content { min-width: 0; }
.question-footer { display: flex; align-items: center; justify-content: space-between; gap: 12px; margin-top: 20px; }
.question-navigation { display: flex; justify-content: flex-end; gap: 10px; margin-left: auto; }
.question-navigation :deep(.el-button + .el-button) { margin-left: 0; }
.ai-tutor-panel { grid-area: ai-tutor; position: sticky; top: 84px; }
.ai-tutor-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.ai-tutor-actions { display: flex; align-items: center; justify-content: flex-end; gap: 8px; flex-wrap: wrap; }
.ai-tutor-actions .el-button { margin: 0; }
.ai-tutor-card :deep(.el-card__body) { display: grid; gap: 14px; }
.ai-locked { min-height: 260px; display: grid; place-content: center; justify-items: center; color: var(--color-text-secondary); text-align: center; }
.lock-icon { font-size: 30px; }
.ai-intro { margin: 0; color: var(--color-text-secondary); line-height: 1.7; }
.explain-button, .ask-button { width: 100%; }
.ai-message-list { overflow-anchor: none; max-height: 380px; overflow-y: auto; display: flex; flex-direction: column; gap: 12px; padding: 2px; }
.ai-message { display: flex; flex-direction: column; gap: 4px; max-width: 92%; }
.ai-message > span { color: var(--color-text-secondary); font-size: 12px; }
.ai-message > div { min-width: 0; max-width: 100%; padding: 10px 12px; border-radius: 10px; background: var(--color-surface-muted); line-height: 1.6; overflow-wrap: anywhere; }
.ai-message.user { align-self: flex-end; align-items: flex-end; }
.ai-message.user > div { background: var(--color-primary); color: white; white-space: pre-wrap; }
.streaming-status { margin-top: 8px; color: var(--color-text-secondary); font-size: 12px; }
@media (max-width: 900px) {
  .practice-layout { grid-template-columns: 1fr; grid-template-areas: "answer-card" "question" "ai-tutor"; }
  .ai-tutor-panel { position: static; }
}
@media (max-width: 640px) {
  .practice-page { padding: 22px 14px 40px; }
  .practice-header { align-items: flex-start; flex-direction: column; }
  .judge-list { grid-template-columns: 1fr; }
  .question-footer { align-items: stretch; flex-direction: column; }
  .question-footer > :deep(.el-button) { width: 100%; margin-left: 0; }
  .question-navigation { width: 100%; margin-left: 0; }
  .question-navigation :deep(.el-button) { flex: 1; }
}
</style>
