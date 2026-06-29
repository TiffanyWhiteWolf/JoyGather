import { defineStore } from 'pinia'
import { computed, ref, watch } from 'vue'
import type { ActivityDraft } from '@/types'

const readList = (key: string, fallback: string[]) => {
  try { return JSON.parse(localStorage.getItem(key) || '') as string[] } catch { return fallback }
}

export const useAppStore = defineStore('app', () => {
  const city = ref('杭州')
  const notifications = ref(3)
  const joinedActivityIds = ref<string[]>(readList('quju:joined', ['act-001']))
  const waitingActivityIds = ref<string[]>(readList('quju:waiting', ['act-003']))
  const joinedTeamIds = ref<string[]>(readList('quju:teams', ['team-01']))
  const draft = ref<ActivityDraft | null>(null)
  const submittedActivities = ref<ActivityDraft[]>([])
  const toast = ref('')
  let toastTimer = 0

  try { draft.value = JSON.parse(localStorage.getItem('quju:draft') || 'null') as ActivityDraft | null } catch { draft.value = null }
  try { submittedActivities.value = JSON.parse(localStorage.getItem('quju:submitted') || '[]') as ActivityDraft[] } catch { submittedActivities.value = [] }

  watch(joinedActivityIds, value => localStorage.setItem('quju:joined', JSON.stringify(value)), { deep: true })
  watch(waitingActivityIds, value => localStorage.setItem('quju:waiting', JSON.stringify(value)), { deep: true })
  watch(joinedTeamIds, value => localStorage.setItem('quju:teams', JSON.stringify(value)), { deep: true })

  function showToast(message: string) {
    toast.value = message
    window.clearTimeout(toastTimer)
    toastTimer = window.setTimeout(() => { toast.value = '' }, 2600)
  }

  function joinActivity(id: string, full = false) {
    if (full) {
      if (!waitingActivityIds.value.includes(id)) waitingActivityIds.value.push(id)
      showToast('已加入候补队列，有名额时会第一时间通知你')
      return '候补中' as const
    }
    if (!joinedActivityIds.value.includes(id)) joinedActivityIds.value.push(id)
    waitingActivityIds.value = waitingActivityIds.value.filter(item => item !== id)
    showToast('报名成功，期待现场见！')
    return '已报名' as const
  }

  function cancelRegistration(id: string) {
    const wasWaiting = waitingActivityIds.value.includes(id)
    joinedActivityIds.value = joinedActivityIds.value.filter(item => item !== id)
    waitingActivityIds.value = waitingActivityIds.value.filter(item => item !== id)
    showToast(wasWaiting ? '已退出候补队列' : '已取消报名，名额已释放')
  }

  function joinTeam(id: string) {
    if (!joinedTeamIds.value.includes(id)) joinedTeamIds.value.push(id)
    showToast('已加入小队，群聊已同步开放')
  }

  function saveDraft(value: ActivityDraft) {
    draft.value = { ...value, updatedAt: new Date().toLocaleString('zh-CN', { hour12: false }) }
    localStorage.setItem('quju:draft', JSON.stringify(draft.value))
    showToast('草稿已保存，可稍后继续编辑')
  }

  function clearDraft() {
    draft.value = null
    localStorage.removeItem('quju:draft')
  }

  function submitActivity(value: ActivityDraft) {
    const submitted = { ...value, updatedAt: new Date().toLocaleString('zh-CN', { hour12: false }) }
    submittedActivities.value = [submitted, ...submittedActivities.value.filter(item => item.id !== value.id)]
    localStorage.setItem('quju:submitted', JSON.stringify(submittedActivities.value))
    clearDraft()
    showToast(value.capacity > 50 ? '已提交人工审核，可在活动管理中查看进度' : 'AI 安全审核通过，活动已发布')
  }

  const registrationCount = computed(() => joinedActivityIds.value.length)

  return {
    city, notifications, joinedActivityIds, waitingActivityIds, joinedTeamIds, draft, submittedActivities, toast,
    registrationCount, showToast, joinActivity, cancelRegistration, joinTeam, saveDraft, clearDraft, submitActivity,
  }
})
