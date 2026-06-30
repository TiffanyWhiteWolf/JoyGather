<script setup lang="ts">
import { MessageCircle, Search, UserRoundCheck, UserRoundMinus, ShieldOff, ShieldCheck, X, MoreHorizontal, Edit3, ShieldBan, UserRoundPlus, UserCheck, UserX } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRouter } from 'vue-router'
import { apiGet, apiPost, apiPut, apiDelete } from '@/lib/api'
import { useAppStore } from '@/stores/app'
import type { Friend, FriendRequest, User } from '@/types'

const app = useAppStore()
const router = useRouter()
const friends = ref<Friend[]>([])
const requests = ref<FriendRequest[]>([])
const error = ref('')
const loading = ref(true)

// Search
const searchQuery = ref('')
const searchResults = ref<User[]>([])
const searching = ref(false)
const actionLoading = ref<Record<string, boolean>>({})

// Edit
const editingFriend = ref<string | null>(null)
const editRemark = ref('')
const editGroup = ref('')
const actionMenu = ref<string | null>(null)

// Friend request dialog
const requestDialog = reactive<{ show: boolean; targetUserId: string; targetNickname: string; message: string; loading: boolean }>({
  show: false, targetUserId: '', targetNickname: '', message: '', loading: false,
})

// Common emojis for quick insert
const commonEmojis = ['😊', '👋', '💪', '🔥', '🎉', '🤝', '👍', '❤️', '😎', '🌟', '🙌', '📸', '🏃', '☕', '🎮']

function insertEmoji(emoji: string) {
  requestDialog.message += emoji
}

// Confirm dialog
const confirmDialog = reactive<{ show: boolean; title: string; message: string; confirmText: string; danger?: boolean; onConfirm: (() => void) | null }>({
  show: false, title: '', message: '', confirmText: '确认', danger: false, onConfirm: null,
})

function showConfirm(title: string, message: string, onConfirm: () => void, confirmText = '确认', danger = true) {
  confirmDialog.show = true
  confirmDialog.title = title
  confirmDialog.message = message
  confirmDialog.confirmText = confirmText
  confirmDialog.danger = danger
  confirmDialog.onConfirm = onConfirm
}

function closeConfirm() {
  confirmDialog.show = false
  confirmDialog.onConfirm = null
}

function doConfirm() {
  if (confirmDialog.onConfirm) confirmDialog.onConfirm()
  closeConfirm()
}

async function loadFriends() {
  try {
    friends.value = await apiGet<Friend[]>('/friends')
    app.friendIds = friends.value.map(f => f.userId)
  } catch {
    friends.value = []
  }
}

async function loadRequests() {
  try {
    requests.value = await apiGet<FriendRequest[]>('/friends/requests')
  } catch {
    requests.value = []
  }
}

async function loadAll() {
  loading.value = true
  error.value = ''
  try {
    await Promise.all([loadFriends(), loadRequests(), app.refreshUserState()])
  } catch (e) {
    error.value = e instanceof Error ? e.message : '加载失败'
  } finally {
    loading.value = false
  }
}

async function searchUsers() {
  const q = searchQuery.value.trim()
  if (!q || q.length < 2) { searchResults.value = []; return }
  searching.value = true
  try {
    searchResults.value = await apiGet<User[]>(`/users/search?q=${encodeURIComponent(q)}`)
  } catch {
    searchResults.value = []
  } finally {
    searching.value = false
  }
}

async function followUser(userId: string) {
  actionLoading.value[userId] = true
  try {
    await apiPost(`/follows/${userId}`, {})
    app.addFollowedId(userId)
    await loadFriends()
    app.showToast('已关注')
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '操作失败')
  } finally {
    actionLoading.value[userId] = false
  }
}

