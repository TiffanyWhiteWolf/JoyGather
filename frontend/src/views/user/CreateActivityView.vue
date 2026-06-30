<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CalendarDays, Check, ChevronLeft, ClipboardCopy, MapPin, Save, ShieldCheck, Sparkles, Users } from 'lucide-vue-next'
import { useAppStore } from '@/stores/app'
import { apiGet, apiPost, apiPut } from '@/lib/api'
import LocationPicker from '@/components/map/LocationPicker.vue'
import type { Activity, ActivityCategory, ActivityDraft } from '@/types'

const app = useAppStore()
const route = useRoute()
const router = useRouter()
const step = ref(1)
const submitted = ref(false)
const submitError = ref('')
const saving = ref(false)
const submitting = ref(false)
const persistedDraftId = ref('')
const savedAt = ref('尚未保存')
const longitude = ref(120.15507)
const latitude = ref(30.274085)
const categories: ActivityCategory[] = ['城市探索', '户外运动', '桌游聚会', '学习交流', '运动健身', '公益活动']
const templates = [
  { name: '城市探索', category: '城市探索' as ActivityCategory, title: '周末城市漫步', tags: 'Citywalk、摄影、新手友好', summary: '不赶路地认识城市，也认识同行的人。', safety: '请穿舒适的鞋，遵守交通规则并保持队伍联系。' },
  { name: '户外徒步', category: '户外运动' as ActivityCategory, title: '新手友好轻徒步', tags: '徒步、自然、零基础', summary: '低强度路线，途中安排休息与补给。', safety: '根据天气准备防晒或雨具，领队携带急救包。' },
  { name: '桌游聚会', category: '桌游聚会' as ActivityCategory, title: '不尴尬桌游破冰夜', tags: '桌游、室内、破冰', summary: '主持人全程带玩，不需要任何经验。', safety: '请保管好个人物品，未成年人需监护人确认。' },
]

const form = reactive<ActivityDraft>({
  id: `draft-${Date.now()}`, title: '', category: '城市探索', tags: '', summary: '',
  date: '', startTime: '', endTime: '', location: '', district: '拱墅区', capacity: 20,
  deadline: '', price: 0, minAge: 16, safetyNote: '', joinFields: ['真实姓名', '手机号码'], updatedAt: '',
})
const reviewMode = computed(() => form.capacity > 50 ? '人工审核' : 'AI 自动审核')

interface ActivityRequest {
  title: string
  summary: string
  description: string
  category: ActivityCategory
  tags: string[]
  date: string
  time: string
  startTime: string
  endTime: string
  deadline: string
  location: string
  district: string
  capacity: number
  price: number
  longitude: number
  latitude: number
  minAge: number
  safetyNote: string
  joinFields: string[]
  submitToken: string
}

type FieldErrorKey =
  | 'title'
  | 'tags'
  | 'summary'
  | 'date'
  | 'deadline'
  | 'startTime'
  | 'endTime'
  | 'location'
  | 'capacity'
  | 'safetyNote'

const fieldErrors = reactive<Record<FieldErrorKey, string>>({
  title: '',
  tags: '',
  summary: '',
  date: '',
  deadline: '',
  startTime: '',
  endTime: '',
  location: '',
  capacity: '',
  safetyNote: '',
})

function clearFieldErrors() {
  (Object.keys(fieldErrors) as FieldErrorKey[]).forEach((key) => {
    fieldErrors[key] = ''
  })
}

function useTemplate(index: number) {
  const item = templates[index]
  Object.assign(form, { category: item.category, title: item.title, tags: item.tags, summary: item.summary, safetyNote: item.safety })
  app.showToast(`已应用「${item.name}」模板`)
}

