<script setup lang="ts">
import { Check, Copy, LoaderCircle, RotateCcw, Sparkles, WandSparkles } from 'lucide-vue-next'
import { ref } from 'vue'
import { useRouter } from 'vue-router'
import { useAppStore } from '@/stores/app'
import { apiPost } from '@/lib/api'

const router = useRouter()
const app = useAppStore()
const AI_PLAN_STORAGE_KEY = 'quju:ai-plan'
const theme = ref('在杭州老街组织一场适合新朋友参加的夜间摄影漫步')
const people = ref('12-20 人')
const style = ref('轻松破冰')
const loading = ref(false)
const generated = ref(false)
const error = ref('')
const plan = ref({
  title: '',
  introduction: '',
  tags: [] as string[],
  schedule: [] as string[],
  safetyNote: '',
  aiGenerated: false,
  notice: '',
})

async function generate() {
  loading.value = true
  error.value = ''
  try {
    plan.value = await apiPost<typeof plan.value>('/ai/plans', { theme: theme.value, people: people.value, style: style.value })
    generated.value = true
  } catch (err) {
    error.value = err instanceof Error ? err.message : 'AI 生成失败，可稍后重试或手动创建'
  } finally {
    loading.value = false
  }
}

async function copyPlan() {
  await navigator.clipboard.writeText(`${plan.value.title}\n${plan.value.introduction}`)
  app.showToast('活动方案已复制')
}

function adoptPlan() {
  sessionStorage.setItem(AI_PLAN_STORAGE_KEY, JSON.stringify({
    title: plan.value.title,
    introduction: plan.value.introduction,
    tags: plan.value.tags,
    schedule: plan.value.schedule,
    safetyNote: plan.value.safetyNote,
    aiGenerated: plan.value.aiGenerated,
    notice: plan.value.notice,
  }))
  router.push({ path: '/create', query: { ai: '1' } })
}
</script>

<template>
  <div class="planner-page">
    <div class="container planner-wrap">
      <div class="planner-head">
        <span><Sparkles :size="17" />QUJU AI LAB</span>
        <h1>一句话，策划一场<br />让人想参加的活动</h1>
        <p>AI 会结合主题、城市与参与偏好，生成可继续编辑的完整方案。</p>
      </div>
      <div class="planner-grid">
        <section class="prompt-panel">
          <div class="step"><i>1</i><div><b>告诉我你的想法</b><small>不需要完整，模糊的念头也可以</small></div></div>
          <textarea v-model="theme" placeholder="例如：想组织一次日落时分的城市散步"></textarea>
          <div class="quick-prompts">
            <button @click="theme='周末在公园组织一场新手友好的飞盘局'">新手飞盘</button>
            <button @click="theme='带大家去九溪进行轻徒步和茶园野餐'">茶山徒步</button>
            <button @click="theme='举办一场不尴尬的桌游破冰夜'">桌游夜</button>
          </div>
          <div class="step step-two"><i>2</i><div><b>补充一点偏好</b><small>帮助方案更贴近你</small></div></div>
          <div class="preference-grid">
            <label>预计人数<select v-model="people"><option>6-12 人</option><option>12-20 人</option><option>20-50 人</option></select></label>
            <label>活动氛围<select v-model="style"><option>轻松破冰</option><option>沉浸体验</option><option>专业交流</option></select></label>
          </div>
          <p v-if="error" class="form-error">{{ error }}</p>
          <button class="generate-btn" :disabled="loading" @click="generate">
            <LoaderCircle v-if="loading" class="spin" />
            <WandSparkles v-else />
            {{ loading ? '正在构思有趣的细节' : '生成活动方案' }}
          </button>
          <p class="ai-note">内容由 AI 生成，发布前请确认时间、地点与安全信息。</p>
        </section>
        <section :class="['result-panel', { empty: !generated }]">
          <div v-if="!generated" class="empty-result">
            <div class="magic-orb"><Sparkles /></div>
            <h3>你的活动方案会出现在这里</h3>
            <p>包括标题、亮点、流程、物资和招募文案。</p>
          </div>
          <div v-else class="generated">
            <div class="result-head">
              <span><Check :size="15" />{{ plan.aiGenerated ? 'AI 方案已生成' : '本地模板方案' }}</span>
              <div><button @click="copyPlan"><Copy :size="15" />复制</button><button @click="generate"><RotateCcw :size="15" />重生成</button></div>
            </div>
            <p v-if="plan.notice" :class="['result-notice', { degraded: !plan.aiGenerated }]">{{ plan.notice }}</p>
            <small class="field-label">活动标题</small>
            <h2>{{ plan.title }}</h2>
            <div class="generated-tags"><span v-for="tag in plan.tags" :key="tag">{{ tag }}</span></div>
            <small class="field-label">活动亮点</small>
            <p>{{ plan.introduction }}</p>
            <small class="field-label">建议流程</small>
            <ol><li v-for="item in plan.schedule" :key="item">{{ item }}</li></ol>
            <small class="field-label">安全须知</small>
            <p>{{ plan.safetyNote }}</p>
            <button class="btn btn-primary publish" @click="adoptPlan">采用方案并继续编辑</button>
          </div>
        </section>
      </div>
    </div>
  </div>