async function unfollowUser(userId: string, isFriend: boolean) {
  const title = isFriend ? '取消关注并解除好友？' : '取消关注？'
  const msg = isFriend ? '取消关注将同步解除好友关系，对方将从好友列表中移除。' : '取消关注后对方的动态将不再出现在你的关注列表。'
  showConfirm(title, msg, async () => {
    actionLoading.value[userId] = true
    try {
      await apiDelete(`/follows/${userId}`)
      app.removeFollowedId(userId)
      friends.value = friends.value.filter(f => f.userId !== userId)
      app.showToast(isFriend ? '已取消关注，好友关系已解除' : '已取消关注')
    } catch (e) {
      app.showToast(e instanceof Error ? e.message : '操作失败')
    } finally {
      actionLoading.value[userId] = false
      actionMenu.value = null
    }
  }, '取消关注')
}

async function blockUser(userId: string, isFriend: boolean) {
  const msg = isFriend ? '拉黑后对方将从好友列表移除，且无法再关注你。' : '拉黑后对方无法关注你，你也无法关注对方。'
  showConfirm('拉黑该用户？', msg, async () => {
    try {
      await apiPost(`/blocks/${userId}`, { reason: '' })
      app.removeFollowedId(userId)
      app.addBlockedId(userId)
      friends.value = friends.value.filter(f => f.userId !== userId)
      app.showToast('已拉黑')
    } catch (e) {
      app.showToast(e instanceof Error ? e.message : '操作失败')
    }
    actionMenu.value = null
  }, '拉黑')
}

async function unblockUser(userId: string) {
  actionLoading.value[userId] = true
  try {
    await apiDelete(`/blocks/${userId}`)
    app.removeBlockedId(userId)
    app.showToast('已取消拉黑')
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '操作失败')
  } finally {
    actionLoading.value[userId] = false
  }
}

function openRequestDialog(user: User) {
  requestDialog.show = true
  requestDialog.targetUserId = user.id
  requestDialog.targetNickname = user.nickname
  requestDialog.message = ''
  requestDialog.loading = false
}

function closeRequestDialog() {
  requestDialog.show = false
  requestDialog.message = ''
}

async function sendFriendRequest() {
  if (!requestDialog.message.trim()) {
    app.showToast('请说点什么吧')
    return
  }
  if (requestDialog.message.length > 100) {
    app.showToast('留言不能超过100字')
    return
  }
  requestDialog.loading = true
  try {
    await apiPost('/friends/requests', {
      userId: requestDialog.targetUserId,
      source: 'PROFILE',
      message: requestDialog.message.trim(),
    })
    app.addFollowedId(requestDialog.targetUserId)
    await loadFriends()
    app.showToast('已发送好友申请并关注对方')
    closeRequestDialog()
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '发送失败')
  } finally {
    requestDialog.loading = false
  }
}

async function approveRequest(requestId: string) {
  try {
    await apiPost(`/friends/requests/${requestId}/approve`, {})
    await loadFriends()
    await loadRequests()
    await app.refreshUserState()
    app.showToast('已通过申请，成为好友')
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '操作失败')
  }
}

async function rejectRequest(requestId: string) {
  try {
    await apiPost(`/friends/requests/${requestId}/reject`, {})
    await loadRequests()
    app.showToast('已拒绝申请')
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '操作失败')
  }
}

function startEdit(friend: Friend) {
  editingFriend.value = friend.userId
  editRemark.value = friend.remark || ''
  editGroup.value = friend.groupName || ''
  actionMenu.value = null
}

function cancelEdit() {
  editingFriend.value = null
  editRemark.value = ''
  editGroup.value = ''
}

async function saveEdit(friendId: string) {
  try {
    await apiPut(`/friends/${friendId}`, { remark: editRemark.value, groupName: editGroup.value })
    const f = friends.value.find(ff => ff.userId === friendId)
    if (f) { f.remark = editRemark.value; f.groupName = editGroup.value }
    app.showToast('备注已更新')
    cancelEdit()
  } catch (e) {
    app.showToast(e instanceof Error ? e.message : '更新失败')
  }
}

function goChat(friend: Friend) {
  router.push('/messages')
  app.showToast('已打开消息页')
}

