<script setup lang="ts">
import { Bell, ChevronDown, Compass, FileText, LogOut, Map, Menu, MessageCircle, Plus, QrCode, Sparkles, Users, X } from 'lucide-vue-next'
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { apiGet, apiPost, clearAuthStorage, logout as apiLogout } from '@/lib/api'
import UserQrCode from '@/components/common/UserQrCode.vue'
import QrScannerModal from '@/components/common/QrScannerModal.vue'
import type { NotificationItem, User } from '@/types'

const app = useAppStore()
const router = useRouter()
const menuOpen = ref(false)
const cityOpen = ref(false)
const noticeOpen = ref(false)
const noticeWrap = ref<HTMLElement | null>(null)
const qrOpen = ref(false)
const qrTab = ref<'my' | 'scan'>('my')
const showQrScanner = ref(false)
const currentUser = ref<User | null>(null)
const cities = ['杭州', '上海', '南京', '苏州']
const nav = [
  { to: '/', label: '发现', icon: Compass },
  { to: '/discover', label: '地图', icon: Map },
  { to: '/teams', label: '小队', icon: Users },
  { to: '/messages', label: '消息', icon: MessageCircle },
]
const noticePreview = computed(() => app.notificationItems.slice(0, 8))

async function loadCurrentUser() {
  try {
    currentUser.value = await apiGet<User>('/auth/me')
    await app.refreshUserState()
  } catch {
    clearAuthStorage()
    currentUser.value = null
    await app.refreshUserState()
  }
}

async function toggleNotice() {
  noticeOpen.value = !noticeOpen.value
  if (noticeOpen.value && currentUser.value) {
    await app.refreshNotifications()
  }
}

async function openNotice(item: NotificationItem) {
  try {
    await app.markNotificationRead(item.id)
  } catch (err) {
    app.showToast(err instanceof Error ? err.message : '通知状态更新失败')
  }
}

function formatNoticeTime(value?: string) {
  if (!value) return ''
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return date.toLocaleString('zh-CN', { hour12: false, month: '2-digit', day: '2-digit', hour: '2-digit', minute: '2-digit' })
}

function handleDocumentClick(event: MouseEvent) {
  const target = event.target as Node | null
  if (noticeOpen.value && noticeWrap.value && target && !noticeWrap.value.contains(target)) {
    noticeOpen.value = false
  }
}

function selectCity(city: string) {
  app.city = city
  cityOpen.value = false
  app.showToast(`已切换到${city}`)
}

async function logout() {
  await apiLogout()
  app.clearUserState()
  currentUser.value = null
  await router.push('/')
  app.showToast('已退出登录')
}

async function handleScanned(userId: string, _nickname: string) {
  showQrScanner.value = false
  if (userId === currentUser.value?.id) {
    alert('这是你自己的二维码')
    return
  }
  try {
    await apiPost('/friends/requests', { userId, source: 'QR_CODE', message: '' })
    alert('好友申请已发送！')
  } catch (err) {
    alert(err instanceof Error ? err.message : '发送好友申请失败')
  }
}

function handleAuthChanged() {
  void loadCurrentUser()
}

onMounted(async () => {
  await loadCurrentUser()
  window.addEventListener('quju:auth-changed', handleAuthChanged)
  document.addEventListener('click', handleDocumentClick)
})
onBeforeUnmount(() => {
  window.removeEventListener('quju:auth-changed', handleAuthChanged)
  document.removeEventListener('click', handleDocumentClick)
})
</script>

