<script setup>
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AuthThemeToggle from '@/components/AuthThemeToggle.vue'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref()
const submitting = ref(false)
const errorMessage = ref('')
const form = reactive({ username: '', password: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  errorMessage.value = ''
  try {
    await auth.login(form)
    const redirect = typeof route.query.redirect === 'string' && route.query.redirect.startsWith('/')
      ? route.query.redirect
      : '/home'
    await router.replace(redirect)
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '登录失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="auth-page">
    <AuthThemeToggle />
    <section class="auth-brand" aria-label="平台介绍">
      <span class="auth-brand__mark">学</span>
      <p>ONLINE LEARNING</p>
      <h1>在线学习与题库管理系统</h1>
      <span>课程学习、在线练习、错题复盘与学习诊断，在一个清晰的学习空间中完成。</span>
    </section>
    <el-card class="auth-card" shadow="never">
      <p class="auth-eyebrow">WELCOME BACK</p>
      <h2>登录账户</h2>
      <p class="auth-tip">继续你的学习进度</p>
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model.trim="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="current-password" />
        </el-form-item>
        <el-button class="auth-submit" type="primary" native-type="submit" :loading="submitting" :disabled="submitting">登录</el-button>
      </el-form>
      <p class="auth-switch">还没有账号？<router-link to="/register">前往注册</router-link></p>
    </el-card>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(300px, 520px) minmax(360px, 430px);
  place-content: center;
  gap: 72px;
  padding: 40px 24px;
  background: radial-gradient(circle at 12% 20%, var(--color-auth-glow) 0, transparent 34%), var(--color-page);
}
.auth-brand { max-width: 520px; }
.auth-brand__mark { display: grid; width: 52px; height: 52px; place-items: center; margin-bottom: 24px; border-radius: 16px; background: var(--color-primary); color: #fff; font-size: 24px; font-weight: 800; box-shadow: 0 12px 28px rgba(37, 99, 235, .25); }
.auth-brand p, .auth-eyebrow { margin: 0 0 8px; color: var(--color-primary); font-size: 12px; font-weight: 800; letter-spacing: 1.8px; }
.auth-brand h1 { margin: 0 0 18px; font-size: clamp(34px, 4vw, 50px); line-height: 1.2; }
.auth-brand > span:last-child { color: var(--color-text-secondary); font-size: 16px; line-height: 1.8; }
.auth-card { width: 100%; padding: 14px 12px; border-radius: var(--radius-lg); }
.auth-card h2 { margin: 0 0 6px; font-size: 28px; }
.auth-tip { margin: 0 0 24px; color: var(--color-text-secondary); }
.auth-submit { width: 100%; margin-top: 4px; }
.auth-switch { margin: 22px 0 2px; text-align: center; }
.el-alert { margin-bottom: 16px; }
@media (max-width: 850px) { .auth-page { grid-template-columns: minmax(0, 430px); gap: 26px; } .auth-brand { text-align: center; } .auth-brand__mark { margin: 0 auto 14px; } .auth-brand h1 { font-size: 30px; } .auth-brand > span:last-child { display: none; } }
@media (max-width: 480px) { .auth-page { align-content: start; padding: 30px 14px; } .auth-card { padding: 6px 2px; } }
</style>
