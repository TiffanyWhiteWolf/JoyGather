<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { apiDelete, apiGet, apiPost } from '@/lib/api'
import { useAppStore } from '@/stores/app'
import { MapPin, Shield, UserCheck, UserMinus, UserPlus } from 'lucide-vue-next'

const route = useRoute()
const router = useRouter()
const app = useAppStore()

interface PublicUser {
  id: string
  nickname: string
  avatar: string
  role: string
  city: string
  gender?: string
  birthday?: string
  bio: string
  interests: string[]
  following: number
  followers: number
  credit: number
  verified?: boolean
  merchantName?: string
  merchantFields?: string[]
  isFriend?: boolean
  isFollowed?: boolean
  isBlocked?: boolean
}

const user = ref<PublicUser | null>(null)
const currentUserId = ref('')
const loading = ref(true)
const error = ref('')
const actionLoading = ref(false)
const showBlockConfirm = ref(false)
const showUnfollowConfirm = ref(false)

const isSelf = computed(() => user.value?.id === currentUserId.value)
const isBlocked = computed(() => app.blockedIds.includes(user.value?.id ?? ''))
const isFollowed = computed(() => app.followedIds.includes(user.value?.id ?? ''))
const isFriend = computed(() => app.friendIds.includes(user.value?.id ?? ''))

async function loadUser(userId: string) {
  loading.value = true
  error.value = ''
  try {
    user.value = await apiGet<PublicUser>(`/users/${userId}`)
  } catch (err) {
    error.value = err instanceof Error ? err.message : '加载失败'
  } finally {
    loading.value = false
  }
}

onMounted(async () => {
  try {
    const me = await apiGet<{ id: string }>('/auth/me')
    currentUserId.value = me.id
    await app.refreshUserState()
  } catch { /* not logged in */ }
  const userId = route.params.userId as string
  if (userId) loadUser(userId)
})

watch(() => route.params.userId, (id) => {
  if (id) loadUser(id as string)
})

async function toggleFollow() {
  if (!user.value || actionLoading.value) return
  if (isFollowed.value) {
    showUnfollowConfirm.value = true
    return
  }
  actionLoading.value = true
  try {
    await apiPost(`/follows/${user.value.id}`, {})
    app.addFollowedId(user.value.id)
  } catch (err) {
    alert(err instanceof Error ? err.message : '操作失败')
  } finally {
    actionLoading.value = false
  }
}

async function confirmUnfollow() {
  if (!user.value) return
  actionLoading.value = true
  try {
    await apiDelete(`/follows/${user.value.id}`)
    app.removeFollowedId(user.value.id)
    showUnfollowConfirm.value = false
  } catch (err) {
    alert(err instanceof Error ? err.message : '操作失败')
  } finally {
    actionLoading.value = false
  }
}

async function sendFriendRequest() {
  if (!user.value) return
  actionLoading.value = true
  try {
    await apiPost('/friends/requests', { userId: user.value.id, source: 'PROFILE', message: '' })
    alert('好友申请已发送！')
  } catch (err) {
    alert(err instanceof Error ? err.message : '发送失败')
  } finally {
    actionLoading.value = false
  }
}

async function handleBlock() {
  if (!user.value || actionLoading.value) return
  actionLoading.value = true
  try {
    if (isBlocked.value) {
      await apiDelete(`/blocks/${user.value.id}`)
      app.removeBlockedId(user.value.id)
      app.showToast('已取消拉黑')
    } else {
      await apiPost(`/blocks/${user.value.id}`, { reason: '' })
      app.removeFollowedId(user.value.id)
      app.addBlockedId(user.value.id)
      app.showToast('已拉黑')
    }
    showBlockConfirm.value = false
  } catch (err) {
    alert(err instanceof Error ? err.message : '操作失败')
  } finally {
    actionLoading.value = false
  }
}

function goToChat() {
  router.push('/messages')
}
</script>

