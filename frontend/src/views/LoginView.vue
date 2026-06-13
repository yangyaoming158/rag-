<template>
  <main class="login-page">
    <section class="login-panel">
      <div class="brand">
        <div class="brand-mark">D</div>
        <div>
          <h1>DevDocs RAG</h1>
          <p>项目文档智能问答系统</p>
        </div>
      </div>

      <el-form ref="formRef" :model="form" :rules="rules" label-position="top" @submit.prevent="submit">
        <el-form-item label="用户名" prop="username">
          <el-input v-model.trim="form.username" autocomplete="username" />
        </el-form-item>
        <el-form-item label="密码" prop="password">
          <el-input v-model="form.password" type="password" autocomplete="current-password" show-password />
        </el-form-item>
        <el-button type="primary" native-type="submit" :loading="submitting" class="login-button">
          登录
        </el-button>
      </el-form>
    </section>
  </main>
</template>

<script setup lang="ts">
import type { FormInstance, FormRules } from 'element-plus'
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useAuthStore } from '../stores/auth'

const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const formRef = ref<FormInstance>()
const submitting = ref(false)

const form = reactive({
  username: 'admin',
  password: ''
})

const rules: FormRules<typeof form> = {
  username: [{ required: true, message: '请输入用户名', trigger: 'blur' }],
  password: [{ required: true, message: '请输入密码', trigger: 'blur' }]
}

async function submit() {
  const valid = await formRef.value?.validate().catch(() => false)
  if (!valid) return
  submitting.value = true
  try {
    await auth.login(form.username, form.password)
    await router.push((route.query.redirect as string | undefined) || '/')
  } finally {
    submitting.value = false
  }
}
</script>

<style scoped>
.login-page {
  align-items: center;
  display: flex;
  justify-content: center;
  min-height: 100vh;
  padding: 32px;
}

.login-panel {
  background: #ffffff;
  border: 1px solid #e5e7eb;
  border-radius: 8px;
  box-shadow: 0 18px 45px rgba(15, 23, 42, 0.08);
  padding: 32px;
  width: min(100%, 420px);
}

.brand {
  align-items: center;
  display: flex;
  gap: 14px;
  margin-bottom: 28px;
}

.brand-mark {
  align-items: center;
  background: #2563eb;
  border-radius: 8px;
  color: #ffffff;
  display: flex;
  font-size: 22px;
  font-weight: 700;
  height: 48px;
  justify-content: center;
  width: 48px;
}

h1 {
  font-size: 22px;
  line-height: 1.25;
  margin: 0;
}

p {
  color: #6b7280;
  margin: 4px 0 0;
}

.login-button {
  margin-top: 8px;
  width: 100%;
}
</style>
