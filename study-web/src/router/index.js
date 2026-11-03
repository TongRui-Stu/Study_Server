import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const UserLayout = () => import('@/layout/UserLayout.vue')
const AdminLayout = () => import('@/layout/AdminLayout.vue')

const router = createRouter({
  history: createWebHistory(),
  routes: [
    {
      path: '/',
      component: UserLayout,
      redirect: '/home',
      meta: { requiresAuth: true },
      children: [
        { path: 'home', component: () => import('@/views/home/HomeView.vue') },
        { path: 'subjects/:id', component: () => import('@/views/subject/SubjectDetailView.vue') },
        { path: 'chapters/:id', component: () => import('@/views/chapter/ChapterDetailView.vue') },
        { path: 'practice', component: () => import('@/views/practice/PracticeView.vue') },
        { path: 'practice/:knowledgePointId', component: () => import('@/views/practice/PracticeView.vue') },
        { path: 'wrong-questions', component: () => import('@/views/wrong/WrongQuestionView.vue') },
        { path: 'favorites', component: () => import('@/views/favorite/FavoriteView.vue') },
        { path: 'statistics', component: () => import('@/views/statistics/StatisticsView.vue') }
      ]
    },
    { path: '/login', component: () => import('@/views/login/LoginView.vue'), meta: { guestOnly: true } },
    { path: '/register', component: () => import('@/views/register/RegisterView.vue'), meta: { guestOnly: true } },
    {
      path: '/admin',
      component: AdminLayout,
      meta: { requiresAuth: true, requiresAdmin: true },
      children: [
        { path: '', component: () => import('@/views/admin/AdminHomeView.vue') },
        { path: 'subjects', component: () => import('@/views/admin/SubjectManageView.vue') },
        { path: 'chapters', component: () => import('@/views/admin/ChapterManageView.vue') },
        { path: 'knowledge-points', component: () => import('@/views/admin/KnowledgePointManageView.vue') },
        { path: 'questions', component: () => import('@/views/admin/QuestionManageView.vue') },
        { path: 'question-import', component: () => import('@/views/admin/QuestionImportView.vue') }
      ]
    }
  ]
})

router.onError((error, to) => {
  const dynamicImportFailed = /Failed to fetch dynamically imported module|Importing a module script failed/i.test(error?.message || '')
  if (!dynamicImportFailed || typeof window === 'undefined') return
  const reloadKey = `route-reload:${to.fullPath}`
  if (!sessionStorage.getItem(reloadKey)) {
    sessionStorage.setItem(reloadKey, '1')
    window.location.assign(to.fullPath)
  } else {
    sessionStorage.removeItem(reloadKey)
  }
})

router.afterEach(to => {
  if (typeof window !== 'undefined') {
    sessionStorage.removeItem(`route-reload:${to.fullPath}`)
  }
})

router.beforeEach(async to => {
  const auth = useAuthStore()
  if (auth.token && !auth.user) {
    try {
      await auth.fetchCurrentUser()
    } catch {
      // A stale token is cleared by the store and handled below.
    }
  }
  const requiresAuth = to.matched.some(record => record.meta.requiresAuth)
  const requiresAdmin = to.matched.some(record => record.meta.requiresAdmin)
  if (requiresAuth && !auth.token) {
    return { path: '/login', query: { redirect: to.fullPath } }
  }
  if (requiresAdmin && auth.user?.role !== 'ADMIN') {
    return '/home'
  }
  if (to.meta.guestOnly && auth.token) {
    return '/home'
  }
})

export default router