<template>
  <div class="container user-profile-page">
    <button class="back-btn" @click="router.back()">&larr; 返回</button>

    <div v-if="loading" class="loading">加载中...</div>
    <div v-else-if="error" class="error-state">{{ error }}</div>

    <template v-else-if="user">
      <!-- 头像卡片 -->
      <div class="profile-card">
        <div class="card-cover" :style="{ backgroundImage: `url(${user.avatar})` }"></div>
        <div class="card-body">
          <img class="profile-avatar" :src="user.avatar" :alt="user.nickname" />
          <div class="card-info">
            <h1 class="name-row">
              {{ user.nickname }}
              <span v-if="user.verified" class="verified-badge" title="已认证"><Shield :size="12" /></span>
            </h1>
            <p class="role-city">
              <span class="role-tag">{{ user.role }}</span>
              <span v-if="user.city" class="city"><MapPin :size="12" />{{ user.city }}</span>
            </p>
            <p v-if="user.bio" class="profile-bio">{{ user.bio }}</p>
          </div>
        </div>
      </div>

      <!-- 统计行 -->
      <div class="stats-row">
        <div class="stat"><b>{{ user.following }}</b><span>关注</span></div>
        <div class="stat divider"></div>
        <div class="stat"><b>{{ user.followers }}</b><span>粉丝</span></div>
        <div class="stat divider"></div>
        <div class="stat"><b>{{ user.credit }}</b><span>信用分</span></div>
      </div>

      <!-- 操作按钮 -->
      <div class="profile-actions" v-if="!isSelf">
        <!-- 已拉黑状态 -->
        <template v-if="isBlocked">
          <span class="blocked-badge">已拉黑</span>
          <button class="btn btn-outline btn-sm" :disabled="actionLoading" @click="handleBlock">取消拉黑</button>
        </template>
        <!-- 正常状态 -->
        <template v-else>
          <button v-if="!isFollowed" class="btn btn-primary" :disabled="actionLoading" @click="toggleFollow">
            <UserPlus :size="15" />关注
          </button>
          <button v-else class="btn btn-outline" :disabled="actionLoading" @click="toggleFollow">
            <UserCheck :size="15" />已关注
          </button>
          <button v-if="!isFriend" class="btn btn-outline" :disabled="actionLoading" @click="sendFriendRequest">
            申请好友
          </button>
          <button v-else class="btn btn-primary" @click="goToChat">
            发消息
          </button>
          <button
            class="btn btn-outline btn-block"
            :disabled="actionLoading"
            @click="showBlockConfirm = true"
          >
            <UserMinus :size="15" />拉黑
          </button>
        </template>
      </div>

      <!-- 兴趣标签 -->
      <section v-if="user.interests?.length" class="profile-section">
        <h3>兴趣标签</h3>
        <div class="interest-tags">
          <span v-for="tag in user.interests" :key="tag" class="tag">{{ tag }}</span>
        </div>
      </section>

      <!-- 基本信息 -->
      <section v-if="user.gender || user.birthday" class="profile-section">
        <h3>基本信息</h3>
        <div class="info-grid">
          <div v-if="user.gender" class="info-item">
            <span class="label">性别</span><span>{{ user.gender }}</span>
          </div>
          <div v-if="user.birthday" class="info-item">
            <span class="label">生日</span><span>{{ user.birthday }}</span>
          </div>
        </div>
      </section>

      <!-- 商家信息 -->
      <section v-if="user.merchantName" class="profile-section">
        <h3>商家信息</h3>
        <div class="info-grid">
          <div class="info-item"><span class="label">商家名称</span><span>{{ user.merchantName }}</span></div>
          <div v-if="user.merchantFields?.length" class="info-item">
            <span class="label">经营领域</span><span>{{ user.merchantFields.join('、') }}</span>
          </div>
        </div>
      </section>
    </template>

    <!-- 取关确认弹窗 -->
    <div v-if="showUnfollowConfirm" class="modal-mask" @click.self="showUnfollowConfirm = false">
      <div class="confirm-modal">
        <h3>确认取消关注</h3>
        <p>取消关注后你们将不再是好友关系。</p>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="showUnfollowConfirm = false">取消</button>
          <button class="btn btn-dark" :disabled="actionLoading" @click="confirmUnfollow">确认取关</button>
        </div>
      </div>
    </div>

    <!-- 拉黑确认弹窗 -->
    <div v-if="showBlockConfirm" class="modal-mask" @click.self="showBlockConfirm = false">
      <div class="confirm-modal">
        <h3>确认拉黑</h3>
        <p>拉黑后你们将互相取消关注并解除好友关系，且对方无法再向你发送好友申请。</p>
        <div class="modal-actions">
          <button class="btn btn-outline" @click="showBlockConfirm = false">取消</button>
          <button class="btn btn-dark" :disabled="actionLoading" @click="handleBlock">确认拉黑</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.user-profile-page {
  max-width: 640px;
  margin: 0 auto;
  padding: 20px 16px 60px;
}
.back-btn {
  margin-bottom: 16px;
  padding: 6px 14px;
  border: 1px solid var(--color-border);
  border-radius: 8px;
  background: #fff;
  cursor: pointer;
  font-size: 13px;
  color: var(--color-ink-soft);
}
.back-btn:hover { background: var(--color-bg); }
.loading, .error-state { text-align: center; padding: 60px 0; color: var(--color-ink-soft); font-size: 15px; }
.error-state { color: var(--color-danger); }

