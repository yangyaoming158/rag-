<template>
  <main class="shell">
    <header class="topbar">
      <div>
        <h1>DevDocs RAG</h1>
        <p>Phase 0 骨架已启动</p>
      </div>
      <div class="user">
        <span>{{ auth.user?.username }}</span>
        <el-button @click="logout">退出</el-button>
      </div>
    </header>

    <section class="content">
      <el-alert
        title="当前阶段只包含登录、健康检查与数据库初始化。知识库、文档上传和问答将在后续 Phase 实现。"
        type="info"
        :closable="false"
      />
      <el-descriptions title="运行状态" :column="1" border>
        <el-descriptions-item label="用户">{{ auth.user?.username }}</el-descriptions-item>
        <el-descriptions-item label="角色">{{ auth.user?.role }}</el-descriptions-item>
        <el-descriptions-item label="后端接口">/api/auth/me</el-descriptions-item>
      </el-descriptions>
    </section>
  </main>
</template>

<script setup lang="ts">
import { useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const auth = useAuthStore()

async function logout() {
  auth.logout()
  await router.push('/login')
}
</script>

<style scoped>
.shell {
  min-height: 100vh;
}

.topbar {
  align-items: center;
  background: #ffffff;
  border-bottom: 1px solid #e5e7eb;
  display: flex;
  justify-content: space-between;
  padding: 18px 28px;
}

h1 {
  font-size: 22px;
  margin: 0;
}

p {
  color: #6b7280;
  margin: 4px 0 0;
}

.user {
  align-items: center;
  display: flex;
  gap: 12px;
}

.content {
  display: grid;
  gap: 18px;
  padding: 28px;
}
</style>
