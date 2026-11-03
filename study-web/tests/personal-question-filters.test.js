import test from 'node:test'
import assert from 'node:assert/strict'
import { personalQuestionFilters } from '../src/utils/personalQuestionFilters.js'

test('favorite sort survives practice and return queries while private fields are discarded', () => {
  assert.deepEqual(personalQuestionFilters({
    chapterId: ' 1 ', keyword: '链表', favoriteTimeOrder: 'ASC', source: 'favorite',
    userId: '999', questionIds: ['1', '2']
  }), { chapterId: '1', keyword: '链表', favoriteTimeOrder: 'ASC' })
  assert.deepEqual(personalQuestionFilters({ favoriteTimeOrder: '', keyword: ' ' }), {})
})
