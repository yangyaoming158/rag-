import { createRouter, createWebHistory } from 'vue-router'
import { useAuthStore } from '../stores/auth'
import LoginView from '../views/LoginView.vue'
import HomeView from '../views/HomeView.vue'
import KbDetailView from '../views/KbDetailView.vue'
import ChatView from '../views/ChatView.vue'

const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/login', name: 'login', component: LoginView },
    { path: '/', name: 'home', component: HomeView, meta: { requiresAuth: true } },
    { path: '/kbs/:id', name: 'kb-detail', component: KbDetailView, meta: { requiresAuth: true } },
    { path: '/kbs/:id/chat', name: 'kb-chat', component: ChatView, meta: { requiresAuth: true } }
  ]
})

router.beforeEach((to) => {
  const auth = useAuthStore()
  if (to.meta.requiresAuth && !auth.loggedIn) {
    return { name: 'login', query: { redirect: to.fullPath } }
  }
  if (to.name === 'login' && auth.loggedIn) {
    return { name: 'home' }
  }
  return true
})

export default router
