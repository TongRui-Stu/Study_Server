<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { getSubjectList } from '@/api/subject'
import { streamAiChat } from '@/api/ai'
import AiMarkdown from '@/components/AiMarkdown.vue'
import AiMessageSource from '@/components/AiMessageSource.vue'
import { useRouter } from 'vue-router'
import { downloadConversationMarkdown, hasExportableMessages } from '@/utils/markdownConversation'
import { createAiAutoScroll } from '@/utils/aiAutoScroll'
import { createAiStreamSession, finalizeInterruptedAiTurn, isAbortError } from '@/utils/aiStreamSession'

const router = useRouter()

const subjects = ref([])
const loading = ref(true)
const errorMessage = ref('')
const aiInput = ref('')
const aiLoading = ref(false)
const aiError = ref('')
const messageList = ref(null)
const aiStream = createAiStreamSession()
const aiScroll = createAiAutoScroll(() => messageList.value, { afterUpdate: nextTick })
let activeAiRequest = null
const welcomeMessage = {
  role: 'assistant',
  content: '你好，我是 AI 学习助手。你可以问我课程概念、解题思路或学习方法。',
  local: true
}
const aiMessages = ref([{ ...welcomeMessage }])
const canExportAiConversation = computed(() => hasExportableMessages(aiMessages.value))
const recommendedQuestions = [
  '请解释进程和线程的区别',
  '如何制定一周复习计划？',
  '做错题后应该怎样复盘？'
]

onMounted(async () => {
  try {
    const response = await getSubjectList()
    if (response.code !== 200) {
      throw new Error(response.message || '获取科目失败')
    }
    subjects.value = response.data || []
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '获取科目失败，请检查后端服务'
  } finally {
    loading.value = false
  }
})

onBeforeUnmount(() => {
  aiStream.cancel('unmounted')
  aiScroll.dispose()
})

function openSubject(subject) {
  router.push({ path: `/subjects/${subject.id}`, query: { name: subject.name } })
}

function conversationHistory() {
  return aiMessages.value
    .filter(message => !message.local && typeof message.content === 'string' && message.content.trim())
    .slice(-16)
    .map(({ role, content }) => ({ role, content }))
}

function scrollMessagesToBottom(force = false) {
  return aiScroll.request(force)
}

