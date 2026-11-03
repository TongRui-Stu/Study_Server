<script setup>
import { reactive, ref } from 'vue'
import { ElMessage } from 'element-plus'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import AuthThemeToggle from '@/components/AuthThemeToggle.vue'

const router = useRouter()
const auth = useAuthStore()
const formRef = ref()
const submitting = ref(false)
const errorMessage = ref('')
const form = reactive({ username: '', nickname: '', email: '', password: '', confirmPassword: '' })
const rules = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  nickname: [{ required: true, message: '请输入昵称', trigger: 'blur' }],
  email: [{ type: 'email', message: '邮箱格式不正确', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }],
  confirmPassword: [
    { required: true, message: '请确认密码', trigger: 'blur' },
    { validator: (rule, value, callback) => callback(value === form.password ? undefined : new Error('两次密码不一致')), trigger: 'blur' }
  ]
}

async function submit() {
  const valid = await formRef.value.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  errorMessage.value = ''
  try {
    await auth.register({
      username: form.username,
      nickname: form.nickname,
      email: form.email || null,
      password: form.password
    })
    ElMessage.success('注册成功，请登录')
    await router.replace('/login')
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '注册失败，请稍后重试'
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
      <p>START LEARNING</p>
      <h1>建立你的学习档案</h1>
      <span>注册学生账户后即可开始章节练习，并持续积累错题、收藏与学习统计。</span>
    </section>
    <el-card class="auth-card" shadow="never">
      <p class="auth-eyebrow">CREATE ACCOUNT</p>
      <h2>注册学生账户</h2>
      <p class="auth-tip">填写基础信息即可开始学习</p>
      <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model.trim="form.username" autocomplete="username" maxlength="50" />
        </el-form-item>
        <el-form-item label="昵称" prop="nickname">
          <el-input v-model.trim="form.nickname" maxlength="50" />
        </el-form-item>
        <el-form-item label="邮箱（可选）" prop="email">
          <el-input v-model.trim="form.email" type="email" autocomplete="email" maxlength="100" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-form-item label="确认密码" prop="confirmPassword">
          <el-input v-model="form.confirmPassword" type="password" show-password autocomplete="new-password" />
        </el-form-item>
        <el-button class="auth-submit" type="primary" native-type="submit" :loading="submitting" :disabled="submitting">注册</el-button>
      </el-form>
      <p class="auth-switch">已有账号？<router-link to="/login">前往登录</router-link></p>
    </el-card>
  </main>
</template>

<style scoped>
.auth-page {
  min-height: 100vh;
  display: grid;
  grid-template-columns: minmax(300px, 520px) minmax(380px, 450px);
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
.auth-card { width: 100%; padding: 10px 12px; border-radius: var(--radius-lg); }
.auth-card h2 { margin: 0 0 6px; font-size: 28px; }
.auth-tip { margin: 0 0 20px; color: var(--color-text-secondary); }
.auth-submit { width: 100%; margin-top: 2px; }
.auth-switch { margin: 20px 0 2px; text-align: center; }
.el-alert { margin-bottom: 16px; }
@media (max-width: 850px) { .auth-page { grid-template-columns: minmax(0, 450px); gap: 22px; } .auth-brand { text-align: center; } .auth-brand__mark { margin: 0 auto 12px; } .auth-brand h1 { font-size: 28px; } .auth-brand > span:last-child { display: none; } }
@media (max-width: 480px) { .auth-page { align-content: start; padding: 24px 14px; } .auth-card { padding: 4px 2px; } }
</style>
