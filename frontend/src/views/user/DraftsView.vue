<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { useRouter } from 'vue-router'
import { CalendarDays, FileEdit, FilePlus2, MapPin, Trash2 } from 'lucide-vue-next'
import { apiDelete, apiGet } from '@/lib/api'
import { useAppStore } from '@/stores/app'
import type { Activity } from '@/types'

const router = useRouter()
const app = useAppStore()
const drafts = ref<Activity[]>([])
const loading = ref(true)
const error = ref('')
const deletingId = ref('')

async function loadDrafts() {
  loading.value = true
  error.value = ''
  try {
    drafts.value = await apiGet<Activity[]>('/activities/drafts')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '草稿加载失败'
  } finally {
    loading.value = false
  }
}

async function removeDraft(draft: Activity) {
  if (!window.confirm(`确定删除草稿“${draft.title}”吗？删除后无法恢复。`)) return
  deletingId.value = draft.id
  try {
    await apiDelete<void>(`/activities/drafts/${draft.id}`)
    drafts.value = drafts.value.filter(item => item.id !== draft.id)
    app.showToast('草稿已删除')
  } catch (err) {
    error.value = err instanceof Error ? err.message : '删除草稿失败'
  } finally {
    deletingId.value = ''
  }
}

function displayTime(value?: string) {
  if (!value) return '刚刚'
  return value.replace('T', ' ').slice(0, 16)
}

onMounted(loadDrafts)
</script>

<template>
  <div class="container drafts-page">
    <header class="drafts-head">
      <div><span class="eyebrow">ACTIVITY DRAFTS</span><h1>我的草稿</h1><p>未完成的活动只对你可见，不会出现在首页、搜索或地图中。</p></div>
      <RouterLink to="/create" class="btn btn-primary"><FilePlus2 :size="17" />新建活动</RouterLink>
    </header>
    <p v-if="error" class="draft-error">{{ error }}</p>
    <div v-if="loading" class="draft-empty">正在加载草稿…</div>
    <div v-else-if="!drafts.length" class="draft-empty"><FileEdit :size="34" /><h2>还没有草稿</h2><p>创建活动时随时保存，就能在这里继续。</p><RouterLink to="/create" class="btn btn-primary">发起第一场活动</RouterLink></div>
    <div v-else class="draft-list">
      <article v-for="draft in drafts" :key="draft.id" class="draft-card">
        <img :src="draft.cover" :alt="draft.title" />
        <div class="draft-copy"><div><span>{{ draft.category }}</span><small>保存于 {{ displayTime(draft.updatedAt) }}</small></div><h2>{{ draft.title }}</h2><p>{{ draft.summary }}</p><div class="draft-meta"><span><CalendarDays :size="14" />{{ draft.date || '时间待补充' }}</span><span><MapPin :size="14" />{{ draft.location || '地点待补充' }}</span></div></div>
        <div class="draft-actions"><button class="btn btn-outline" @click="router.push({ path: '/create', query: { draft: draft.id } })"><FileEdit :size="16" />继续编辑</button><button class="delete-button" :disabled="deletingId===draft.id" @click="removeDraft(draft)"><Trash2 :size="16" />{{ deletingId===draft.id ? '删除中' : '删除' }}</button></div>
      </article>
    </div>
  </div>
</template>

<style scoped>
.drafts-page{padding:48px 0 80px}.drafts-head{display:flex;align-items:flex-end;justify-content:space-between;gap:20px;margin-bottom:28px}.drafts-head h1{margin:5px 0 8px;font-size:36px}.drafts-head p{margin:0;color:var(--color-ink-soft)}.draft-list{display:grid;gap:14px}.draft-card{display:grid;grid-template-columns:150px 1fr auto;gap:22px;align-items:center;padding:16px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-lg);box-shadow:var(--shadow-soft)}.draft-card>img{width:150px;height:112px;border-radius:12px;object-fit:cover;background:var(--color-bg)}.draft-copy>div:first-child{display:flex;gap:12px;align-items:center}.draft-copy>div:first-child span{padding:4px 8px;border-radius:var(--radius-pill);background:var(--color-primary-soft);color:var(--color-primary);font-size:10px;font-weight:800}.draft-copy small{color:var(--color-ink-soft)}.draft-copy h2{margin:10px 0 5px;font-size:19px}.draft-copy p{margin:0;color:var(--color-ink-soft);font-size:11px}.draft-meta{display:flex;gap:18px;margin-top:13px;color:var(--color-ink-soft);font-size:10px}.draft-meta span{display:flex;align-items:center;gap:5px}.draft-actions{display:flex;flex-direction:column;gap:8px}.delete-button{padding:8px;border:0;background:none;color:var(--color-danger);display:flex;align-items:center;justify-content:center;gap:5px;font-size:11px}.draft-empty{padding:70px 20px;border:1px dashed var(--color-line);border-radius:var(--radius-lg);background:#fff;color:var(--color-ink-soft);text-align:center}.draft-empty svg{color:var(--color-primary)}.draft-empty h2{color:var(--color-ink)}.draft-empty .btn{margin-top:12px}.draft-error{padding:12px;border-radius:10px;background:#ffeaed;color:var(--color-danger)}
@media(max-width:760px){.drafts-head{align-items:flex-start;flex-direction:column}.draft-card{grid-template-columns:90px 1fr}.draft-card>img{width:90px;height:90px}.draft-actions{grid-column:1/-1;flex-direction:row;justify-content:flex-end}.draft-meta{flex-direction:column;gap:5px}}
</style>
