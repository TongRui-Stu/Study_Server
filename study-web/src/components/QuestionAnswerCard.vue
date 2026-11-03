<script setup>
import { computed } from 'vue'

const props = defineProps({
  questions: {
    type: Array,
    default: () => []
  },
  currentIndex: {
    type: Number,
    default: 0
  },
  questionStates: {
    type: Object,
    default: () => ({})
  }
})

const emit = defineEmits(['select'])

const items = computed(() => props.questions.map((question, index) => {
  const state = props.questionStates[question.id] || {}
  return {
    id: question.id,
    index,
    current: index === props.currentIndex,
    status: state.submitted ? (state.correct ? 'correct' : 'incorrect') : 'unanswered'
  }
}))

function statusLabel(item) {
  const status = item.status === 'correct' ? '回答正确'
    : item.status === 'incorrect' ? '回答错误' : '未作答'
  return `第 ${item.index + 1} 题，${status}${item.current ? '，当前题' : ''}`
}
</script>

<template>
  <el-card v-if="items.length" class="answer-card" shadow="never">
    <template #header>
      <div class="answer-card__heading">
        <strong>答题卡</strong>
        <span>{{ currentIndex + 1 }} / {{ items.length }}</span>
      </div>
    </template>

    <div class="answer-card__grid" role="list" aria-label="题目导航">
      <button
        v-for="item in items"
        :key="item.id"
        type="button"
        role="listitem"
        :class="['answer-card__number', `is-${item.status}`, { 'is-current': item.current }]"
        :aria-current="item.current ? 'step' : undefined"
        :aria-label="statusLabel(item)"
        @click="emit('select', item.index)"
      >{{ item.index + 1 }}</button>
    </div>

    <div class="answer-card__legend" aria-label="答题卡图例">
      <span><i class="legend-current" />当前</span>
      <span><i class="legend-unanswered" />未答</span>
      <span><i class="legend-correct" />正确</span>
      <span><i class="legend-incorrect" />错误</span>
    </div>
  </el-card>
</template>

<style scoped>
.answer-card { min-width: 0; }
.answer-card__heading { display: flex; align-items: center; justify-content: space-between; gap: 12px; }
.answer-card__heading span { color: var(--color-text-secondary); font-size: 13px; }
.answer-card__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(38px, 1fr));
  gap: 9px;
  max-height: 230px;
  padding: 3px;
  overflow-y: auto;
}
.answer-card__number {
  display: grid;
  min-width: 38px;
  height: 38px;
  place-items: center;
  border: 1px solid var(--color-border-strong);
  border-radius: var(--radius-sm);
  background: var(--color-surface-muted);
  color: var(--color-text);
  cursor: pointer;
  font: inherit;
  font-weight: 700;
}
.answer-card__number:hover { border-color: var(--color-primary); color: var(--color-primary); }
.answer-card__number.is-correct { border-color: var(--color-success); background: var(--color-success); color: #fff; }
.answer-card__number.is-incorrect { border-color: var(--color-danger); background: var(--color-danger); color: #fff; }
.answer-card__number.is-current { outline: 3px solid var(--color-primary); outline-offset: 2px; }
.answer-card__number.is-current.is-unanswered { background: var(--color-primary-soft); color: var(--color-primary); }
.answer-card__legend { display: flex; flex-wrap: wrap; gap: 9px 14px; margin-top: 16px; color: var(--color-text-secondary); font-size: 12px; }
.answer-card__legend span { display: inline-flex; align-items: center; gap: 5px; }
.answer-card__legend i { width: 11px; height: 11px; border: 1px solid var(--color-border-strong); border-radius: 3px; background: var(--color-surface-muted); }
.answer-card__legend .legend-current { border: 3px solid var(--color-primary); background: var(--color-primary-soft); }
.answer-card__legend .legend-correct { border-color: var(--color-success); background: var(--color-success); }
.answer-card__legend .legend-incorrect { border-color: var(--color-danger); background: var(--color-danger); }
</style>