function validateCurrentStep() {
  submitError.value = ''
  clearFieldErrors()

  if (step.value === 1) {
    if (!form.title.trim()) fieldErrors.title = '请输入活动名称。'
    if (!form.tags.trim()) fieldErrors.tags = '请输入活动标签。'
    if (!form.summary.trim()) fieldErrors.summary = '请输入活动简介。'
  }

  if (step.value === 2) {
    if (!form.date) fieldErrors.date = '请选择活动日期。'
    if (!form.deadline) fieldErrors.deadline = '请选择报名截止时间。'
    if (!form.startTime) fieldErrors.startTime = '请选择开始时间。'
    if (!form.endTime) fieldErrors.endTime = '请选择结束时间。'
    if (!form.location.trim()) fieldErrors.location = '请输入集合地点。'

    if (form.startTime && form.endTime && form.startTime >= form.endTime) {
      fieldErrors.endTime = '活动结束时间需要晚于开始时间。'
    }
    if (form.date && form.startTime && new Date(`${form.date}T${form.startTime}:00`).getTime() <= Date.now()) {
      fieldErrors.startTime = '活动开始时间需要晚于当前时间。'
    }
    if (form.date && form.startTime && form.deadline && new Date(form.deadline).getTime() > new Date(`${form.date}T${form.startTime}:00`).getTime()) {
      fieldErrors.deadline = '报名截止时间不能晚于活动开始时间。'
    }
  }

  if (step.value === 3) {
    if (!Number.isInteger(form.capacity) || form.capacity < 2) {
      fieldErrors.capacity = '人数上限必须为大于等于 2 的整数。'
    }
    if (!form.safetyNote.trim()) {
      fieldErrors.safetyNote = '请补充安全须知。'
    }
  }

  return !(Object.values(fieldErrors).some(Boolean))
}

function splitTags() {
  return form.tags.split(/[、,，]/).map(item => item.trim()).filter(Boolean).slice(0, 5)
}

function toActivityRequest(): ActivityRequest {
  return {
    title: form.title,
    summary: form.summary,
    description: form.summary,
    category: form.category,
    tags: splitTags(),
    date: form.date,
    time: `${form.startTime} - ${form.endTime}`,
    startTime: form.startTime,
    endTime: form.endTime,
    deadline: form.deadline,
    location: form.location,
    district: form.district,
    capacity: form.capacity,
    price: form.price,
    longitude: longitude.value,
    latitude: latitude.value,
    minAge: form.minAge,
    safetyNote: form.safetyNote,
    joinFields: form.joinFields,
    submitToken: form.id,
  }
}

async function next() {
  if (submitting.value || saving.value) return
  if (step.value < 4) {
    if (!validateCurrentStep()) return
    const saved = await persistDraft(false)
    if (saved) step.value++
    return
  }

  for (const targetStep of [1, 2, 3]) {
    step.value = targetStep
    if (!validateCurrentStep()) return
  }
  step.value = 4
  submitting.value = true
  try {
    const saved = await persistDraft(false)
    if (!saved || !persistedDraftId.value) return
    await apiPost<Activity>(`/activities/${persistedDraftId.value}/submit`, {})
    app.clearDraft()
    await app.refreshUserState()
    submitted.value = true
  } catch (err) {
    submitError.value = err instanceof Error ? err.message : '提交审核失败，请稍后重试'
  } finally {
    submitting.value = false
  }
}

async function persistDraft(notify = true) {
  saving.value = true
  submitError.value = ''
  try {
    const saved = persistedDraftId.value
      ? await apiPut<Activity>(`/activities/drafts/${persistedDraftId.value}`, toActivityRequest())
      : await apiPost<Activity>('/activities/drafts', toActivityRequest())
    persistedDraftId.value = saved.id
    form.id = saved.id
    savedAt.value = (saved.updatedAt || new Date().toISOString()).replace('T', ' ').slice(0, 16)
    if (notify) app.showToast('草稿已保存，可在“我的草稿”中继续编辑')
    return saved
  } catch (err) {
    submitError.value = err instanceof Error ? err.message : '保存草稿失败'
    return null
  } finally {
    saving.value = false
  }
}

async function save() {
  await persistDraft(true)
}

function startNewActivity() {
  submitted.value = false
  step.value = 1
  persistedDraftId.value = ''
  savedAt.value = '尚未保存'
  submitError.value = ''
  Object.assign(form, {
    id: `draft-${Date.now()}`, title: '', category: '城市探索', tags: '', summary: '',
    date: '', startTime: '', endTime: '', location: '', district: '拱墅区', capacity: 20,
    deadline: '', price: 0, minAge: 16, safetyNote: '', joinFields: ['真实姓名', '手机号码'], updatedAt: '',
  })
  longitude.value = 120.15507
  latitude.value = 30.274085
}

