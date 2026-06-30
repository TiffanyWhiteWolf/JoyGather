<script setup lang="ts">
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { Bell, ClipboardCheck, LayoutDashboard, LogOut, Search, ShieldCheck, Users } from 'lucide-vue-next'
import { ref } from 'vue'
import { useAppStore } from '@/stores/app'
import { logout as apiLogout } from '@/lib/api'

const route = useRoute()
const router = useRouter()
const app = useAppStore()
const query = ref('')
const noticeOpen = ref(false)
const nav = [
  { to: '/admin', label: '数据概览', icon: LayoutDashboard },
  { to: '/admin/reviews', label: '审核中心', icon: ClipboardCheck },
  { to: '/admin/users', label: '用户管理', icon: Users },
  { to: '/admin/content', label: '内容治理', icon: ShieldCheck },
]

function search() {
  const value = query.value.trim()
  if (!value) return
  router.push({ path: '/admin/users', query: { q: value } })
}

async function logout() {
  await apiLogout()
  app.clearUserState()
  await router.push('/auth')
  app.showToast('管理员已退出登录')
}
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <RouterLink class="brand admin-brand" to="/"><span class="brand-mark"><span></span><span></span><span></span></span><span>趣聚</span><small>运营台</small></RouterLink>
      <nav class="admin-nav">
        <RouterLink v-for="item in nav" :key="item.to" :to="item.to" :class="{ active: route.path === item.to }"><component :is="item.icon" :size="19" />{{ item.label }}<span v-if="item.to === '/admin/reviews'" class="nav-count">12</span></RouterLink>
      </nav>
      <div class="admin-profile"><img src="https://i.pravatar.cc/80?img=11" alt="管理员" /><div><b>周晴</b><small>超级管理员</small></div></div>
      <button class="back-site admin-logout" @click="logout"><LogOut :size="16" />退出登录</button>
    </aside>
    <section class="admin-main">
      <header class="admin-topbar">
        <form class="admin-search" @submit.prevent="search"><Search :size="18" /><input v-model="query" placeholder="搜索用户、活动或小队" /></form>
        <div class="admin-notice-wrap">
          <button class="icon-button" @click="noticeOpen = !noticeOpen"><Bell :size="19" /><span>4</span></button>
          <div v-if="noticeOpen" class="admin-notice"><b>运营通知</b><p>审核、封禁和内容治理结果会写入审计日志。</p><p>第三方调用失败可在数据库 third_party_events 查看。</p></div>
        </div>
      </header>
      <div class="admin-content"><RouterView /></div>
    </section>
  </div>
</template>

<style scoped>
.admin-logout{width:100%;border:0;background:transparent;color:var(--color-danger)}
.admin-search input{min-width:0}
.admin-notice-wrap{position:relative}
.admin-notice{position:absolute;z-index:30;right:0;top:calc(100% + 10px);width:270px;padding:12px;background:#fff;border:1px solid var(--color-line);border-radius:10px;box-shadow:var(--shadow-card)}
.admin-notice b{display:block;margin-bottom:6px;font-size:12px}
.admin-notice p{margin:0;padding:8px 0;border-top:1px solid var(--color-line);color:var(--color-ink-soft);font-size:11px;line-height:1.5}
</style>
