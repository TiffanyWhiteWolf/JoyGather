<script setup lang="ts">
import { RouterLink, RouterView, useRoute, useRouter } from 'vue-router'
import { Bell, ClipboardCheck, LayoutDashboard, LogOut, Megaphone, Plus, Search, ShieldCheck, Users, X } from 'lucide-vue-next'
import { onMounted, provide, ref } from 'vue'
import { useAppStore } from '@/stores/app'
import { apiGet, apiPost, logout as apiLogout } from '@/lib/api'
import type { NotificationItem } from '@/types'

const route = useRoute()
const router = useRouter()
const app = useAppStore()
const query = ref('')
const noticeOpen = ref(false)
const publishedNotifications = ref<NotificationItem[]>([])
const publishOpen = ref(false)
const publishForm = ref({ title: '', content: '', type: '系统通知' })
const publishError = ref('')
const publishVersion = ref(0)
const typeOptions = ['系统通知', '安全提醒', '活动通知', '运营公告']

const nav = [
  { to: '/admin', label: '数据概览', icon: LayoutDashboard },
  { to: '/admin/reviews', label: '审核中心', icon: ClipboardCheck },
  { to: '/admin/users', label: '用户管理', icon: Users },
  { to: '/admin/content', label: '内容治理', icon: ShieldCheck },
  { to: '/admin/notifications', label: '通知管理', icon: Megaphone },
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

async function fetchPublishedNotifications() {
  try {
    const rows = await apiGet<Record<string, unknown>[]>('/admin/notifications')
    publishedNotifications.value = rows.slice(0, 5).map(row => ({
      id: String(row.id ?? ''),
      userId: String(row.user_id ?? row.userId ?? ''),
      type: String(row.type ?? ''),
      title: String(row.title ?? ''),
      content: typeof row.content === 'string' ? row.content : '',
      read: true,
      createdAt: typeof row.created_at === 'string' ? row.created_at : '',
    }))
  } catch {
    publishedNotifications.value = []
  }
}

function openPublish() {
  publishForm.value = { title: '', content: '', type: '系统通知' }
  publishError.value = ''
  publishOpen.value = true
  noticeOpen.value = false
}

async function publish() {
  if (!publishForm.value.title.trim()) { publishError.value = '通知标题不能为空'; return }
  if (!publishForm.value.content.trim()) { publishError.value = '通知内容不能为空'; return }
  try {
    await apiPost<void>('/admin/notifications', {
      title: publishForm.value.title.trim(),
      content: publishForm.value.content.trim(),
      type: publishForm.value.type,
    })
    app.showToast('通知已发布')
    publishOpen.value = false
    publishVersion.value++
    await fetchPublishedNotifications()
  } catch (e: unknown) {
    publishError.value = e instanceof Error ? e.message : '发布失败，请重试'
  }
}

provide('openPublish', openPublish)
provide('publishVersion', publishVersion)

onMounted(fetchPublishedNotifications)
</script>

<template>
  <div class="admin-shell">
    <aside class="admin-sidebar">
      <RouterLink class="brand admin-brand" to="/"><span class="brand-mark"><span></span><span></span><span></span></span><span>趣聚</span><small>运营台</small></RouterLink>
      <nav class="admin-nav">
        <RouterLink v-for="item in nav" :key="item.to" :to="item.to" :class="{ active: route.path === item.to }"><component :is="item.icon" :size="19" />{{ item.label }}</RouterLink>
      </nav>
      <div class="admin-profile"><img src="https://i.pravatar.cc/80?img=11" alt="管理员" /><div><b>周晴</b><small>超级管理员</small></div></div>
      <button class="back-site admin-logout" @click="logout"><LogOut :size="16" />退出登录</button>
    </aside>
    <section class="admin-main">
      <header class="admin-topbar">
        <form class="admin-search" @submit.prevent="search"><Search :size="18" /><input v-model="query" placeholder="搜索用户、活动或小队" /></form>
        <div class="admin-notice-wrap">
          <button class="icon-button" @click="noticeOpen = !noticeOpen"><Bell :size="19" /></button>
          <div v-if="noticeOpen" class="admin-notice">
            <div class="notice-header">
              <b>已发通知</b>
              <button class="notice-publish-btn" @click="openPublish"><Plus :size="14" />发布</button>
            </div>
            <div v-if="!publishedNotifications.length" class="notice-empty">暂无已发布的通知</div>
            <p v-for="item in publishedNotifications" :key="item.id" class="notice-item">
              <span>{{ item.title }}</span>
              <small>{{ item.createdAt }}</small>
            </p>
            <RouterLink to="/admin/notifications" class="notice-link" @click="noticeOpen = false">查看全部</RouterLink>
          </div>
        </div>
      </header>
      <div class="admin-content"><RouterView /></div>
    </section>

    <!-- 发布通知弹窗 -->
    <div v-if="publishOpen" class="publish-modal" @click.self="publishOpen = false">
      <div class="publish-modal-box">
        <button class="publish-close" @click="publishOpen = false"><X /></button>
        <h2>发布通知</h2>
        <label>
          通知类型 *
          <select v-model="publishForm.type">
            <option v-for="t in typeOptions" :key="t" :value="t">{{ t }}</option>
          </select>
        </label>
        <label>
          通知标题 *
          <input v-model="publishForm.title" placeholder="请输入通知标题" />
        </label>
        <label>
          通知内容 *
          <textarea v-model="publishForm.content" placeholder="请输入通知正文内容" rows="5"></textarea>
        </label>
        <p v-if="publishError" class="publish-modal-error">{{ publishError }}</p>
        <footer>
          <button class="btn btn-outline" @click="publishOpen = false">取消</button>
          <button class="btn btn-dark" @click="publish">确认发布</button>
        </footer>
      </div>
    </div>
  </div>
</template>

<style scoped>
.admin-logout{width:100%;border:0;background:transparent;color:var(--color-danger)}
.admin-search input{min-width:0}
.admin-notice-wrap{position:relative}
.admin-notice{position:absolute;z-index:30;right:0;top:calc(100% + 10px);width:290px;max-height:380px;overflow-y:auto;padding:12px;background:#fff;border:1px solid var(--color-line);border-radius:10px;box-shadow:var(--shadow-card)}
.notice-header{display:flex;justify-content:space-between;align-items:center;margin-bottom:8px}
.notice-header b{font-size:13px;margin-bottom:0}
.notice-publish-btn{display:flex;align-items:center;gap:3px;padding:4px 8px;border:0;border-radius:6px;background:var(--color-primary);color:#fff;font-size:11px;font-weight:700;cursor:pointer}
.notice-publish-btn:hover{opacity:.85}
.notice-empty{padding:16px 0;color:var(--color-ink-soft);font-size:13px;text-align:center}
.notice-item{padding:10px 0;border-top:1px solid var(--color-line);margin:0;display:flex;flex-direction:column;gap:2px;font-size:13px}
.notice-item span{display:block;white-space:nowrap;overflow:hidden;text-overflow:ellipsis}
.notice-item small{color:var(--color-ink-soft);font-size:11px}
.notice-link{display:block;margin-top:8px;padding-top:8px;border-top:1px solid var(--color-line);text-align:center;color:var(--color-primary);font-size:12px;font-weight:700;text-decoration:none}
.notice-link:hover{color:var(--color-primary-dark)}
.publish-modal{position:fixed;z-index:100;inset:0;padding:18px;background:rgba(13,21,34,.55);display:grid;place-items:center}
.publish-modal-box{position:relative;width:min(100%,460px);max-height:90vh;overflow-y:auto;padding:28px;background:#fff;border-radius:16px}
.publish-modal-box h2{margin:0 0 18px;font-size:20px}
.publish-close{position:absolute;right:18px;top:18px;width:32px;height:32px;border:0;border-radius:50%;background:var(--color-bg);display:grid;place-items:center;cursor:pointer}
.publish-modal-box label{display:block;margin-bottom:14px;font-size:11px;font-weight:700;color:var(--color-ink-soft)}
.publish-modal-box label input,.publish-modal-box label textarea,.publish-modal-box label select{display:block;width:100%;margin-top:6px;padding:10px;border:1px solid var(--color-line);border-radius:8px;font-size:13px;outline:none;font-family:inherit}
.publish-modal-box label textarea{resize:vertical}
.publish-modal-error{color:var(--color-danger);font-size:12px;margin:0 0 10px}
.publish-modal-box footer{display:flex;gap:10px;justify-content:flex-end;margin-top:10px}
</style>
