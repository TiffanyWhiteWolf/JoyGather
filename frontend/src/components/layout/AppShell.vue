<script setup lang="ts">
import { Bell, ChevronDown, Compass, FileText, LogOut, Map, Menu, MessageCircle, Plus, QrCode, Sparkles, Users, X } from 'lucide-vue-next'
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { RouterLink, RouterView, useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { apiGet, apiPost, apiPut, clearAuthStorage, logout as apiLogout } from '@/lib/api'
import UserQrCode from '@/components/common/UserQrCode.vue'
import QrScannerModal from '@/components/common/QrScannerModal.vue'
import type { User } from '@/types'

const app = useAppStore()
const router = useRouter()
const menuOpen = ref(false)
const cityOpen = ref(false)
const noticeOpen = ref(false)
const qrOpen = ref(false)
const qrTab = ref<'my' | 'scan'>('my')
const showQrScanner = ref(false)
const currentUser = ref<User | null>(null)
const cities = ['杭州', '上海', '南京', '苏州']
interface Notice { id: string; title: string; content: string; targetId?: string; target_id?: string; readFlag?: boolean; read_flag?: boolean }
const notices = ref<Notice[]>([])
const nav = [
  { to: '/', label: '发现', icon: Compass },
  { to: '/discover', label: '地图', icon: Map },
  { to: '/teams', label: '小队', icon: Users },
  { to: '/messages', label: '消息', icon: MessageCircle },
]

async function loadCurrentUser() {
  try {
    currentUser.value = await apiGet<User>('/auth/me')
    notices.value = await apiGet<Notice[]>('/notifications')
    app.notifications = notices.value.filter(item => !(item.readFlag ?? item.read_flag)).length
    await app.refreshUserState()
  } catch {
    clearAuthStorage()
    currentUser.value = null
    notices.value = []
    app.notifications = 0
    await app.refreshUserState()
  }
}

async function openNotice(item: Notice) {
  if (!(item.readFlag ?? item.read_flag)) {
    await apiPut<void>(`/notifications/${item.id}/read`, {})
    item.readFlag = true
    app.notifications = Math.max(0, app.notifications - 1)
  }
  const targetId = item.targetId || item.target_id
  if (targetId) {
    noticeOpen.value = false
    await router.push(`/activities/${targetId}`)
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
})
onBeforeUnmount(() => {
  window.removeEventListener('quju:auth-changed', handleAuthChanged)
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
          <div class="top-popover-wrap">
            <button class="icon-button notice" @click="noticeOpen = !noticeOpen"><Bell :size="19" /><span>{{ app.notifications }}</span></button>
            <div v-if="noticeOpen" class="top-popover notice-menu">
              <b>通知</b>
              <button v-for="item in notices" :key="item.id" @click="openNotice(item)"><b>{{ item.title }}</b><small>{{ item.content }}</small></button>
              <p v-if="!notices.length">暂无新通知</p>
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
.notice-menu{width:250px}
.notice-menu b{display:block;margin-bottom:6px;font-size:12px}
.notice-menu p{margin:0;padding:8px 0;border-top:1px solid var(--color-line);color:var(--color-ink-soft);font-size:11px;line-height:1.5}
.notice-menu button{width:100%;padding:9px 0;border:0;border-top:1px solid var(--color-line);background:transparent;text-align:left}
.notice-menu button b{margin:0 0 3px;font-size:11px}.notice-menu button small{display:block;color:var(--color-ink-soft);font-size:10px;line-height:1.5}
.logout-button{color:var(--color-danger)}
.qr-popover{width:280px;padding:0;overflow:hidden}
.qr-tabs{display:flex;border-bottom:1px solid var(--color-line)}
.qr-tabs button{flex:1;padding:10px;border:0;background:none;font-size:13px;font-weight:800;color:var(--color-ink-soft);cursor:pointer}
.qr-tabs button.active{color:var(--color-primary);box-shadow:inset 0 -2px var(--color-primary)}
.qr-panel{padding:16px;display:flex;flex-direction:column;align-items:center}
.qr-scan-panel p{margin:0 0 12px;color:var(--color-ink-soft);font-size:12px;text-align:center}
</style>
