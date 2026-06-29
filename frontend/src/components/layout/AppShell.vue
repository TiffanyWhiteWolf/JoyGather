<script setup lang="ts">
import { RouterLink, RouterView } from 'vue-router'
import { Bell, ChevronDown, Compass, Map, Menu, MessageCircle, Plus, Sparkles, Users, X } from 'lucide-vue-next'
import { ref } from 'vue'
import { currentUser } from '@/mock/data'
import { useAppStore } from '@/stores/app'

const app = useAppStore()
const menuOpen = ref(false)
const nav = [
  { to: '/', label: '发现', icon: Compass },
  { to: '/discover', label: '地图', icon: Map },
  { to: '/teams', label: '小队', icon: Users },
  { to: '/messages', label: '消息', icon: MessageCircle },
]
</script>

<template>
  <div class="site-shell">
    <header class="topbar">
      <div class="topbar-inner">
        <RouterLink class="brand" to="/" aria-label="趣聚首页">
          <span class="brand-mark"><span></span><span></span><span></span></span>
          <span>趣聚</span>
        </RouterLink>
        <button class="city-pill"><span class="city-dot"></span>{{ app.city }}<ChevronDown :size="14" /></button>
        <nav :class="['main-nav', { open: menuOpen }]">
          <RouterLink v-for="item in nav" :key="item.to" :to="item.to" @click="menuOpen = false">
            <component :is="item.icon" :size="18" />{{ item.label }}
          </RouterLink>
          <RouterLink to="/ai-planner" class="ai-mobile"><Sparkles :size="17" />AI 策划</RouterLink>
        </nav>
        <div class="top-actions">
          <RouterLink to="/ai-planner" class="ai-link"><Sparkles :size="17" />AI 策划</RouterLink>
          <RouterLink to="/create" class="btn btn-primary btn-sm"><Plus :size="17" />发起活动</RouterLink>
          <button class="icon-button notice"><Bell :size="19" /><span>{{ app.notifications }}</span></button>
          <RouterLink to="/profile" class="avatar-link"><img :src="currentUser.avatar" :alt="currentUser.nickname" /></RouterLink>
          <button class="mobile-menu" @click="menuOpen = !menuOpen"><X v-if="menuOpen" /><Menu v-else /></button>
        </div>
      </div>
    </header>
    <main><RouterView /></main>
    <footer class="site-footer">
      <div class="container footer-inner"><div class="brand brand-light"><span class="brand-mark"><span></span><span></span><span></span></span><span>趣聚</span></div><p>去见面，去同频，去发现城市的另一面。</p><div class="footer-links"><RouterLink to="/auth">登录 / 注册</RouterLink><RouterLink to="/admin">运营后台 →</RouterLink></div></div>
    </footer>
  </div>
</template>
