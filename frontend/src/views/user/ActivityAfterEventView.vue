<script setup lang="ts">
import { ArrowLeft, Bot, Camera, Check, Clock3, ImagePlus, MessageSquareText, Send, Sparkles, Star, UploadCloud } from 'lucide-vue-next'
import { computed, onMounted, reactive, ref } from 'vue'
import { useRoute } from 'vue-router'
import { apiGet, apiPost, apiUpload } from '@/lib/api'
import { useAppStore } from '@/stores/app'
import type { Activity, ActivityAfterEvent, ActivitySummary } from '@/types'

interface FileResponse { id: string; url: string; originalName: string }
interface ClassificationResponse { categories: string[]; aiAvailable: boolean; notice: string }
interface EditableImage { url: string; aiCategory: string; confirmedCategory: string }

const route = useRoute()
const app = useAppStore()
const activity = ref<Activity | null>(null)
const afterEvent = ref<ActivityAfterEvent | null>(null)
const loading = ref(true)
const error = ref('')
const uploading = ref(false)
const classifying = ref(false)
const publishing = ref(false)
const reviewing = ref(false)
const classificationNotice = ref('')
const imageInput = ref<HTMLInputElement | null>(null)
const images = ref<EditableImage[]>([])
const summaryForm = reactive({ title: '', content: '' })
const reviewForm = reactive({ rating: 0, content: '' })
const categories = ['合影', '场地', '过程记录', '物资', '成果展示']

const canPublish = computed(() => Boolean(summaryForm.title.trim() && summaryForm.content.trim() && images.value.length && images.value.every(item => item.confirmedCategory)))
const ratingLabel = computed(() => ['请选择评分', '不太满意', '有待改进', '还不错', '很满意', '超出期待'][reviewForm.rating])