</template>

<style scoped>
.planner-page{min-height:calc(100vh - 72px);padding:55px 0 90px;background:linear-gradient(145deg,#f4f0ff,#fff 50%,#fff4ef)}.planner-head{text-align:center;margin-bottom:35px}.planner-head>span{display:inline-flex;align-items:center;gap:7px;color:#6f4ad8;font-size:11px;font-weight:900;letter-spacing:.15em}.planner-head h1{margin:14px 0 12px;font-size:42px;line-height:1.2}.planner-head p{color:var(--color-ink-soft)}.planner-grid{display:grid;grid-template-columns:1fr 1fr;gap:20px}.prompt-panel,.result-panel{min-height:570px;padding:27px;border-radius:var(--radius-lg);box-shadow:var(--shadow-card)}.prompt-panel{border:1px solid #e8e1fa;background:rgba(255,255,255,.9)}.step{display:flex;gap:12px;align-items:center;margin-bottom:15px}.step i{width:28px;height:28px;border-radius:50%;background:#6f4ad8;color:#fff;display:grid;place-items:center;font-style:normal;font-size:12px;font-weight:800}.step div{display:flex;flex-direction:column}.step small{color:var(--color-ink-soft);font-size:10px}.prompt-panel textarea{width:100%;height:125px;padding:15px;border:1px solid #ded7f2;border-radius:14px;outline:0;resize:none;line-height:1.7}.quick-prompts{display:flex;gap:7px;margin:10px 0 25px;flex-wrap:wrap}.quick-prompts button{border:0;background:#f5f2fc;border-radius:var(--radius-pill);padding:7px 10px;color:#665c80;font-size:10px}.preference-grid{display:grid;grid-template-columns:1fr 1fr;gap:12px}.preference-grid label{display:flex;flex-direction:column;gap:6px;font-size:11px;font-weight:700}.preference-grid select{padding:11px;border:1px solid var(--color-line);border-radius:10px;background:#fff}.generate-btn{width:100%;margin-top:24px;padding:14px;border:0;border-radius:12px;background:linear-gradient(110deg,#6f4ad8,#9b63e9);color:#fff;font-weight:800;display:flex;align-items:center;justify-content:center;gap:8px}.ai-note{text-align:center;color:var(--color-ink-soft);font-size:9px}.result-panel{background:linear-gradient(145deg,#20193c,#33235f);color:#fff}.empty-result{height:100%;display:flex;align-items:center;justify-content:center;flex-direction:column;text-align:center}.magic-orb{width:74px;height:74px;border-radius:24px;background:linear-gradient(145deg,#8f68e6,#6f4ad8);display:grid;place-items:center}.empty-result p{color:#afa6c8}.result-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:25px}.result-head>span{display:flex;align-items:center;gap:5px;color:#9fe4d9;font-size:11px;font-weight:800}.result-head div{display:flex;gap:5px}.result-head button{border:1px solid rgba(255,255,255,.15);background:transparent;color:#c9c2da;border-radius:7px;padding:7px;display:flex;gap:4px;font-size:9px}.field-label{display:block;margin:20px 0 7px;color:#afa6c8;letter-spacing:.08em}.generated h2{font-size:25px}.generated>p{color:#d2cce0;font-size:12px;line-height:1.8}.generated-tags{display:flex;gap:6px;flex-wrap:wrap}.generated-tags span{padding:6px 8px;border-radius:6px;background:rgba(255,255,255,.1);font-size:9px}.generated ol{padding-left:20px;color:#d2cce0;font-size:11px;line-height:2}.publish{width:100%;margin-top:14px}.form-error{padding:9px;border-radius:8px;background:#ffeaed;color:var(--color-danger);font-size:10px}.spin{animation:spin 1s linear infinite}@keyframes spin{to{rotate:360deg}}@media(max-width:800px){.planner-grid{grid-template-columns:1fr}.planner-head h1{font-size:34px}.result-panel{min-height:500px}}@media(max-width:500px){.preference-grid{grid-template-columns:1fr}.planner-head h1{font-size:29px}.prompt-panel,.result-panel{padding:20px}}
.result-notice{margin:0 0 14px;padding:9px 10px;border:1px solid rgba(159,228,217,.24);border-radius:8px;background:rgba(159,228,217,.08);color:#bff1e9;font-size:10px}.result-notice.degraded{border-color:rgba(255,255,255,.16);background:rgba(255,255,255,.08);color:#d9d3e8}
</style>