function toggleField(field: string) {
  form.joinFields = form.joinFields.includes(field) ? form.joinFields.filter(item => item !== field) : [...form.joinFields, field]
}

function selectLocation(point: { location: string; district: string; longitude: number; latitude: number }) {
  form.location = point.location
  form.district = point.district
  longitude.value = point.longitude
  latitude.value = point.latitude
}

onMounted(async () => {
  const draftId = String(route.query.draft || '')
  if (draftId) {
    try {
      const drafts = await apiGet<Activity[]>('/activities/drafts')
      const source = drafts.find(item => item.id === draftId)
      if (!source) throw new Error('草稿不存在或已提交')
      persistedDraftId.value = source.id
      Object.assign(form, {
        id: source.id,
        title: source.title === '未命名草稿' ? '' : source.title,
        category: source.category,
        tags: source.tags.join('、'),
        summary: source.summary === '草稿暂未填写简介' ? '' : source.summary,
        date: source.date || source.startAt?.slice(0, 10) || '',
        startTime: source.startAt?.slice(11, 16) || '',
        endTime: source.endAt?.slice(11, 16) || '',
        deadline: source.deadline?.slice(0, 16) || '',
        location: source.location,
        district: source.district,
        capacity: source.capacity,
        price: Number(source.price),
        minAge: source.minAge || 0,
        safetyNote: source.safetyNote || '',
        joinFields: source.joinFields || [],
      })
      longitude.value = Number(source.longitude)
      latitude.value = Number(source.latitude)
      savedAt.value = (source.updatedAt || '').replace('T', ' ').slice(0, 16) || '已保存'
    } catch (err) {
      submitError.value = err instanceof Error ? err.message : '草稿加载失败'
    }
  }
  const cloneId = String(route.query.clone || '')
  if (cloneId) {
    const source = await apiGet<Activity>(`/activities/${cloneId}`)
    if (source) {
      Object.assign(form, { id: `draft-${Date.now()}`, title: `${source.title}（复刻）`, category: source.category, tags: source.tags.join('、'), summary: source.description, location: source.location, district: source.district, capacity: source.capacity, price: source.price })
      longitude.value = Number(source.longitude)
      latitude.value = Number(source.latitude)
    }
  }
  if (route.query.ai === '1') Object.assign(form, { title: '月光底片｜老街夜游摄影漫步', category: '城市探索', tags: '城市探索、摄影、新手友好', summary: '用镜头收集老街的灯光与路人，不比器材，只交换观察城市的方式。', safetyNote: '请确认夜间照明、紧急联系人和清晰的集合地点。' })
})
</script>

