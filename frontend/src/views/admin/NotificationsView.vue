<script setup lang="ts">
import { inject, onMounted, ref, watch } from 'vue'
import { Megaphone, Plus } from 'lucide-vue-next'
import { apiGet } from '@/lib/api'
import type { NotificationItem } from '@/types'
import type { Ref } from 'vue'

const publishedList = ref<NotificationItem[]>([])
const openPublish = inject<() => void>('openPublish', () => {})
const publishVersion = inject<Ref<number>>('publishVersion', ref(0))

async function loadPublished() {
  try {
    const rows = await apiGet<Record<string, unknown>[]>('/admin/notifications')
    publishedList.value = rows.map(row => ({
      id: String(row.id ?? ''),
      userId: String(row.user_id ?? row.userId ?? ''),
      type: String(row.type ?? ''),
      title: String(row.title ?? ''),
      content: typeof row.content === 'string' ? row.content : '',
      read: true,
      createdAt: typeof row.created_at === 'string' ? row.created_at : '',
    }))
  } catch {
    publishedList.value = []
  }
}

defineExpose({ loadPublished })

onMounted(loadPublished)
watch(publishVersion, loadPublished)
</script>

<template>
  <div>
    <div class="admin-page-title">
      <div>
        <h1>通知管理</h1>
        <p>查看已发布的通知并向用户推送新的系统通知。</p>
      </div>
      <button class="btn btn-dark btn-sm" @click="openPublish">
        <Plus :size="16" />发布通知
      </button>
    </div>

    <section class="admin-card">
      <div class="notify-table-head">
        <span>通知标题</span>
        <span>类型</span>
        <span>发布时间</span>
      </div>
      <div v-if="!publishedList.length" class="empty-notify">
        <Megaphone :size="32" style="color:var(--color-ink-soft);margin-bottom:10px" />
        <p>暂无已发布的通知</p>
        <button class="btn btn-outline btn-sm" @click="openPublish">发布第一条通知</button>
      </div>
      <div
        v-for="item in publishedList"
        :key="item.id"
        class="notify-row"
      >
        <div>
          <b>{{ item.title }}</b>
          <small>{{ item.content }}</small>
        </div>
        <span><i class="notify-type-tag">{{ item.type }}</i></span>
        <span class="muted">{{ item.createdAt }}</span>
      </div>
    </section>
  </div>
</template>

<style scoped>
.notify-table-head {
  display: grid;
  grid-template-columns: 1.8fr .8fr .6fr;
  gap: 14px;
  align-items: center;
  padding: 12px 16px;
  background: #fafafa;
  color: var(--color-ink-soft);
  font-size: 11px;
  font-weight: 800;
  border-radius: 8px 8px 0 0;
}
.notify-row {
  display: grid;
  grid-template-columns: 1.8fr .8fr .6fr;
  gap: 14px;
  align-items: center;
  padding: 14px 16px;
  border-top: 1px solid var(--color-line);
  font-size: 13px;
}
.notify-row > div:first-child {
  display: flex;
  flex-direction: column;
  gap: 4px;
}
.notify-row small {
  color: var(--color-ink-soft);
  font-size: 11px;
  white-space: nowrap;
  overflow: hidden;
  text-overflow: ellipsis;
  max-width: 360px;
}
.notify-type-tag {
  display: inline-block;
  padding: 4px 8px;
  border-radius: 5px;
  background: var(--color-primary-soft);
  color: var(--color-primary);
  font-style: normal;
  font-size: 11px;
  font-weight: 700;
}
.empty-notify {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 48px 16px;
  color: var(--color-ink-soft);
  font-size: 13px;
}
</style>
