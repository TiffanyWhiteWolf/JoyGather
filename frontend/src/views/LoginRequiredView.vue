<script setup lang="ts">
import { ArrowLeft, LogIn } from 'lucide-vue-next'
import { useRoute, useRouter } from 'vue-router'

const router = useRouter()
const route = useRoute()
const redirect = String(route.query.redirect || '/')
</script>

<template>
  <div class="gate-page">
    <div class="gate-card">
      <div class="gate-icon">
        <LogIn :size="32" />
      </div>
      <h2>需要登录才能继续</h2>
      <p>此功能需要登录后才能使用，请选择：</p>
      <div class="gate-actions">
        <RouterLink to="/" class="btn btn-outline"><ArrowLeft :size="17" />返回首页</RouterLink>
        <RouterLink :to="`/auth?redirect=${encodeURIComponent(redirect)}`" class="btn btn-primary">前往登录 <ArrowLeft :size="17" style="transform:rotate(180deg)" /></RouterLink>
      </div>
    </div>
  </div>
</template>

<style scoped>
.gate-page{min-height:100vh;display:grid;place-items:center;padding:24px;background:linear-gradient(135deg,#f7f5f1 0%,#fff7f2 100%)}
.gate-card{width:min(100%,400px);padding:42px 32px;background:#fff;border:1px solid var(--color-line);border-radius:24px;box-shadow:var(--shadow-card);text-align:center}
.gate-icon{width:72px;height:72px;margin:0 auto 22px;border-radius:50%;background:var(--color-primary-soft);color:var(--color-primary);display:grid;place-items:center}
.gate-card h2{margin:0 0 10px;font-size:22px}
.gate-card p{margin:0 0 28px;color:var(--color-ink-soft);font-size:14px;line-height:1.7}
.gate-actions{display:flex;flex-direction:column;gap:10px}
.gate-actions .btn{justify-content:center;padding:14px;border-radius:12px;font-size:14px;font-weight:700}
</style>