// user state helpers
function isFriend(uid: string) { return app.friendIds.includes(uid) }
function isFollowing(uid: string) { return app.followedIds.includes(uid) }
function isSelf(uid: string) {
  try { return JSON.parse(localStorage.getItem('quju:session') || '{}').id === uid } catch { return false }
}
function userState(uid: string): 'friend' | 'following' | 'none' {
  if (isFriend(uid)) return 'friend'
  if (isFollowing(uid)) return 'following'
  return 'none'
}
function isBlocked(uid: string) { return app.blockedIds.includes(uid) }

// pending requests count
const pendingRequests = computed(() => requests.value.filter(r => r.status === '待处理').length)

function closeActionMenu() { actionMenu.value = null }

onMounted(loadAll)
</script>

<template>
  <div class="container friends-page" @click="closeActionMenu">
    <div class="friends-hero">
      <div>
        <span class="eyebrow">SOCIAL CIRCLE</span>
        <h1>关注同频的人，<br />互相关注成为好友</h1>
        <p>关注感兴趣的用户，双方互相关注后自动成为好友，开始聊天。</p>
      </div>
      <div class="friend-stats">
        <div><b>{{ friends.length }}</b><span>好友</span></div>
        <div><b>{{ app.followedIds.length }}</b><span>关注中</span></div>
        <div v-if="pendingRequests"><b>{{ pendingRequests }}</b><span>待处理申请</span></div>
      </div>
    </div>

    <!-- Search -->
    <div class="search-section">
      <div class="search-bar">
        <Search :size="18" />
        <input v-model="searchQuery" placeholder="搜索用户昵称，关注或申请好友" @input="searchUsers" />
        <X v-if="searchQuery" :size="16" class="clear-btn" @click.stop="searchQuery = ''; searchResults = []" />
      </div>
      <div v-if="searchResults.length" class="search-results">
        <div v-for="user in searchResults" :key="user.id" class="search-result-item">
          <img :src="user.avatar" :alt="user.nickname" />
          <div class="result-info">
            <b>{{ user.nickname }}</b>
            <span>{{ user.city }} · {{ user.interests?.slice(0, 2).join('、') || '暂无标签' }}</span>
          </div>
          <template v-if="isSelf(user.id)">
            <span class="state-tag">我自己</span>
          </template>
          <template v-else>
            <template v-if="isBlocked(user.id)">
              <span class="state-tag blocked">已拉黑</span>
              <button class="btn btn-outline btn-sm unblock-btn" :disabled="actionLoading[user.id]" @click.stop="unblockUser(user.id)"><ShieldCheck :size="14" />取消拉黑</button>
            </template>
            <template v-else>
              <template v-if="userState(user.id) === 'friend'">
                <button class="btn btn-mint btn-sm" :disabled="actionLoading[user.id]" disabled>已是好友</button>
                <button class="btn btn-outline btn-sm danger-btn" :disabled="actionLoading[user.id]" @click.stop="blockUser(user.id, true)"><ShieldBan :size="14" /></button>
              </template>
              <template v-else-if="userState(user.id) === 'following'">
                <button class="btn btn-outline btn-sm" :disabled="actionLoading[user.id]" @click.stop="unfollowUser(user.id, false)">已关注</button>
                <button class="btn btn-outline btn-sm" :disabled="actionLoading[user.id]" @click.stop="openRequestDialog(user)">申请好友</button>
                <button class="btn btn-outline btn-sm danger-btn" :disabled="actionLoading[user.id]" @click.stop="blockUser(user.id, false)"><ShieldBan :size="14" /></button>
              </template>
              <template v-else>
                <button class="btn btn-primary btn-sm" :disabled="actionLoading[user.id]" @click.stop="followUser(user.id)">关注</button>
                <button class="btn btn-outline btn-sm" :disabled="actionLoading[user.id]" @click.stop="openRequestDialog(user)">申请好友</button>
                <button class="btn btn-outline btn-sm danger-btn" :disabled="actionLoading[user.id]" @click.stop="blockUser(user.id, false)"><ShieldBan :size="14" /></button>
              </template>
            </template>
          </template>
        </div>
      </div>
      <p v-if="searchQuery.length >= 2 && !searching && !searchResults.length" class="no-results">未找到匹配的用户</p>
    </div>

    <!-- Two-column layout -->
    <div class="content-columns">
      <!-- Left: Friend List -->
      <div class="column-main">
        <h2 class="section-title">我的好友</h2>
        <p v-if="error" class="form-error" role="alert">{{ error }}</p>
        <div v-if="loading" class="loading">加载中...</div>
        <div v-else-if="!friends.length" class="empty-state">
          <UserRoundCheck :size="48" />
          <h3>还没有好友</h3>
          <p>搜索并关注其他用户，对方也关注你后就会成为好友。</p>
        </div>
        <div v-else class="friend-list">
          <div v-for="friend in friends" :key="friend.userId" class="friend-card">
            <img :src="friend.avatar" :alt="friend.nickname" class="friend-avatar" @click="goChat(friend)" />
            <div class="friend-info" @click="goChat(friend)">
              <div class="friend-name-row">
                <b>{{ friend.nickname }}</b>
                <span v-if="friend.remark" class="remark-tag">{{ friend.remark }}</span>
              </div>
              <div class="friend-meta">
                <span v-if="friend.city">{{ friend.city }}</span>
                <span v-if="friend.groupName" class="group-badge">{{ friend.groupName }}</span>
              </div>
              <div v-if="friend.interests" class="friend-interests">
                <span v-for="tag in friend.interests.split(',').slice(0,4)" :key="tag" class="interest-tag">{{ tag.trim() }}</span>
              </div>
              <p v-if="friend.bio" class="friend-bio">{{ friend.bio }}</p>
            </div>
            <div class="friend-actions">
              <button class="btn btn-outline btn-sm" @click.stop="goChat(friend)"><MessageCircle :size="14" />发消息</button>
              <div class="action-wrap">
                <button class="icon-btn" @click.stop="actionMenu === friend.userId ? actionMenu = null : actionMenu = friend.userId"><MoreHorizontal :size="16" /></button>
                <div v-if="actionMenu === friend.userId" class="action-dropdown" @click.stop>
                  <button @click="startEdit(friend)"><Edit3 :size="14" />修改备注</button>
                  <button class="danger" @click="unfollowUser(friend.userId, true)"><UserRoundMinus :size="14" />取消关注</button>
                  <button class="danger" @click="blockUser(friend.userId, true)"><ShieldOff :size="14" />拉黑</button>
                </div>
              </div>
            </div>
          </div>
        </div>
      </div>

      <!-- Right: Incoming Requests -->
      <div class="column-side">
        <h2 class="section-title">收到的好友申请</h2>
        <div v-if="!requests.length" class="empty-state-side">
          <UserCheck :size="32" />
          <p>暂无好友申请</p>
        </div>
        <div v-else class="request-list">
          <div v-for="req in requests" :key="req.id" class="request-card">
            <img :src="req.requesterAvatar" :alt="req.requesterNickname" />
            <div class="request-info">
              <b>{{ req.requesterNickname }}</b>
              <p v-if="req.message" class="request-msg">"{{ req.message }}"</p>
              <span v-if="req.createdAt" class="request-time">{{ req.createdAt }}</span>
            </div>
            <div v-if="req.status === '待处理'" class="request-actions">
              <button class="btn btn-primary btn-sm" @click="approveRequest(req.id)"><UserCheck :size="14" />通过</button>
              <button class="btn btn-outline btn-sm" @click="rejectRequest(req.id)"><UserX :size="14" />拒绝</button>
            </div>
            <span v-else class="state-tag">{{ req.status }}</span>
          </div>
        </div>
      </div>
    </div>

    <!-- Edit Modal -->
    <div v-if="editingFriend" class="modal-mask" @click.self="cancelEdit">
      <div class="edit-modal">
        <h3>修改备注</h3>
        <label>备注名<input v-model="editRemark" placeholder="给好友起个备注" maxlength="20" /></label>
        <label>分组<input v-model="editGroup" placeholder="例如：摄影组、徒步搭子" maxlength="20" /></label>
        <div class="edit-actions">
          <button class="btn btn-outline" @click="cancelEdit">取消</button>
          <button class="btn btn-primary" @click="saveEdit(editingFriend!)">保存</button>
        </div>
      </div>
    </div>

    <!-- Friend Request Dialog -->
    <div v-if="requestDialog.show" class="modal-mask" @click.self="closeRequestDialog">
      <div class="request-modal">
        <h3>申请添加 {{ requestDialog.targetNickname }} 为好友</h3>
        <p class="request-hint">
          发送申请后将自动关注对方。对方确认后你们会成为互相关注的好友。
        </p>
        <div class="message-area">
          <textarea
            v-model="requestDialog.message"
            placeholder="说点什么吧，让对方更了解你..."
            maxlength="100"
            rows="3"
            ref="msgInput"
          ></textarea>
          <span class="char-count">{{ requestDialog.message.length }}/100</span>
        </div>
        <div class="emoji-row">
          <button v-for="e in commonEmojis" :key="e" class="emoji-btn" @click="insertEmoji(e)" :title="e">{{ e }}</button>
        </div>
        <div class="edit-actions">
          <button class="btn btn-outline" @click="closeRequestDialog" :disabled="requestDialog.loading">取消</button>
          <button class="btn btn-primary" @click="sendFriendRequest" :disabled="requestDialog.loading || !requestDialog.message.trim()">
            {{ requestDialog.loading ? '发送中...' : '发送申请' }}
          </button>
        </div>
      </div>
    </div>

    <!-- Confirm Modal -->
    <div v-if="confirmDialog.show" class="modal-mask" @click.self="closeConfirm">
      <div class="confirm-modal">
        <h3>{{ confirmDialog.title }}</h3>
        <p>{{ confirmDialog.message }}</p>
        <div class="edit-actions">
          <button class="btn btn-outline" @click="closeConfirm">取消</button>
          <button :class="['btn', confirmDialog.danger ? 'btn-dark' : 'btn-primary']" @click="doConfirm">{{ confirmDialog.confirmText }}</button>
        </div>
      </div>
    </div>
  </div>
