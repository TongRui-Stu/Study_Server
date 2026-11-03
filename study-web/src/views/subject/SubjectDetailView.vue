<script setup>
import { onMounted, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { getChapterList } from '@/api/chapter'
import { getSubjectList } from '@/api/subject'

const route = useRoute()
const router = useRouter()
const subject = ref({ id: Number(route.params.id), name: route.query.name || '科目' })
const chapters = ref([])
const loading = ref(true)
const errorMessage = ref('')

onMounted(async () => {
  try {
    const [subjectResponse, chapterResponse] = await Promise.all([
      getSubjectList(),
      getChapterList(route.params.id)
    ])
    subject.value = subjectResponse.data.find(item => item.id === Number(route.params.id)) || subject.value
    chapters.value = chapterResponse.data || []
  } catch (error) {
    errorMessage.value = error.response?.data?.message || '获取章节失败'
  } finally {
    loading.value = false
  }
})

function openChapter(chapter) {
  router.push({
    path: `/chapters/${chapter.id}`,
    query: { name: chapter.name, subjectId: subject.value.id, subjectName: subject.value.name }
  })
}
</script>

<template>
  <main class="content-page">
    <el-breadcrumb separator="/">
      <el-breadcrumb-item :to="{ path: '/home' }">首页</el-breadcrumb-item>
      <el-breadcrumb-item>{{ subject.name }}</el-breadcrumb-item>
    </el-breadcrumb>
    <div class="page-title">
      <p>科目</p>
      <h1>{{ subject.name }}</h1>
      <span>{{ subject.description }}</span>
    </div>
    <el-alert v-if="errorMessage" :title="errorMessage" type="error" :closable="false" show-icon />
    <el-skeleton v-else-if="loading" :rows="4" animated />
    <el-empty v-else-if="chapters.length === 0" description="该科目暂时没有启用章节">
      <el-button type="primary" @click="router.push('/home')">返回科目列表</el-button>
    </el-empty>
    <div v-else class="item-grid">
      <el-card v-for="chapter in chapters" :key="chapter.id" shadow="hover" class="click-card" @click="openChapter(chapter)">
        <div class="order">第 {{ chapter.sortOrder }} 章</div>
        <h2>{{ chapter.name }}</h2>
        <p>{{ chapter.description || '暂无描述' }}</p>
        <el-button text type="primary">查看知识点 →</el-button>
      </el-card>
    </div>
  </main>
</template>