<template>
  <div class="container create-page">
    <div class="create-head"><RouterLink :to="persistedDraftId ? '/drafts' : '/'"><ChevronLeft :size="17" />退出编辑</RouterLink><div><span>草稿：{{ savedAt }}</span><button class="btn btn-outline btn-sm" :disabled="saving || submitting" @click="save"><Save :size="16" />{{ saving ? '保存中' : '保存草稿' }}</button></div></div>
    <div class="stepper"><button v-for="(name,i) in ['基础信息','时间地点','参与设置','预览提交']" :key="name" :class="{active:step>=i+1}" @click="step=i+1"><span><Check v-if="step>i+1" :size="13" /><template v-else>{{ i+1 }}</template></span><b>{{ name }}</b></button></div>

    <div v-if="submitted" class="submit-result panel"><span><ShieldCheck :size="34" /></span><h1>{{ reviewMode === '人工审核' ? '已进入人工审核' : '活动发布成功' }}</h1><p>{{ reviewMode === '人工审核' ? '报名人数超过 50 人，已按规则转交运营人员审核。' : '内容安全检查未发现风险，活动已经进入报名阶段。' }}</p><div><button class="btn btn-outline" @click="startNewActivity">再创建一场</button><button class="btn btn-primary" @click="router.push('/profile')">查看活动管理</button></div></div>

    <div v-else class="create-layout">
      <section class="panel">
        <div class="form-title"><span class="eyebrow">STEP {{ step }} / 4</span><h1>{{ ['给活动一个好开场','把时间地点说清楚','设置参与规则','确认后提交审核'][step-1] }}</h1><p>{{ ['清晰真诚的描述，会吸引真正同频的人。','让参与者知道何时出发、在哪里见面。','明确名额、费用和安全边界。','普通活动由 AI 自动审核，大型或风险活动转人工审核。'][step-1] }}</p></div>

        <template v-if="step===1">
          <div class="templates"><button v-for="(item,i) in templates" :key="item.name" @click="useTemplate(i)"><ClipboardCopy :size="16" /><span><b>{{ item.name }}</b><small>一键填充常用内容</small></span></button></div>
          <div class="input-group"><label>活动名称 *</label><input v-model.trim="form.title" class="input" maxlength="30" placeholder="例如：落日以后，沿运河散步" /><small>{{ form.title.length }} / 30</small><p v-if="fieldErrors.title" class="field-error">{{ fieldErrors.title }}</p></div>
          <div class="form-grid"><div class="input-group"><label>活动类型 *</label><select v-model="form.category" class="select"><option v-for="item in categories" :key="item">{{ item }}</option></select></div><div class="input-group"><label>兴趣标签 *</label><input v-model.trim="form.tags" class="input" placeholder="用顿号分隔，最多 5 个" /><p v-if="fieldErrors.tags" class="field-error">{{ fieldErrors.tags }}</p></div></div>
          <div class="input-group"><label>活动简介 *</label><textarea v-model.trim="form.summary" class="textarea" maxlength="500" placeholder="活动亮点、流程和适合的人群"></textarea><small>{{ form.summary.length }} / 500</small><p v-if="fieldErrors.summary" class="field-error">{{ fieldErrors.summary }}</p></div>
        </template>

        <template v-else-if="step===2">
          <div class="form-grid"><div class="input-group"><label>活动日期 *</label><input v-model="form.date" class="input" type="date" /><p v-if="fieldErrors.date" class="field-error">{{ fieldErrors.date }}</p></div><div class="input-group"><label>报名截止 *</label><input v-model="form.deadline" class="input" type="datetime-local" /><p v-if="fieldErrors.deadline" class="field-error">{{ fieldErrors.deadline }}</p></div></div>
          <div class="form-grid"><div class="input-group"><label>开始时间 *</label><input v-model="form.startTime" class="input" type="time" /><p v-if="fieldErrors.startTime" class="field-error">{{ fieldErrors.startTime }}</p></div><div class="input-group"><label>结束时间 *</label><input v-model="form.endTime" class="input" type="time" /><p v-if="fieldErrors.endTime" class="field-error">{{ fieldErrors.endTime }}</p></div></div>
          <div class="form-grid"><div class="input-group"><label>城区 *</label><select v-model="form.district" class="select"><option v-for="item in ['拱墅区','西湖区','上城区','滨江区','余杭区']" :key="item">{{ item }}</option></select></div><div class="input-group"><label>集合地点 *</label><input v-model.trim="form.location" class="input" placeholder="输入可被准确找到的地点" /><p v-if="fieldErrors.location" class="field-error">{{ fieldErrors.location }}</p></div></div>
          <LocationPicker @select="selectLocation" />
          <div class="map-picker"><MapPin /><div><b>地图选点已开启</b><p>当前坐标：{{ latitude.toFixed(5) }}, {{ longitude.toFixed(5) }} · {{ form.location || '点击地图选择集合点' }}</p></div><button @click="selectLocation({ location:'桥西历史文化街区游客中心', district:'拱墅区', longitude:120.139863, latitude:30.318332 })">选用推荐点位</button></div>
        </template>

        <template v-else-if="step===3">
          <div class="form-grid"><div class="input-group"><label>人数上限 *</label><input v-model.number="form.capacity" class="input" type="number" min="2" max="500" step="1" /><p v-if="fieldErrors.capacity" class="field-error">{{ fieldErrors.capacity }}</p></div><div class="input-group"><label>活动费用（元）</label><input v-model.number="form.price" class="input" type="number" min="0" /></div></div>
          <div class="form-grid"><div class="input-group"><label>最低年龄</label><input v-model.number="form.minAge" class="input" type="number" min="0" max="100" /></div><div class="input-group"><label>审核方式</label><div class="review-mode"><ShieldCheck :size="18" /><span><b>{{ reviewMode }}</b><small>{{ form.capacity > 50 ? '超过 50 人按规则转人工' : '提交后进行内容安全检查' }}</small></span></div></div></div>
          <div class="input-group"><label>报名信息</label><div class="field-checks"><button v-for="item in ['真实姓名','手机号码','紧急联系人','身份证号']" :key="item" :class="{active:form.joinFields.includes(item)}" @click="toggleField(item)"><Check :size="14" />{{ item }}</button></div></div>
          <div class="input-group"><label>安全须知 *</label><textarea v-model.trim="form.safetyNote" class="textarea" placeholder="装备、天气、医疗、紧急联系人等必要说明"></textarea><p v-if="fieldErrors.safetyNote" class="field-error">{{ fieldErrors.safetyNote }}</p></div>
        </template>

        <template v-else>
          <div class="preview-card"><div><span>{{ form.category }}</span><b>{{ form.price ? `¥${form.price}` : '免费' }}</b></div><h2>{{ form.title || '未填写活动名称' }}</h2><p>{{ form.summary || '未填写活动简介' }}</p><dl><div><dt><CalendarDays />时间</dt><dd>{{ form.date }} {{ form.startTime }} - {{ form.endTime }}</dd></div><div><dt><MapPin />地点</dt><dd>{{ form.district }} · {{ form.location }}</dd></div><div><dt><Users />参与</dt><dd>上限 {{ form.capacity }} 人 · 截止 {{ form.deadline.replace('T',' ') }}</dd></div><div><dt><ShieldCheck />审核</dt><dd>{{ reviewMode }}</dd></div></dl></div>
          <label class="submit-agreement"><input type="checkbox" checked /> 我确认活动信息真实，并同意平台内容与安全规范</label>
        </template>

        <p v-if="submitError" class="form-error">{{ submitError }}</p>
        <div class="form-actions"><button v-if="step>1" class="btn btn-outline" :disabled="saving || submitting" @click="step--">上一步</button><button class="btn btn-primary" :disabled="saving || submitting" @click="next">{{ submitting ? '提交中…' : saving ? '保存中…' : step===4 ? '提交审核' : '保存并继续' }}</button></div>
      </section>
      <aside><div class="ai-helper"><Sparkles /><h3>没想好怎么写？</h3><p>AI 可以生成可继续修改的标题、亮点与活动流程。</p><RouterLink to="/ai-planner">让 AI 帮我策划 →</RouterLink></div><div class="tips"><b>发布前小提示</b><p>✓ 地点可被参与者清楚找到</p><p>✓ 截止时间早于活动开始</p><p>✓ 户外活动写明安全须知</p><p>✓ 超过 50 人将人工审核</p></div></aside>
    </div>
  </div>
