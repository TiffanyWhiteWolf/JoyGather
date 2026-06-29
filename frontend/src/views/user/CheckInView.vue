<script setup lang="ts">
import { Camera, CheckCircle2, MapPin, QrCode, ShieldCheck } from 'lucide-vue-next'
import { onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { apiGet, apiPost } from '@/lib/api'
import type { User } from '@/types'
const scanned = ref(false)
const route = useRoute()
const code = ref(String(route.query.code || ''))
const user = ref<User | null>(null)
const message = ref('')
const error = ref('')
async function scan() {
  error.value = ''
  try {
    const result = await apiPost<{ activityId: string; status: string }>('/checkins/scan', { code: code.value })
    message.value = `活动 ${result.activityId} · ${result.status}`
    scanned.value = true
  } catch (err) {
    error.value = err instanceof Error ? err.message : '签到失败'
  }
}
onMounted(async () => { try { user.value = await apiGet<User>('/auth/me') } catch { /* show scan error after click */ } })
</script>
<template><div class="check-page"><div class="container"><div class="check-head"><span class="eyebrow">ON-SITE CHECK IN</span><h1>扫码签到</h1><p>到达活动现场后，扫描发起人出示的二维码。</p></div><div class="check-card"><div :class="['scanner',{success:scanned}]"><div class="scan-frame"><QrCode v-if="!scanned" :size="100" /><CheckCircle2 v-else :size="90" /><i></i><span></span></div><div class="scan-line" v-if="!scanned"></div></div><div class="check-info"><template v-if="!scanned"><span class="status-dot">等待扫描</span><h2>输入或粘贴签到码</h2><p><MapPin :size="16" />位置校验由活动发起人开启</p><input v-model.trim="code" class="input" placeholder="签到二维码中的 code" /><p v-if="error" class="auth-error">{{ error }}</p><button class="btn btn-primary" @click="scan"><Camera :size="18" />确认签到</button><div class="privacy"><ShieldCheck :size="18" /><span>位置仅用于本次现场签到验证，不会持续记录。</span></div></template><template v-else><span class="status-dot done">签到成功</span><h2>嗨，{{ user?.nickname || '伙伴' }}，现场见！</h2><p>{{ message }}</p><RouterLink to="/profile" class="btn btn-dark">返回个人页</RouterLink></template></div></div></div></div></template>
<style scoped>
.check-page{min-height:calc(100vh - 72px);padding:55px 0;background:linear-gradient(150deg,#edf9f6,#f7f5f1)}.check-head{text-align:center}.check-head h1{margin:0 0 8px;font-size:38px}.check-head p{color:var(--color-ink-soft)}.check-card{width:min(100%,760px);margin:32px auto;overflow:hidden;border-radius:var(--radius-xl);background:#fff;box-shadow:var(--shadow-card);display:grid;grid-template-columns:1fr 1fr}.scanner{position:relative;min-height:400px;background:#172238;display:grid;place-items:center;color:#fff}.scan-frame{position:relative;width:220px;height:220px;border:2px solid rgba(255,255,255,.18);border-radius:22px;display:grid;place-items:center}.scan-frame:before,.scan-frame:after,.scan-frame i:before,.scan-frame i:after{content:'';position:absolute;width:34px;height:34px;border-color:var(--color-primary);border-style:solid}.scan-frame:before{left:-3px;top:-3px;border-width:4px 0 0 4px;border-radius:12px 0 0}.scan-frame:after{right:-3px;top:-3px;border-width:4px 4px 0 0;border-radius:0 12px 0 0}.scan-frame i:before{left:-3px;bottom:-3px;border-width:0 0 4px 4px;border-radius:0 0 0 12px}.scan-frame i:after{right:-3px;bottom:-3px;border-width:0 4px 4px 0;border-radius:0 0 12px}.scan-line{position:absolute;width:190px;height:2px;background:var(--color-primary);box-shadow:0 0 12px var(--color-primary);animation:scan 2s ease-in-out infinite}.scanner.success{background:var(--color-mint)}@keyframes scan{0%,100%{transform:translateY(-85px)}50%{transform:translateY(85px)}}.check-info{padding:35px;display:flex;align-items:flex-start;justify-content:center;flex-direction:column}.status-dot{padding:6px 10px;border-radius:var(--radius-pill);background:var(--color-primary-soft);color:var(--color-primary);font-size:10px;font-weight:800}.status-dot.done{background:var(--color-mint-soft);color:var(--color-mint)}.check-info h2{margin:18px 0 10px}.check-info p{display:flex;align-items:center;gap:5px;color:var(--color-ink-soft);font-size:12px;line-height:1.6}.check-info>.btn{margin-top:18px}.privacy{display:flex;gap:7px;margin-top:23px;padding-top:18px;border-top:1px solid var(--color-line);color:var(--color-mint);font-size:9px}.privacy span{color:var(--color-ink-soft)}
@media(max-width:650px){.check-card{grid-template-columns:1fr}.scanner{min-height:330px}.check-info{min-height:300px}.check-head h1{font-size:30px}}
</style>