async function sendMessage(message = aiInput.value) {
  const content = message.trim()
  if (!content || aiLoading.value || content.length > 2000) return

  const history = conversationHistory()
  aiMessages.value.push({ role: 'user', content })
  const assistantIndex = aiMessages.value.length
  aiMessages.value.push({ role: 'assistant', content: '', streaming: true })
  aiInput.value = ''
  aiError.value = ''
  aiLoading.value = true
  await scrollMessagesToBottom(true)
  const updateAssistant = patch => {
    const message = aiMessages.value[assistantIndex]
    if (message) aiMessages.value[assistantIndex] = { ...message, ...patch }
  }
  const session = aiStream.begin({
    onSource: event => updateAssistant({ provider: event.provider, model: event.model }),
    onDelta: delta => {
      const current = aiMessages.value[assistantIndex]
      if (current) updateAssistant({ content: current.content + delta })
      scrollMessagesToBottom()
    },
    onDone: async () => {
      activeAiRequest = null
      updateAssistant({ streaming: false })
      aiLoading.value = false
      await scrollMessagesToBottom()
    },
    onError: async message => {
      activeAiRequest = null
      finalizeInterruptedAiTurn(aiMessages.value, assistantIndex)
      aiError.value = message
      aiLoading.value = false
      await scrollMessagesToBottom()
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
    await streamAiChat({ message: content, history }, {
      signal: session.signal,
      onEvent: session.handleEvent
    })
  } catch (error) {
    if (!isAbortError(error) && session.isCurrent()) {
      session.cancel('failed')
      aiError.value = error.message || 'AI 服务暂时不可用，请稍后重试'
    }
  } finally {
    if (activeAiRequest === session && !session.isCurrent()) activeAiRequest = null
  }
}

function cancelAiGeneration() {
  activeAiRequest?.cancel('user')
}

function useRecommendedQuestion(question) {
  if (aiLoading.value) return
  aiInput.value = question
  sendMessage(question)
}

function clearConversation() {
  if (aiLoading.value) return
  aiMessages.value = [{ ...welcomeMessage }]
  aiInput.value = ''
  aiError.value = ''
}

function exportAiConversation() {
  if (!canExportAiConversation.value || aiLoading.value) return
  try {
    downloadConversationMarkdown({
      title: '在线学习平台 - AI 学习助手对话',
      scene: '首页即时问答',
      messages: aiMessages.value,
      filenamePrefix: 'AI学习助手对话'
    })
    ElMessage.success('Markdown 对话已导出')
  } catch (error) {
    ElMessage.error(error.message || 'Markdown 对话导出失败，请稍后重试')
  }
}
</script>

<template>
  <main class="page">
    <div class="home-layout">
      <section class="course-column">
        <div class="hero">
          <p class="eyebrow">COURSE CENTER</p>
          <h2>选择科目，开始学习</h2>
          <p>按科目、章节和知识点循序练习，在错题与统计中看见每一步进步。</p>
        </div>
        <el-alert v-if="errorMessage" :title="errorMessage" type="error" show-icon :closable="false" />
        <el-skeleton v-else-if="loading" :rows="6" animated />
        <el-empty v-else-if="subjects.length === 0" description="暂时没有可学习的科目，请稍后再来" />
        <div v-else class="subject-list">
          <el-card v-for="subject in subjects" :key="subject.id" class="subject-card" shadow="hover" @click="openSubject(subject)">
            <h3>{{ subject.name }}</h3>
            <p>{{ subject.description }}</p>
            <el-button text type="primary">查看章节 →</el-button>
          </el-card>
        </div>
      </section>

      <aside class="ai-column" aria-label="AI 学习助手">
        <el-card class="ai-card" shadow="never">
          <template #header>
            <div class="ai-heading">
              <div><strong>AI 学习助手</strong><span>即时问答</span></div>
              <div class="ai-actions">
                <el-button
                  text
                  title="导出当前 AI 学习助手对话为 Markdown"
                  aria-label="导出当前 AI 学习助手对话为 Markdown"
                  :disabled="!canExportAiConversation || aiLoading"
                  @click="exportAiConversation"
                >导出 MD</el-button>
                <el-button text :disabled="aiLoading" @click="clearConversation">清空</el-button>
              </div>
            </div>
          </template>

          <div ref="messageList" @scroll.passive="aiScroll.onScroll" class="message-list" aria-live="polite">
            <div v-for="(message, index) in aiMessages" :key="index" :class="['message', message.role]">
              <span>{{ message.role === 'user' ? '我' : 'AI' }}</span>
              <div class="message-content">
                <AiMarkdown v-if="message.role === 'assistant' && message.content" :content="message.content" :streaming="message.streaming" />
                <template v-else-if="message.role === 'assistant'">{{ message.streaming ? '正在生成...' : '未生成内容' }}</template>
                <template v-else>{{ message.content }}</template>
                <AiMessageSource :message="message" />
                <div v-if="message.streaming && message.content" class="streaming-status">正在生成...</div>
              </div>
            </div>
          </div>

          <div class="recommendations">
            <el-button v-for="question in recommendedQuestions" :key="question" size="small" plain :disabled="aiLoading" @click="useRecommendedQuestion(question)">
              {{ question }}
            </el-button>
          </div>

          <el-alert v-if="aiError" class="ai-error" :title="aiError" type="warning" :closable="false" show-icon />
          <el-input
            v-model="aiInput"
            type="textarea"
            :rows="3"
            maxlength="2000"
            show-word-limit
            resize="none"
            placeholder="输入学习问题，Enter 发送，Shift+Enter 换行"
            :disabled="aiLoading"
            @keydown.enter.exact.prevent="sendMessage()"
          />
          <div class="composer-actions">
            <el-button v-if="aiLoading" class="send-button" @click="cancelAiGeneration">取消生成</el-button>
            <el-button v-else class="send-button" type="primary" :disabled="!aiInput.trim()" @click="sendMessage()">发送</el-button>
          </div>
        </el-card>
      </aside>
    </div>
  </main>
</template>

<style scoped>
.page { max-width: var(--content-max-width); margin: 0 auto; padding: 38px var(--page-padding) 64px; }
.home-layout { display: grid; grid-template-columns: minmax(0, 1.7fr) minmax(340px, 1fr); gap: 28px; align-items: start; }
.hero { margin: 4px 0 28px; padding: 26px 28px; border: 1px solid var(--color-hero-border); border-radius: var(--radius-lg); background: var(--gradient-hero); }
.hero h2 { margin: 4px 0 10px; font-size: clamp(28px, 3vw, 38px); }
.hero p { max-width: 680px; margin-bottom: 0; color: var(--color-text-secondary); }
.eyebrow { color: var(--color-primary) !important; font-size: 12px; font-weight: 800; letter-spacing: 1.5px; }
.subject-list { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 16px; }
.subject-card { min-height: 176px; cursor: pointer; transition: transform .2s ease, box-shadow .2s ease; }
.subject-card:hover { transform: translateY(-3px); box-shadow: var(--shadow-card); }
.subject-card h3 { margin-top: 0; }
.subject-card p { min-height: 48px; color: var(--color-text-secondary); line-height: 1.7; }
.ai-column { position: sticky; top: 84px; }
.ai-card :deep(.el-card__body) { display: grid; gap: 14px; }
.ai-heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.ai-heading div { display: flex; flex-direction: column; gap: 3px; }
.ai-heading .ai-actions { flex-direction: row; align-items: center; justify-content: flex-end; gap: 4px; }
.ai-actions .el-button { margin: 0; }
.ai-heading span { color: var(--color-text-secondary); font-size: 12px; font-weight: 400; }
.message-list { overflow-anchor: none; height: 360px; overflow-y: auto; display: flex; flex-direction: column; gap: 12px; padding: 4px 2px; }
.message { display: flex; flex-direction: column; gap: 4px; max-width: 90%; }
.message > span { color: var(--color-text-secondary); font-size: 12px; }
.message-content { min-width: 0; max-width: 100%; padding: 10px 12px; border-radius: 10px; background: var(--color-surface-muted); color: var(--color-text); line-height: 1.6; overflow-wrap: anywhere; }
.message.user { align-self: flex-end; align-items: flex-end; }
.message.user .message-content { background: var(--color-primary); color: white; white-space: pre-wrap; }
.recommendations { display: flex; flex-wrap: wrap; gap: 7px; }
.recommendations .el-button { height: auto; margin: 0; padding: 6px 9px; white-space: normal; text-align: left; }
.ai-error { margin: 0; }
.send-button { width: 100%; }
.composer-actions { width: 100%; }
.streaming-status { margin-top: 8px; color: var(--color-text-secondary); font-size: 12px; }
@media (max-width: 920px) { .home-layout { grid-template-columns: 1fr; } .ai-column { position: static; } }
@media (max-width: 600px) { .page { padding-top: 22px; padding-bottom: 40px; } .hero { padding: 22px 18px; } .subject-list { grid-template-columns: 1fr; } .message-list { height: 320px; } }
</style>