<template>
  <div class="site-shell">
    <header class="topbar">
      <div class="topbar-inner">
        <RouterLink class="brand" to="/" aria-label="趣聚首页">
          <span class="brand-mark"><span></span><span></span><span></span></span>
          <span>趣聚</span>
        </RouterLink>
        <div class="top-popover-wrap">
          <button class="city-pill" @click="cityOpen = !cityOpen"><span class="city-dot"></span>{{ app.city }}<ChevronDown :size="14" /></button>
          <div v-if="cityOpen" class="top-popover city-menu">
            <button v-for="city in cities" :key="city" :class="{ active: app.city === city }" @click="selectCity(city)">{{ city }}</button>
          </div>
        </div>
        <nav :class="['main-nav', { open: menuOpen }]">
          <RouterLink v-for="item in nav" :key="item.to" :to="item.to" @click="menuOpen = false">
            <component :is="item.icon" :size="18" />{{ item.label }}
          </RouterLink>
          <RouterLink to="/ai-planner" class="ai-mobile"><Sparkles :size="17" />AI 策划</RouterLink>
          <RouterLink to="/drafts" class="ai-mobile"><FileText :size="17" />我的草稿</RouterLink>
        </nav>
        <div class="top-actions">
          <RouterLink to="/ai-planner" class="ai-link"><Sparkles :size="17" />AI 策划</RouterLink>
          <RouterLink v-if="currentUser" to="/drafts" class="ai-link"><FileText :size="17" />我的草稿</RouterLink>
          <RouterLink to="/create" class="btn btn-primary btn-sm"><Plus :size="17" />发起活动</RouterLink>
          <div ref="noticeWrap" class="top-popover-wrap">
            <button class="icon-button notice" @click="toggleNotice"><Bell :size="19" /><span v-if="app.notifications">{{ app.notifications }}</span></button>
            <div v-if="noticeOpen" class="top-popover notice-menu">
              <b>通知</b>
              <button v-for="item in noticePreview" :key="item.id" :class="['notice-item', { unread: !item.read }]" @click="openNotice(item)">
                <span class="notice-meta">
                  <strong>{{ item.title }}</strong>
                  <small>{{ formatNoticeTime(item.createdAt) }}</small>
                </span>
                <span v-if="item.content" class="notice-content">{{ item.content }}</span>
                <span class="notice-tag">{{ item.read ? '已读' : '未读' }}</span>
              </button>
              <p v-if="currentUser && !noticePreview.length" class="notice-empty">暂时还没有通知</p>
              <p v-else-if="!currentUser" class="notice-empty">登录后可查看站内通知</p>
            </div>
          </div>
          <div v-if="currentUser" class="top-popover-wrap">
            <button class="icon-button qr" @click="qrOpen=!qrOpen;qrTab='my'"><QrCode :size="19" /></button>
            <div v-if="qrOpen" class="top-popover qr-popover">
              <div class="qr-tabs">
                <button :class="{active:qrTab==='my'}" @click="qrTab='my'">我的二维码</button>
                <button :class="{active:qrTab==='scan'}" @click="qrTab='scan'">扫一扫</button>
              </div>
              <div v-if="qrTab==='my'" class="qr-panel">
                <UserQrCode :user-id="currentUser.id" :nickname="currentUser.nickname" />
              </div>
              <div v-else class="qr-panel qr-scan-panel">
                <p>扫描其他用户的二维码即可发送好友申请</p>
                <button class="btn btn-primary btn-sm" @click="showQrScanner=true;qrOpen=false">打开摄像头</button>
              </div>
            </div>
          </div>
          <RouterLink v-if="currentUser" to="/profile" class="avatar-link"><img :src="currentUser.avatar" :alt="currentUser.nickname" /></RouterLink>
          <button v-if="currentUser" class="icon-button logout-button" title="退出登录" @click="logout"><LogOut :size="18" /></button>
          <RouterLink v-else to="/auth" class="btn btn-outline btn-sm">登录</RouterLink>
          <button class="mobile-menu" @click="menuOpen = !menuOpen"><X v-if="menuOpen" /><Menu v-else /></button>
        </div>
      </div>
    </header>
    <main><RouterView /></main>
    <footer class="site-footer">
      <div class="container footer-inner"><div class="brand brand-light"><span class="brand-mark"><span></span><span></span><span></span></span><span>趣聚</span></div><p>去见面，去同频，去发现城市的另一面。</p><div class="footer-links"><RouterLink to="/auth">登录 / 注册</RouterLink><RouterLink to="/admin">运营后台 →</RouterLink></div></div>
    </footer>
  </div>
  <QrScannerModal v-if="showQrScanner" @close="showQrScanner=false" @scanned="handleScanned" />
</template>

<style scoped>
.top-popover-wrap{position:relative;display:flex}
.top-popover{position:absolute;z-index:40;right:0;top:calc(100% + 10px);min-width:160px;padding:10px;background:#fff;border:1px solid var(--color-line);border-radius:10px;box-shadow:var(--shadow-card)}
.city-menu{display:grid;gap:4px}
.city-menu button{padding:8px 10px;border:0;border-radius:8px;background:#fff;text-align:left;font-size:12px}
.city-menu button.active,.city-menu button:hover{background:var(--color-primary-soft);color:var(--color-primary);font-weight:800}
.notice-menu{width:320px;display:grid;gap:8px}
.notice-menu b{display:block;margin-bottom:2px;font-size:12px}
.notice-item{padding:10px 0;border:0;border-top:1px solid var(--color-line);background:none;text-align:left;display:grid;gap:5px;cursor:pointer}
.notice-item.unread strong{color:var(--color-primary)}
.notice-item:first-of-type{border-top:1px solid var(--color-line)}
.notice-meta{display:flex;align-items:center;justify-content:space-between;gap:10px}
.notice-meta strong{font-size:12px;font-weight:800;color:var(--color-ink)}
.notice-meta small{color:var(--color-ink-soft);font-size:10px;white-space:nowrap}
.notice-content{color:var(--color-ink-soft);font-size:11px;line-height:1.5}
.notice-tag{justify-self:flex-start;padding:2px 8px;border-radius:999px;background:var(--color-bg);color:var(--color-ink-soft);font-size:10px;font-weight:700}
.notice-item.unread .notice-tag{background:var(--color-primary-soft);color:var(--color-primary)}
.notice-empty{margin:0;padding:10px 0;border-top:1px solid var(--color-line);color:var(--color-ink-soft);font-size:11px;line-height:1.5}
.logout-button{color:var(--color-danger)}
.qr-popover{width:280px;padding:0;overflow:hidden}
.qr-tabs{display:flex;border-bottom:1px solid var(--color-line)}
.qr-tabs button{flex:1;padding:10px;border:0;background:none;font-size:13px;font-weight:800;color:var(--color-ink-soft);cursor:pointer}
.qr-tabs button.active{color:var(--color-primary);box-shadow:inset 0 -2px var(--color-primary)}
.qr-panel{padding:16px;display:flex;flex-direction:column;align-items:center}
.qr-scan-panel p{margin:0 0 12px;color:var(--color-ink-soft);font-size:12px;text-align:center}
</style>