</template>

<style scoped>
.friends-page { padding: 48px 0 80px; }
.friends-hero {
  display: flex; justify-content: space-between; align-items: flex-end;
  padding: 36px 42px; background: linear-gradient(135deg, #f0f4ff, #fdf2f8);
  border-radius: var(--radius-xl); margin-bottom: 32px;
}
.friends-hero h1 { margin: 5px 0 10px; font-size: 34px; line-height: 1.25; letter-spacing: -.04em; }
.friends-hero p { color: var(--color-ink-soft); max-width: 400px; }
.friend-stats { display: flex; gap: 28px; }
.friend-stats div { text-align: center; }
.friend-stats b { display: block; font-size: 28px; font-weight: 800; }
.friend-stats span { font-size: 12px; color: var(--color-ink-soft); }

.section-title { font-size: 20px; margin: 0 0 16px; }

/* Two-column layout */
.content-columns {
  display: grid; grid-template-columns: 1fr 360px; gap: 24px; align-items: start;
}
.column-main { min-width: 0; }
.column-side {
  position: sticky; top: 24px;
}
.empty-state-side {
  text-align: center; padding: 32px 16px; color: var(--color-ink-soft);
  background: #fff; border: 1px solid var(--color-line); border-radius: var(--radius-lg);
}
.empty-state-side svg { margin-bottom: 8px; opacity: .4; }
.empty-state-side p { font-size: 13px; }

/* Search */
.search-section { margin-bottom: 0; }
.search-bar {
  display: flex; align-items: center; gap: 10px; height: 48px; padding: 0 14px;
  background: #fff; border: 1px solid var(--color-line); border-radius: var(--radius-pill);
  box-shadow: var(--shadow-soft);
}
.search-bar input { flex: 1; border: 0; outline: 0; font-size: 14px; }
.clear-btn { cursor: pointer; color: var(--color-ink-soft); }
.search-results {
  margin-top: 8px; background: #fff; border: 1px solid var(--color-line);
  border-radius: var(--radius-lg); overflow: hidden; box-shadow: var(--shadow-card);
}
.search-result-item {
  display: flex; align-items: center; gap: 12px; padding: 12px 16px;
}
.search-result-item + .search-result-item { border-top: 1px solid var(--color-line); }
.search-result-item img { width: 40px; height: 40px; border-radius: 50%; object-fit: cover; }
.result-info { flex: 1; }
.result-info b { display: block; font-size: 14px; }
.result-info span { font-size: 12px; color: var(--color-ink-soft); }
.state-tag { font-size: 12px; font-weight: 700; padding: 6px 10px; border-radius: var(--radius-pill); }
.state-tag.friend { background: var(--color-mint-soft); color: var(--color-mint); }
.state-tag.blocked { background: #fff0f0; color: #c0392b; border: 1px solid #f5c2c7; }
.no-results { margin-top: 8px; color: var(--color-ink-soft); font-size: 13px; }

.loading { text-align: center; padding: 40px; color: var(--color-ink-soft); }
.empty-state { text-align: center; padding: 60px 20px; color: var(--color-ink-soft); }
.empty-state svg { margin-bottom: 12px; color: var(--color-line); }
.empty-state h3 { margin-bottom: 6px; font-size: 18px; }

/* Friend List */
.friend-list { display: flex; flex-direction: column; gap: 8px; }
.friend-card {
  display: flex; align-items: flex-start; gap: 14px; padding: 16px 18px;
  background: #fff; border: 1px solid var(--color-line); border-radius: var(--radius-lg);
}
.friend-avatar { width: 48px; height: 48px; border-radius: 50%; object-fit: cover; cursor: pointer; flex-shrink: 0; }
.friend-info { flex: 1; cursor: pointer; min-width: 0; }
.friend-name-row { display: flex; align-items: center; gap: 8px; margin-bottom: 4px; }
.friend-name-row b { font-size: 15px; }
.remark-tag { padding: 1px 7px; background: var(--color-primary-soft); color: var(--color-primary); border-radius: 4px; font-size: 11px; font-weight: 600; }
.friend-meta { display: flex; align-items: center; gap: 6px; font-size: 12px; color: var(--color-ink-soft); margin-bottom: 4px; }
.group-badge { padding: 0 6px; background: var(--color-bg); border-radius: 4px; font-size: 11px; }
.friend-interests { display: flex; gap: 4px; flex-wrap: wrap; margin-bottom: 4px; }
.interest-tag {
  padding: 1px 8px; font-size: 11px; border-radius: var(--radius-pill);
  background: var(--color-mint-soft); color: var(--color-mint);
}
.friend-bio {
  margin: 0; font-size: 12px; color: var(--color-ink-soft);
  overflow: hidden; text-overflow: ellipsis; white-space: nowrap; max-width: 320px;
}
.friend-actions { display: flex; align-items: center; gap: 8px; flex-shrink: 0; }
.icon-btn {
  width: 32px; height: 32px; display: flex; align-items: center; justify-content: center;
  border: 1px solid var(--color-line); border-radius: 8px; background: #fff; cursor: pointer;
}
.action-wrap { position: relative; }
.action-dropdown {
  position: absolute; right: 0; top: calc(100% + 4px); z-index: 20;
  min-width: 140px; padding: 6px; background: #fff; border: 1px solid var(--color-line);
  border-radius: 10px; box-shadow: var(--shadow-card);
}
.action-dropdown button {
  display: flex; align-items: center; gap: 8px; width: 100%; padding: 8px 10px;
  border: 0; border-radius: 6px; background: none; font-size: 13px; cursor: pointer;
}
.action-dropdown button:hover { background: var(--color-bg); }
.action-dropdown button.danger { color: var(--color-danger); }

/* Incoming Requests */
.request-list { display: flex; flex-direction: column; gap: 8px; }
.request-card {
  display: flex; align-items: flex-start; gap: 10px; padding: 14px;
  background: #fff; border: 1px solid var(--color-line); border-radius: var(--radius-lg);
}
.request-card img { width: 36px; height: 36px; border-radius: 50%; object-fit: cover; flex-shrink: 0; }
.request-info { flex: 1; min-width: 0; }
.request-info b { font-size: 13px; display: block; }
.request-msg { margin: 4px 0; color: var(--color-ink-soft); font-size: 12px; font-style: italic; line-height: 1.4; word-break: break-word; }
.request-time { font-size: 11px; color: var(--color-ink-soft); }
.request-actions { display: flex; gap: 4px; flex-shrink: 0; margin-top: 2px; }

/* Friend Request Dialog */
.request-modal {
  width: 440px; max-width: 90vw; padding: 28px; background: #fff; border-radius: var(--radius-xl);
}
.request-modal h3 { margin-bottom: 8px; font-size: 18px; }
.request-hint { color: var(--color-ink-soft); font-size: 13px; margin-bottom: 16px; line-height: 1.5; }
.message-area { position: relative; margin-bottom: 12px; }
.message-area textarea {
  width: 100%; padding: 12px; border: 1px solid var(--color-line); border-radius: 10px;
  font-size: 14px; font-family: inherit; outline: 0; resize: vertical; line-height: 1.6;
}
.message-area textarea:focus { border-color: var(--color-primary); }
.char-count { position: absolute; right: 10px; bottom: 8px; font-size: 11px; color: var(--color-ink-soft); }
.emoji-row { display: flex; gap: 6px; flex-wrap: wrap; margin-bottom: 16px; }
.emoji-btn {
  width: 34px; height: 34px; display: flex; align-items: center; justify-content: center;
  border: 1px solid var(--color-line); border-radius: 8px; background: #fff;
  font-size: 18px; cursor: pointer; transition: background .15s;
}
.emoji-btn:hover { background: var(--color-bg); }

/* Modal */
.modal-mask {
  position: fixed; inset: 0; z-index: 100; background: rgba(0,0,0,.3);
  display: flex; align-items: center; justify-content: center;
}
.edit-modal {
  width: 380px; max-width: 90vw; padding: 28px; background: #fff; border-radius: var(--radius-xl);
}
.edit-modal h3 { margin-bottom: 20px; font-size: 18px; }
.edit-modal label { display: block; margin-bottom: 14px; font-size: 13px; font-weight: 600; color: var(--color-ink-soft); }
.edit-modal input {
  display: block; width: 100%; margin-top: 4px; padding: 10px 12px;
  border: 1px solid var(--color-line); border-radius: 8px; font-size: 14px; outline: 0;
}
.edit-actions { display: flex; gap: 8px; justify-content: flex-end; margin-top: 8px; }
.confirm-modal {
  width: 380px; max-width: 90vw; padding: 28px; background: #fff; border-radius: var(--radius-xl);
}
.confirm-modal h3 { margin-bottom: 12px; font-size: 18px; }
.confirm-modal p { color: var(--color-ink-soft); font-size: 14px; line-height: 1.6; }
.danger-btn { color: var(--color-danger); border-color: var(--color-danger); }
.danger-btn:hover { background: #fff5f5; }
.unblock-btn { color: #c0392b; border-color: #e8c8c8; background: #fefafa; }
.unblock-btn:hover { background: #fff0f0; border-color: #c0392b; }
.btn-mint {
  background: var(--color-mint-soft); color: var(--color-mint);
  border: 1px solid var(--color-mint); cursor: default;
}
.form-error { padding: 9px; border-radius: 8px; background: #ffeaed; color: var(--color-danger); font-size: 10px; }

@media (max-width: 900px) {
  .content-columns { grid-template-columns: 1fr; }
  .column-side { position: static; }
}
</style>
