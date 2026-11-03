const filterKeys = ['subjectId', 'chapterId', 'knowledgePointId', 'keyword', 'wrongCountOrder', 'favoriteTimeOrder', 'type', 'difficulty']

// Whitelist business query fields. Never propagate user IDs or question arrays.
export function personalQuestionFilters(query = {}) {
  return Object.fromEntries(filterKeys.flatMap(key => {
    const value = query[key]
    return typeof value === 'string' || typeof value === 'number'
      ? (String(value).trim() ? [[key, String(value).trim()]] : [])
      : []
  }))
}
