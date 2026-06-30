import { defineStore } from 'pinia'
import { computed, ref } from 'vue'
import { apiGet } from '@/lib/api'
import type { ActivityDraft } from '@/types'

export const useAppStore = defineStore('app', () => {
  const city = ref('杭州')
  const notifications = ref(3)
  const joinedActivityIds = ref<string[]>([])
  const waitingActivityIds = ref<string[]>([])
  const joinedTeamIds = ref<string[]>([])
  const friendIds = ref<string[]>([])
  const followedIds = ref<string[]>([])
  const blockedIds = ref<string[]>([])
  const draft = ref<ActivityDraft | null>(null)
  const submittedActivities = ref<ActivityDraft[]>([])
  const toast = ref('')
  let toastTimer = 0

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

  function addFriend(id: string) {
    if (!friendIds.value.includes(id)) friendIds.value.push(id)
    if (!followedIds.value.includes(id)) followedIds.value.push(id)
  }

  function removeFriend(id: string) {
    friendIds.value = friendIds.value.filter(item => item !== id)
    followedIds.value = followedIds.value.filter(item => item !== id)
  }

  function addFollowedId(id: string) {
    if (!followedIds.value.includes(id)) followedIds.value.push(id)
  }

  function removeFollowedId(id: string) {
    followedIds.value = followedIds.value.filter(item => item !== id)
    friendIds.value = friendIds.value.filter(item => item !== id)
  }

  function addBlockedId(id: string) {
    if (!blockedIds.value.includes(id)) blockedIds.value.push(id)
  }

  function removeBlockedId(id: string) {
    blockedIds.value = blockedIds.value.filter(item => item !== id)
  }

  function saveDraft(value: ActivityDraft) {
    draft.value = { ...value, updatedAt: new Date().toLocaleString('zh-CN', { hour12: false }) }
    showToast('草稿已保存，可稍后继续编辑')
  }

  function clearDraft() {
    draft.value = null
  }

  function submitActivity(value: ActivityDraft) {
    const submitted = { ...value, updatedAt: new Date().toLocaleString('zh-CN', { hour12: false }) }
    submittedActivities.value = [submitted, ...submittedActivities.value.filter(item => item.id !== value.id)]
    clearDraft()
    showToast(value.capacity > 50 ? '已提交人工审核，可在活动管理中查看进度' : 'AI 安全审核通过，活动已发布')
  }

  function clearUserState() {
    joinedActivityIds.value = []
    waitingActivityIds.value = []
    joinedTeamIds.value = []
    friendIds.value = []
    followedIds.value = []
    blockedIds.value = []
    draft.value = null
    submittedActivities.value = []
  }

  async function refreshUserState() {
    if (!localStorage.getItem('quju:token')) {
      clearUserState()
      return
    }
    try {
      const statuses = await apiGet<Record<string, string>>('/activities/registrations/me')
      joinedActivityIds.value = Object.entries(statuses).filter(([, status]) => status === '已报名' || status === '已签到').map(([id]) => id)
      waitingActivityIds.value = Object.entries(statuses).filter(([, status]) => status === '候补中').map(([id]) => id)
    } catch {
      joinedActivityIds.value = []
      waitingActivityIds.value = []
    }
    try {
      joinedTeamIds.value = await apiGet<string[]>('/teams/memberships/me')
    } catch {
      joinedTeamIds.value = []
    }
    try {
      const friends = await apiGet<{ userId: string }[]>('/friends')
      friendIds.value = friends.map(f => f.userId)
    } catch {
      friendIds.value = []
    }
    try {
      followedIds.value = await apiGet<string[]>('/follows/me')
    } catch {
      followedIds.value = []
    }
    try {
      blockedIds.value = await apiGet<string[]>('/blocks/me')
    } catch {
      blockedIds.value = []
    }
  }

  const registrationCount = computed(() => joinedActivityIds.value.length)

  return {
    city, notifications, joinedActivityIds, waitingActivityIds, joinedTeamIds, friendIds, followedIds, blockedIds, draft, submittedActivities, toast,
    registrationCount, showToast, joinActivity, cancelRegistration, joinTeam, addFriend, removeFriend, addFollowedId, removeFollowedId, addBlockedId, removeBlockedId, saveDraft, clearDraft, submitActivity, clearUserState, refreshUserState,
  }
})
