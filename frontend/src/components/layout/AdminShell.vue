<script setup lang="ts">
import { RouterLink, RouterView, useRoute } from 'vue-router'
import { Activity, Bell, ChevronLeft, ClipboardCheck, LayoutDashboard, Search, ShieldCheck, Users } from 'lucide-vue-next'

const route = useRoute()
const nav = [
  { to: '/admin', label: '数据概览', icon: LayoutDashboard },
  { to: '/admin/reviews', label: '审核中心', icon: ClipboardCheck },
  { to: '/admin/users', label: '用户管理', icon: Users },
  { to: '/admin/content', label: '内容治理', icon: ShieldCheck },
]
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <RouterLink class="brand admin-brand" to="/"><span class="brand-mark"><span></span><span></span><span></span></span><span>趣聚</span><small>运营台</small></RouterLink>
      <nav class="admin-nav">
        <RouterLink v-for="item in nav" :key="item.to" :to="item.to" :class="{ active: route.path === item.to }"><component :is="item.icon" :size="19" />{{ item.label }}<span v-if="item.to === '/admin/reviews'" class="nav-count">12</span></RouterLink>
      </nav>
      <div class="admin-profile"><img src="https://i.pravatar.cc/80?img=11" alt="管理员" /><div><b>周晴</b><small>超级管理员</small></div></div>
      <RouterLink to="/" class="back-site"><ChevronLeft :size="16" />返回用户端</RouterLink>
    </aside>
    <section class="admin-main">
      <header class="admin-topbar"><div class="admin-search"><Search :size="18" /><input placeholder="搜索用户、活动或小队" /></div><button class="icon-button"><Bell :size="19" /><span>4</span></button></header>
      <div class="admin-content"><RouterView /></div>
    </section>
  </div>
</template>
