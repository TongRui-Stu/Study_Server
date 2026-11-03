<script setup>
const modules = [
  { path: '/admin/subjects', title: '科目管理', description: '维护课程入口、展示顺序与启用状态', metric: '课程体系起点' },
  { path: '/admin/chapters', title: '章节管理', description: '按科目组织章节，保持学习路径清晰', metric: '连接科目与知识点' },
  { path: '/admin/knowledge-points', title: '知识点管理', description: '维护练习的最小学习单元与顺序', metric: '题目归属基础' },
  { path: '/admin/questions', title: '题库管理', description: '管理题目、选项、答案并按条件导出', metric: '支持筛选与 Excel' },
  { path: '/admin/question-import', title: '题库批量导入', description: '下载动态模板，预校验后以单事务导入题目', metric: '校验、预览与回滚' }
]
</script>

<template>
  <section class="admin-overview">
    <header class="overview-header">
      <p>ADMIN CONSOLE</p>
      <h1>管理首页</h1>
      <span>按照“科目 → 章节 → 知识点 → 题目”的层级维护教学内容。</span>
    </header>

    <div class="module-grid">
      <router-link v-for="(item, index) in modules" :key="item.path" :to="item.path" class="module-card">
        <span class="module-index">0{{ index + 1 }}</span>
        <div><h2>{{ item.title }}</h2><p>{{ item.description }}</p></div>
        <small>{{ item.metric }}</small>
        <strong>进入管理 →</strong>
      </router-link>
    </div>

    <el-alert
      title="管理操作会直接影响学生端可见内容；删除或禁用前请确认关联数据与展示影响。"
      type="info"
      :closable="false"
      show-icon
    />
  </section>
</template>

<style scoped>
.admin-overview { max-width: 1180px; margin: 0 auto; }
.overview-header { padding: 26px 28px; border: 1px solid var(--color-hero-border); border-radius: var(--radius-lg); background: var(--gradient-hero); }
.overview-header p { margin: 0 0 6px; color: var(--color-primary); font-size: 12px; font-weight: 800; letter-spacing: 1.6px; }
.overview-header h1 { margin: 0 0 8px; font-size: 32px; }
.overview-header span { color: var(--color-text-secondary); }
.module-grid { display: grid; grid-template-columns: repeat(2, minmax(0, 1fr)); gap: 18px; margin: 22px 0; }
.module-card { display: grid; grid-template-columns: auto 1fr; gap: 8px 16px; min-height: 190px; padding: 24px; border: 1px solid var(--color-border); border-radius: var(--radius-md); background: var(--color-surface); color: var(--color-text); box-shadow: var(--shadow-card); text-decoration: none; transition: transform .18s ease, border-color .18s ease; }
.module-card:hover { transform: translateY(-2px); border-color: var(--color-primary-hover); }
.module-index { grid-row: span 2; color: var(--color-primary-hover); font-size: 24px; font-weight: 800; }
.module-card h2 { margin: 0 0 8px; font-size: 20px; }
.module-card p { margin: 0; color: var(--color-text-secondary); line-height: 1.7; }
.module-card small { align-self: end; color: var(--color-text-secondary); }
.module-card > strong { align-self: end; justify-self: end; color: var(--color-primary); font-size: 14px; }
@media (max-width: 760px) { .module-grid { grid-template-columns: 1fr; } .overview-header { padding: 22px 18px; } .overview-header h1 { font-size: 28px; } }
</style>