</template>

<style scoped>
.create-page{padding:26px 0 70px}.create-head{display:flex;justify-content:space-between;align-items:center;margin-bottom:28px}.create-head>a{display:flex;align-items:center;gap:5px;color:var(--color-ink-soft);font-size:13px}.create-head>div{display:flex;align-items:center;gap:12px}.create-head span{color:var(--color-ink-soft);font-size:10px}.stepper{display:flex;justify-content:center;margin-bottom:30px}.stepper button{position:relative;width:150px;border:0;background:none;display:flex;align-items:center;gap:8px;color:#aaa;font-size:11px;cursor:pointer}.stepper button:not(:last-child):after{content:'';position:absolute;right:8px;width:35px;height:1px;background:var(--color-line)}.stepper span{width:26px;height:26px;border-radius:50%;background:#ddd;display:grid;place-items:center}.stepper .active{color:var(--color-ink)}.stepper .active span{background:var(--color-primary);color:#fff}.create-layout{display:grid;grid-template-columns:1fr 280px;gap:20px;align-items:start}.panel{padding:36px}.form-title h1{margin:3px 0 8px;font-size:29px}.form-title p{color:var(--color-ink-soft)}.input-group{margin:18px 0}.input-group small{margin-top:4px;text-align:right;color:var(--color-ink-soft);font-size:9px}.templates{display:grid;grid-template-columns:repeat(3,1fr);gap:9px;margin:22px 0}.templates button{padding:12px;border:1px solid var(--color-line);border-radius:10px;background:var(--color-bg);display:flex;align-items:center;gap:8px;text-align:left;cursor:pointer}.templates svg{color:var(--color-primary)}.templates span{display:flex;flex-direction:column}.templates b{font-size:11px}.templates small{margin-top:3px;color:var(--color-ink-soft);font-size:8px}.map-picker{padding:18px;border:1px dashed var(--color-primary);border-radius:13px;background:var(--color-primary-soft);display:flex;align-items:center;gap:12px}.map-picker>svg{color:var(--color-primary)}.map-picker div{flex:1}.map-picker b{font-size:12px}.map-picker p{margin:4px 0 0;color:var(--color-ink-soft);font-size:10px}.map-picker button{border:0;background:#fff;border-radius:8px;padding:8px;font-size:9px;font-weight:700}.review-mode{min-height:47px;padding:9px 12px;border-radius:10px;background:var(--color-mint-soft);display:flex;align-items:center;gap:8px;color:var(--color-mint)}.review-mode span{display:flex;flex-direction:column}.review-mode b{font-size:11px}.review-mode small{font-size:8px;text-align:left}.field-checks{display:flex;flex-wrap:wrap;gap:7px}.field-checks button{padding:8px 10px;border:1px solid var(--color-line);border-radius:8px;background:#fff;color:var(--color-ink-soft);display:flex;align-items:center;gap:4px;font-size:10px}.field-checks button.active{border-color:var(--color-primary);background:var(--color-primary-soft);color:var(--color-primary)}.preview-card{margin-top:22px;padding:25px;border:1px solid var(--color-line);border-radius:15px;background:var(--color-bg)}.preview-card>div:first-child{display:flex;justify-content:space-between;color:var(--color-primary);font-size:11px;font-weight:800}.preview-card h2{margin:14px 0 8px}.preview-card>p{color:var(--color-ink-soft);line-height:1.7}.preview-card dl{display:grid;grid-template-columns:1fr 1fr;gap:12px;margin:22px 0 0}.preview-card dl>div{padding:12px;background:#fff;border-radius:9px}.preview-card dt{display:flex;align-items:center;gap:5px;color:var(--color-ink-soft);font-size:9px}.preview-card dt svg{width:14px}.preview-card dd{margin:7px 0 0;font-size:11px;font-weight:700}.submit-agreement{display:block;margin:18px 0;font-size:11px}.form-error{padding:10px;border-radius:8px;background:#ffeaed;color:var(--color-danger);font-size:11px}.form-actions{display:flex;justify-content:flex-end;gap:9px;margin-top:24px}.ai-helper,.tips{margin-bottom:14px;padding:22px;border-radius:var(--radius-md)}.ai-helper{background:linear-gradient(145deg,#2b2051,#6f4ad8);color:#fff}.ai-helper>svg{color:#d9c8ff}.ai-helper h3{margin:14px 0 7px}.ai-helper p{color:#d2cae6;font-size:11px;line-height:1.7}.ai-helper a{color:#fff;font-size:11px;font-weight:800}.tips{background:#fff;border:1px solid var(--color-line)}.tips p{color:var(--color-ink-soft);font-size:10px}.submit-result{max-width:720px;margin:70px auto;text-align:center}.submit-result>span{width:70px;height:70px;margin:auto;border-radius:50%;background:var(--color-mint-soft);color:var(--color-mint);display:grid;place-items:center}.submit-result h1{margin:20px 0 10px}.submit-result p{color:var(--color-ink-soft)}.submit-result>div{display:flex;justify-content:center;gap:10px;margin-top:24px}
.field-error{margin-top:6px;color:var(--color-danger);font-size:10px}
@media(max-width:850px){.create-layout{grid-template-columns:1fr}.create-layout aside{display:grid;grid-template-columns:1fr 1fr;gap:12px}.stepper button{width:auto;flex:1}.stepper button:after{display:none}.templates{grid-template-columns:1fr}.preview-card dl{grid-template-columns:1fr}}@media(max-width:600px){.create-head>div>span,.stepper b{display:none}.stepper button{justify-content:center}.panel{padding:22px}.create-layout aside{grid-template-columns:1fr}.map-picker{align-items:flex-start;flex-wrap:wrap}}
</style>
