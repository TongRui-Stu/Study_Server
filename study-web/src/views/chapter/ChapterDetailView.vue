<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getKnowledgePointList } from '@/api/knowledgePoint'

const route = useRoute()
const router = useRouter()
const chapterName = route.query.name || '章节'
const points = ref([])
const loading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  try {
    const response = await getKnowledgePointList(route.params.id)
    points.value = response.data || []
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '获取知识点失败'
  } finally {
    loading.value = false
  }
})

function back() {
  if (route.query.subjectId) {
    router.push({ path: `/subjects/${route.query.subjectId}`, query: { name: route.query.subjectName } })
  } else {
    router.push('/home')
  }
}

function startPractice(point) {
  router.push({
    path: `/practice/${point.id}`,
    query: {
      chapterId: route.params.id,
      chapterName,
      subjectId: route.query.subjectId,
      subjectName: route.query.subjectName
    }
  })
}
</script>

<template>
  <main class="content-page">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item v-if="route.query.subjectId" :to="{ path: `/subjects/${route.query.subjectId}`, query: { name: route.query.subjectName } }">{{ route.query.subjectName }}</el-breadcrumb-item>
      <el-breadcrumb-item>{{ chapterName }}</el-breadcrumb-item>
    </el-breadcrumb>
    <div class="page-title">
      <p>{{ route.query.subjectName || '知识点' }}</p>
      <h1>{{ chapterName }}</h1>
      <span>本章知识点</span>
    </div>
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
    <el-skeleton v-else-if="loading" :rows="4" animated />
    <el-empty v-else-if="points.length === 0" description="该章节暂时没有启用知识点">
      <el-button type="primary" @click="back">返回上一级</el-button>
    </el-empty>
    <div v-else class="item-grid">
      <el-card v-for="point in points" :key="point.id" shadow="never">
        <div class="order">知识点 {{ point.sortOrder }}</div>
        <h2>{{ point.name }}</h2>
        <p>{{ point.description || '暂无描述' }}</p>
        <el-button type="primary" plain @click="startPractice(point)">开始练习</el-button>
      </el-card>
    </div>
  </main>
</template>