.profile-card {
  border-radius: var(--radius-lg);
  overflow: hidden;
  background: #fff;
  border: 1px solid var(--color-border);
  position: relative;
}
.card-cover {
  height: 100px;
  background-size: cover;
  background-position: center;
  filter: blur(20px) brightness(1.1);
  transform: scale(1.1);
}
.card-body {
  display: flex;
  gap: 16px;
  padding: 0 20px 20px;
  margin-top: -48px;
  position: relative;
  z-index: 1;
}
.profile-avatar {
  width: 88px;
  height: 88px;
  border-radius: 50%;
  object-fit: cover;
  border: 4px solid #fff;
  box-shadow: 0 2px 8px rgba(0,0,0,.08);
  flex-shrink: 0;
}
.card-info { padding-top: 44px; }
.name-row {
  margin: 0 0 4px;
  font-size: 20px;
  display: flex;
  align-items: center;
  gap: 6px;
}
.verified-badge {
  display: inline-flex;
  align-items: center;
  padding: 2px;
  border-radius: 50%;
  background: var(--color-primary);
  color: #fff;
}
.role-city {
  margin: 0 0 6px;
  display: flex;
  align-items: center;
  gap: 8px;
  font-size: 13px;
  color: var(--color-ink-soft);
}
.role-tag {
  padding: 2px 8px;
  border-radius: 4px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-size: 11px;
}
.city {
  display: inline-flex;
  align-items: center;
  gap: 3px;
}
.profile-bio {
  margin: 0;
  font-size: 14px;
  line-height: 1.6;
  color: var(--color-ink);
}

.stats-row {
  display: flex;
  justify-content: center;
  gap: 0;
  margin: 20px 0;
  padding: 18px 0;
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}
.stat {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 0 24px;
}
.stat b { font-size: 20px; font-weight: 700; }
.stat span { font-size: 12px; color: var(--color-ink-soft); margin-top: 2px; }
.stat.divider {
  width: 1px;
  background: var(--color-border);
  padding: 0;
  align-self: stretch;
}

.profile-actions {
  display: flex;
  gap: 10px;
  margin: 20px 0;
  align-items: center;
  flex-wrap: wrap;
}
.profile-actions .btn {
  padding: 8px 18px;
  font-size: 14px;
  display: inline-flex;
  align-items: center;
  gap: 5px;
}
.btn-block { color: var(--color-danger); border-color: #e8c8c8; }
.btn-block:hover { background: #fff5f5; }
.blocked-badge {
  padding: 6px 14px;
  border-radius: 8px;
  background: #fff0f0;
  color: #c0392b;
  font-size: 13px;
  font-weight: 700;
}

.profile-section {
  margin-top: 20px;
  padding: 20px;
  background: #fff;
  border: 1px solid var(--color-border);
  border-radius: var(--radius-lg);
}
.profile-section h3 {
  margin: 0 0 12px;
  font-size: 15px;
  color: var(--color-ink);
}
.interest-tags { display: flex; flex-wrap: wrap; gap: 8px; }
.tag {
  padding: 6px 14px;
  border-radius: 20px;
  background: var(--color-bg);
  font-size: 13px;
  color: var(--color-ink);
}
.info-grid {
  display: grid;
  grid-template-columns: 1fr 1fr;
  gap: 12px;
}
.info-item { display: flex; gap: 8px; font-size: 14px; align-items: center; }
.label { color: var(--color-ink-soft); min-width: 48px; font-size: 13px; }

.modal-mask {
  position: fixed; z-index: 200; inset: 0;
  background: rgba(13,21,34,.55);
  backdrop-filter: blur(4px);
  display: grid; place-items: center; padding: 16px;
}
.confirm-modal {
  width: min(100%, 400px);
  padding: 28px;
  background: #fff;
  border-radius: var(--radius-lg);
  box-shadow: var(--shadow-float);
  text-align: center;
}
.confirm-modal h3 { margin: 0 0 10px; }
.confirm-modal p { color: var(--color-ink-soft); line-height: 1.6; font-size: 13px; margin-bottom: 18px; }
.modal-actions { display: flex; gap: 10px; justify-content: center; }
</style>