async function loadData() {
  loading.value = true
  error.value = ''
  try {
    const [activityRow, feedback] = await Promise.all([
      apiGet<Activity>(`/activities/${route.params.id}`),
      apiGet<ActivityAfterEvent>(`/activities/${route.params.id}/after-event`),
    ])
    activity.value = activityRow
    afterEvent.value = feedback
    if (feedback.summary) applySummary(feedback.summary)
    if (feedback.myReview) {
      reviewForm.rating = feedback.myReview.rating
      reviewForm.content = feedback.myReview.content || ''
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '活动回顾加载失败'
  } finally {
    loading.value = false
  }
}

function applySummary(summary: ActivitySummary) {
  summaryForm.title = summary.title
  summaryForm.content = summary.content
  images.value = (summary.images || []).map(item => ({ ...item }))
}

async function uploadImages(event: Event) {
  const input = event.target as HTMLInputElement
  const files = Array.from(input.files || [])
  if (!files.length) return
  uploading.value = true
  try {
    for (const file of files.slice(0, Math.max(0, 9 - images.value.length))) {
      const form = new FormData()
      form.append('file', file)
      const uploaded = await apiUpload<FileResponse>('/files/upload', form)
      images.value.push({ url: uploaded.url, aiCategory: '', confirmedCategory: '' })
    }
    await classifyImages()
  } catch (err) {
    app.showToast(err instanceof Error ? err.message : '图片上传失败')
  } finally {
    uploading.value = false
    input.value = ''
  }
}

async function classifyImages() {
  if (!images.value.length || classifying.value) return
  classifying.value = true
  try {
    const result = await apiPost<ClassificationResponse>(`/activities/${route.params.id}/summaries/classify`, { imageUrls: images.value.map(item => item.url) })
    images.value = images.value.map((item, index) => ({
      ...item,
      aiCategory: result.categories[index] || '过程记录',
      confirmedCategory: item.confirmedCategory || result.categories[index] || '过程记录',
    }))
    classificationNotice.value = result.notice
  } catch (err) {
    classificationNotice.value = `${err instanceof Error ? err.message : 'AI 分类失败'}，你仍可手动选择分类后发布。`
    images.value = images.value.map(item => ({ ...item, confirmedCategory: item.confirmedCategory || '过程记录' }))
  } finally {
    classifying.value = false
  }
}

function removeImage(index: number) {
  images.value.splice(index, 1)
}

async function publishSummary() {
  if (!canPublish.value || publishing.value) return
  publishing.value = true
  try {
    await apiPost(`/activities/${route.params.id}/summaries`, {
      title: summaryForm.title,
      content: summaryForm.content,
      imageUrls: images.value.map(item => item.url),
      confirmedCategories: images.value.map(item => item.confirmedCategory),
    })
    app.showToast(afterEvent.value?.summary ? '活动总结已更新' : '活动总结已发布')
    await loadData()
  } catch (err) {
    app.showToast(err instanceof Error ? err.message : '总结发布失败')
  } finally {
    publishing.value = false
  }
}

async function submitReview() {
  if (!reviewForm.rating || reviewing.value) return
  reviewing.value = true
  try {
    await apiPost(`/activities/${route.params.id}/reviews`, reviewForm)
    app.showToast(afterEvent.value?.myReview ? '评价已更新' : '感谢你的真实反馈')
    await loadData()
  } catch (err) {
    app.showToast(err instanceof Error ? err.message : '评价提交失败')
  } finally {
    reviewing.value = false
  }
}

function formatDeadline(value?: string) {
  if (!value) return ''
  return new Date(value).toLocaleString('zh-CN', { month: 'long', day: 'numeric', hour: '2-digit', minute: '2-digit', hour12: false })
}

onMounted(loadData)
</script>

<template>
  <div class="after-page">
    <div v-if="loading" class="container loading-state"><Sparkles /><b>正在整理活动回顾…</b></div>
    <div v-else-if="error" class="container error-state">{{ error }}</div>
    <template v-else-if="activity && afterEvent">
      <section class="after-hero">
        <div class="container">
          <RouterLink :to="`/activities/${activity.id}`" class="back"><ArrowLeft :size="16" />返回活动详情</RouterLink>
          <div class="hero-card"><img :src="activity.cover" :alt="activity.title" /><div class="hero-shade"></div><div class="hero-copy"><span>ACTIVITY MOMENTS · 活动回顾</span><h1>{{ activity.title }}</h1><p>{{ activity.date }} · {{ activity.location }}</p></div><div class="rating-badge"><Star fill="currentColor" /><b>{{ afterEvent.averageRating || '—' }}</b><small>{{ afterEvent.reviewCount }} 条评价</small></div></div>
        </div>
      </section>

      <div class="container after-layout">
        <main>
          <article v-if="afterEvent.summary" class="summary-story panel">
            <div class="section-kicker"><Camera :size="16" />发起人图文总结</div>
            <h2>{{ afterEvent.summary.title }}</h2>
            <div class="author-line"><img :src="afterEvent.summary.authorAvatar" /><span><b>{{ afterEvent.summary.authorName }}</b><small>{{ afterEvent.summary.createdAt }} 发布</small></span></div>
            <p>{{ afterEvent.summary.content }}</p>
            <div class="story-gallery"><figure v-for="image in afterEvent.summary.images" :key="image.url"><img :src="image.url" /><figcaption>{{ image.confirmedCategory }}</figcaption></figure></div>
          </article>
          <section v-else class="panel empty-summary"><ImagePlus :size="34" /><h2>活动故事还在整理中</h2><p>发起人发布后，照片与文字会沉淀在这里。</p></section>

          <section v-if="afterEvent.canPublishSummary" class="panel editor-panel">
            <div class="section-head"><div><span class="section-kicker"><Sparkles :size="16" />图文总结工作台</span><h2>{{ afterEvent.summary ? '更新活动总结' : '发布活动总结' }}</h2></div><span class="step-chip">上传 → AI 分类 → 人工确认 → 发布</span></div>
            <label>总结标题<input v-model.trim="summaryForm.title" maxlength="80" placeholder="给这次共同经历起一个标题" /></label>
            <label>总结正文<textarea v-model.trim="summaryForm.content" maxlength="4000" placeholder="记录现场故事、难忘瞬间和活动成果…"></textarea></label>
            <input ref="imageInput" class="hidden-file" type="file" accept="image/*" multiple @change="uploadImages" />
            <div class="upload-row"><button class="upload-button" :disabled="uploading || images.length >= 9" @click="imageInput?.click()"><UploadCloud />{{ uploading ? '上传中…' : '上传活动照片' }}</button><button v-if="images.length" class="ai-button" :disabled="classifying" @click="classifyImages"><Bot />{{ classifying ? 'AI 分类中…' : '重新进行 AI 分类' }}</button><span>{{ images.length }}/9 张</span></div>
            <p v-if="classificationNotice" class="classification-notice"><Bot :size="16" />{{ classificationNotice }}</p>
            <div v-if="images.length" class="image-editor-grid"><div v-for="(image,index) in images" :key="image.url" class="image-editor"><img :src="image.url" /><button @click="removeImage(index)">×</button><small v-if="image.aiCategory">AI 建议：{{ image.aiCategory }}</small><label>确认分类<select v-model="image.confirmedCategory"><option disabled value="">请选择</option><option v-for="category in categories" :key="category">{{ category }}</option></select></label></div></div>
            <div class="publish-row"><span><Check :size="16" />所有图片分类均需人工确认</span><button class="btn btn-primary" :disabled="!canPublish || publishing" @click="publishSummary"><Send :size="16" />{{ publishing ? '发布中…' : afterEvent.summary ? '保存更新' : '发布最终总结' }}</button></div>
          </section>

          <section class="panel reviews-panel">
            <div class="section-head"><div><span class="section-kicker"><MessageSquareText :size="16" />参与者评价</span><h2>大家怎么说</h2></div><div class="score"><b>{{ afterEvent.averageRating || '—' }}</b><span><Star v-for="n in 5" :key="n" :fill="n <= Math.round(afterEvent.averageRating) ? 'currentColor' : 'none'" /><small>{{ afterEvent.reviewCount }} 人评价</small></span></div></div>
            <div v-if="afterEvent.reviews.length" class="review-list"><article v-for="review in afterEvent.reviews" :key="review.id"><img :src="review.avatar" /><div><header><b>{{ review.nickname }}</b><span><Star v-for="n in 5" :key="n" :fill="n <= review.rating ? 'currentColor' : 'none'" /></span></header><p>{{ review.content || '这位伙伴只留下了星星。' }}</p><small>{{ review.createdAt }}<em v-if="review.mine">我的评价</em></small></div></article></div>
            <div v-else class="no-reviews">还没有评价，参加过活动的伙伴可以来留下第一条。</div>
          </section>
        </main>

        <aside>
          <section v-if="afterEvent.canReview" class="review-form panel">
            <span class="section-kicker"><Star :size="16" />我的评价</span><h2>{{ afterEvent.myReview ? '修改评价' : '分享你的体验' }}</h2>
            <div class="deadline"><Clock3 :size="15" />评价开放至 {{ formatDeadline(afterEvent.reviewDeadline) }}</div>
            <div class="star-picker"><button v-for="n in 5" :key="n" :class="{active:n<=reviewForm.rating}" @click="reviewForm.rating=n"><Star :fill="n<=reviewForm.rating?'currentColor':'none'" /></button></div><b class="rating-label">{{ ratingLabel }}</b>
            <textarea v-model.trim="reviewForm.content" maxlength="600" placeholder="说说活动中让你印象最深的瞬间…"></textarea>
            <button class="btn btn-primary review-submit" :disabled="!reviewForm.rating || reviewing" @click="submitReview">{{ reviewing ? '提交中…' : afterEvent.myReview ? '更新我的评价' : '提交评价' }}</button>
            <small>同一活动仅保留一条评价，期限内可修改。</small>
          </section>
          <section v-else class="eligibility panel"><div :class="{expired:afterEvent.reviewExpired}"><Clock3 /></div><h3>{{ afterEvent.reviewExpired ? '评价已截止' : '评价入口暂未开放' }}</h3><p>{{ afterEvent.eligibilityMessage }}</p></section>
          <section class="memory-note"><Sparkles /><b>把共同经历留下来</b><p>真实照片与参与者反馈，会成为下一次相遇最好的参考。</p></section>
        </aside>
      </div>
    </template>
  </div>
</template>

<style scoped>
.after-page{padding-bottom:80px}.after-hero{padding:24px 0 42px;background:linear-gradient(180deg,#edeae4 0,#f6f3ee 100%)}.back{margin-bottom:16px;display:inline-flex;align-items:center;gap:6px;color:var(--color-ink-soft);font-size:12px}.hero-card{position:relative;height:360px;overflow:hidden;border-radius:28px;box-shadow:var(--shadow-card)}.hero-card>img{width:100%;height:100%;object-fit:cover}.hero-shade{position:absolute;inset:0;background:linear-gradient(90deg,rgba(14,21,32,.82),rgba(14,21,32,.15) 70%)}.hero-copy{position:absolute;left:42px;bottom:40px;color:#fff}.hero-copy>span{color:#ffc857;font-size:11px;font-weight:900;letter-spacing:.12em}.hero-copy h1{max-width:720px;margin:10px 0;font-size:38px}.hero-copy p{margin:0;color:#e9ebee}.rating-badge{position:absolute;right:28px;bottom:28px;padding:15px 19px;border:1px solid rgba(255,255,255,.35);border-radius:18px;background:rgba(255,255,255,.92);display:grid;grid-template-columns:auto auto;align-items:center;gap:2px 7px;color:#e7a710}.rating-badge svg{width:22px}.rating-badge b{font-size:25px;color:var(--color-ink)}.rating-badge small{grid-column:1/3;color:var(--color-ink-soft);text-align:center}.after-layout{display:grid;grid-template-columns:minmax(0,1fr) 330px;gap:24px;margin-top:-20px;position:relative}.panel{padding:28px;border:1px solid var(--color-line);border-radius:20px;background:#fff;box-shadow:0 8px 30px rgba(23,34,56,.05)}main>.panel{margin-bottom:20px}.section-kicker{display:inline-flex;align-items:center;gap:6px;color:var(--color-primary);font-size:11px;font-weight:900}.section-head{display:flex;align-items:flex-start;justify-content:space-between;gap:20px}.section-head h2,.editor-panel h2{margin:8px 0 18px;font-size:24px}.summary-story>h2{margin:10px 0 14px;font-size:28px}.author-line{display:flex;align-items:center;gap:9px}.author-line img{width:38px;height:38px;border-radius:50%;object-fit:cover}.author-line span{display:flex;flex-direction:column}.author-line small{margin-top:3px;color:var(--color-ink-soft);font-size:9px}.summary-story>p{margin:22px 0;color:#47505f;line-height:1.95;white-space:pre-wrap}.story-gallery{display:grid;grid-template-columns:repeat(2,1fr);gap:12px}.story-gallery figure{position:relative;margin:0;overflow:hidden;border-radius:14px;background:var(--color-bg)}.story-gallery figure:first-child:nth-last-child(odd){grid-column:1/3}.story-gallery img{display:block;width:100%;height:250px;object-fit:cover}.story-gallery figcaption{position:absolute;left:10px;bottom:10px;padding:5px 9px;border-radius:20px;background:rgba(19,29,43,.74);color:#fff;font-size:9px}.empty-summary{text-align:center;padding:60px;color:var(--color-ink-soft)}.empty-summary svg{color:var(--color-primary)}.empty-summary h2{color:var(--color-ink)}.step-chip{padding:7px 10px;border-radius:20px;background:var(--color-primary-soft);color:var(--color-primary);font-size:9px;font-weight:800}.editor-panel>label{display:block;margin:15px 0;color:var(--color-ink-soft);font-size:11px;font-weight:800}.editor-panel input,.editor-panel textarea,.image-editor select,.review-form textarea{width:100%;margin-top:7px;padding:12px;border:1px solid var(--color-line);border-radius:11px;background:#faf9f7;outline:none}.editor-panel textarea{min-height:150px;resize:vertical;line-height:1.7}.hidden-file{display:none}.upload-row{display:flex;align-items:center;gap:9px}.upload-row>span{margin-left:auto;color:var(--color-ink-soft);font-size:10px}.upload-button,.ai-button{padding:10px 13px;border:0;border-radius:10px;display:flex;align-items:center;gap:7px;font-weight:800}.upload-button{background:var(--color-ink);color:#fff}.ai-button{background:#eee9ff;color:#6745c5}.upload-button svg,.ai-button svg{width:17px}.classification-notice{padding:10px 12px;border-radius:10px;background:#f4f0ff;color:#6745c5;display:flex;align-items:center;gap:7px;font-size:10px}.image-editor-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:11px;margin-top:15px}.image-editor{position:relative;padding:8px;border:1px solid var(--color-line);border-radius:13px}.image-editor>img{width:100%;height:120px;border-radius:9px;object-fit:cover}.image-editor>button{position:absolute;right:13px;top:13px;width:25px;height:25px;border:0;border-radius:50%;background:rgba(20,28,40,.72);color:#fff}.image-editor>small{display:block;margin:6px 2px;color:#6745c5;font-size:8px}.image-editor label{font-size:9px;font-weight:800}.image-editor select{padding:8px}.publish-row{margin-top:18px;padding-top:16px;border-top:1px solid var(--color-line);display:flex;align-items:center;justify-content:space-between}.publish-row>span{color:var(--color-ink-soft);display:flex;align-items:center;gap:5px;font-size:10px}.score{display:flex;align-items:center;gap:8px}.score>b{font-size:31px}.score>span{display:flex;color:#ffc13c}.score svg{width:13px}.score small{display:block;margin-left:5px;color:var(--color-ink-soft)}.review-list article{padding:17px 0;border-top:1px solid var(--color-line);display:flex;gap:12px}.review-list article>img{width:42px;height:42px;border-radius:50%;object-fit:cover}.review-list article>div{flex:1}.review-list header{display:flex;justify-content:space-between}.review-list header span{display:flex;color:#ffc13c}.review-list header svg{width:13px}.review-list p{margin:8px 0;color:#596170;line-height:1.65}.review-list small{color:var(--color-ink-soft);font-size:9px}.review-list em{margin-left:8px;padding:2px 6px;border-radius:8px;background:var(--color-primary-soft);color:var(--color-primary);font-style:normal}.no-reviews{padding:35px;text-align:center;color:var(--color-ink-soft)}aside{position:relative}.review-form{position:sticky;top:90px}.review-form h2{margin:8px 0}.deadline{padding:9px;border-radius:9px;background:#fff7e4;color:#916311;display:flex;align-items:center;gap:6px;font-size:9px}.star-picker{display:flex;justify-content:center;margin:20px 0 5px}.star-picker button{padding:2px;border:0;background:none;color:#d8dadd}.star-picker button.active{color:#ffc13c}.star-picker svg{width:31px;height:31px}.rating-label{display:block;text-align:center;font-size:11px}.review-form textarea{min-height:120px;resize:vertical;line-height:1.6}.review-submit{width:100%;margin-top:11px}.review-form>small{display:block;margin-top:10px;color:var(--color-ink-soft);text-align:center}.eligibility{text-align:center}.eligibility>div{width:54px;height:54px;margin:auto;border-radius:50%;background:var(--color-mint-soft);color:var(--color-mint);display:grid;place-items:center}.eligibility>div.expired{background:#eee;color:#8d929b}.eligibility p{color:var(--color-ink-soft);line-height:1.6;font-size:11px}.memory-note{margin-top:16px;padding:22px;border-radius:18px;background:linear-gradient(135deg,#1d2939,#3e4c61);color:#fff}.memory-note svg{color:#ffc857}.memory-note b{display:block;margin:8px 0}.memory-note p{margin:0;color:#ced4de;font-size:10px;line-height:1.6}.loading-state,.error-state{min-height:500px;display:grid;place-items:center}.loading-state{align-content:center;gap:10px;color:var(--color-primary)}.error-state{color:var(--color-danger)}
@media(max-width:900px){.after-layout{grid-template-columns:1fr}.review-form{position:static}.image-editor-grid{grid-template-columns:repeat(2,1fr)}}@media(max-width:600px){.after-hero{padding-top:14px}.hero-card{height:300px;border-radius:20px}.hero-copy{left:22px;bottom:25px}.hero-copy h1{font-size:27px}.rating-badge{right:14px;top:14px;bottom:auto}.after-layout{margin-top:-12px}.panel{padding:20px}.story-gallery{grid-template-columns:1fr}.story-gallery figure:first-child:nth-last-child(odd){grid-column:auto}.story-gallery img{height:220px}.image-editor-grid{grid-template-columns:1fr}.section-head,.publish-row{align-items:flex-start;flex-direction:column}.publish-row .btn{width:100%}}
</style>
