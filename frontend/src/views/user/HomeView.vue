<script setup lang="ts">
import { ArrowRight, CalendarDays, ChevronRight, MapPin, Search, Sparkles, Users } from 'lucide-vue-next'
import ActivityCard from '@/components/activity/ActivityCard.vue'
import SectionHeader from '@/components/common/SectionHeader.vue'
import CityMap from '@/components/map/CityMap.vue'
import { useRouter } from 'vue-router'
import { computed, onMounted, ref } from 'vue'
import { apiGet } from '@/lib/api'
import type { Activity, Team } from '@/types'

const router = useRouter()
const query = ref('')
const activities = ref<Activity[]>([])
const recommendedActivities = ref<Activity[]>([])
const teams = ref<Team[]>([])
const heroCover = computed(() => activities.value[0]?.cover ?? 'https://images.unsplash.com/photo-1519501025264-65ba15a82390?auto=format&fit=crop&w=1400&q=85')
const nextActivity = computed(() => activities.value[0])
const nearbyCount = computed(() => activities.value.filter(item => item.distance <= 10).length)
const activeMembers = computed(() => teams.value.reduce((total, team) => total + team.members, 0))
const feedTab = ref<'最新' | '推荐' | '附近'>('最新')
const feedActivities = computed(() => {
  if (feedTab.value === '附近') return [...activities.value].sort((a, b) => a.distance - b.distance)
  if (feedTab.value === '推荐') return recommendedActivities.value
  return activities.value
})
const submitSearch = () => router.push({ path: '/discover', query: { q: query.value } })

onMounted(async () => {
  const [activityRows, recommendationRows, teamRows] = await Promise.all([
    apiGet<Activity[]>('/activities'),
    apiGet<Activity[]>('/activities/recommendations?limit=10').catch(() => [] as Activity[]),
    apiGet<Team[]>('/teams'),
  ])
  activities.value = activityRows
  recommendedActivities.value = recommendationRows.length > 0
    ? recommendationRows
    : [...activityRows].sort((a, b) => Number(Boolean(b.featured)) - Number(Boolean(a.featured)))
  teams.value = teamRows
})
</script>

<template>
  <div>
    <section class="home-hero">
      <div class="hero-orb orb-one"></div><div class="hero-orb orb-two"></div>
      <div class="container hero-inner">
        <div class="hero-copy"><span class="hero-kicker"><span></span>本周杭州新增 {{ activities.length }} 场活动</span><h1>在城市里，<br />遇见<span>同频的人</span></h1><p>从一场日落散步开始，把屏幕里的兴趣，变成真实可触的共同经历。</p><form class="hero-search" @submit.prevent="submitSearch"><Search :size="20" /><input v-model="query" placeholder="搜索活动、地点或兴趣" /><button type="submit">去发现 <ArrowRight :size="17" /></button></form><div class="trending"><b>正在发生</b><RouterLink to="/discover?q=Citywalk">Citywalk</RouterLink><RouterLink to="/discover?q=飞盘">飞盘</RouterLink><RouterLink to="/discover?q=桌游">桌游</RouterLink><RouterLink to="/discover?q=徒步">轻徒步</RouterLink></div></div>
        <div class="hero-visual"><img :src="heroCover" alt="城市活动" /><div class="floating-card float-top"><div class="float-icon"><CalendarDays :size="18" /></div><div><small>下一场</small><b>{{ nextActivity?.date || '待更新' }} {{ nextActivity?.time?.split(' - ')[0] || '' }}</b></div></div><div class="floating-card float-bottom"><div class="avatars"><img src="https://i.pravatar.cc/80?img=45" /><img src="https://i.pravatar.cc/80?img=15" /><img src="https://i.pravatar.cc/80?img=32" /></div><div><b>{{ nextActivity?.joined || 0 }} 人已加入</b><small>还剩 {{ nextActivity ? Math.max(0, nextActivity.capacity - nextActivity.joined) : 0 }} 个位置</small></div></div><div class="hero-label"><MapPin :size="15" /> {{ nextActivity?.district || '杭州' }} · {{ nextActivity?.location || '活动地点' }}</div></div>
      </div>
    </section>

    <section class="container page-section">
      <div class="feed-head"><SectionHeader eyebrow="ACTIVITY FEED" :title="`${feedTab}活动`" description="发现城市里的精彩活动，与志趣相投的人不期而遇" link="/discover" /><div class="feed-tabs"><button v-for="tab in ['最新','推荐','附近'] as const" :key="tab" :class="{active:feedTab===tab}" @click="feedTab=tab">{{ tab }}</button></div></div>
      <div class="activity-grid"><ActivityCard v-for="activity in feedActivities.slice(0, 3)" :key="activity.id" :activity="activity" /></div>
    </section>

    <section class="map-section"><div class="container map-feature"><div class="map-copy"><span class="eyebrow">NEARBY</span><h2>你的附近，<br />正在发生什么？</h2><p>切换地图模式，直观看见城市里的活动密度。也许下一个有趣的夜晚，就在两个街区之外。</p><div class="map-stats"><div><b>{{ nearbyCount }}</b><span>附近活动</span></div><div><b>{{ activeMembers }}</b><span>小队成员</span></div></div><RouterLink to="/discover?view=map" class="btn btn-dark">打开活动地图 <ChevronRight :size="18" /></RouterLink></div><CityMap :activities="activities.slice(0,5)" compact /></div></section>

    <section class="container page-section">
      <SectionHeader eyebrow="COMMUNITIES" title="找到你的长期同伴" description="一次见面是开始，一群同好让热爱持续发生" link="/teams" />
      <div class="team-preview-grid"><RouterLink v-for="team in teams" :key="team.id" to="/teams" class="team-preview"><img :src="team.cover" :alt="team.name" /><div class="team-shade"></div><div class="team-copy"><div class="tag-row"><span v-for="tag in team.tags" :key="tag"># {{ tag }}</span></div><h3>{{ team.name }}</h3><p>{{ team.description }}</p><span class="member-count"><Users :size="15" />{{ team.members }} 位成员</span></div></RouterLink></div>
    </section>

    <section class="container ai-banner"><div class="ai-shine"></div><div><span class="ai-badge"><Sparkles :size="14" /> AI 活动策划师</span><h2>有个模糊的念头？<br />让它变成一场完整活动。</h2><p>告诉 AI 主题、人数和偏好，30 秒生成方案、流程和招募文案。</p></div><RouterLink to="/ai-planner" class="btn btn-light">开始策划 <ArrowRight :size="17" /></RouterLink></section>
  </div>
</template>

<style scoped>
.home-hero{position:relative;overflow:hidden;padding:64px 0 76px;background:linear-gradient(145deg,#fff7f2 0%,#f6f2ec 52%,#eaf7f4 100%)}.hero-inner{position:relative;display:grid;grid-template-columns:1.05fr .95fr;align-items:center;gap:72px}.hero-copy{position:relative;z-index:2}.hero-kicker{display:flex;align-items:center;gap:9px;margin-bottom:18px;color:#7c655c;font-size:13px;font-weight:700}.hero-kicker span{width:8px;height:8px;background:var(--color-primary);border-radius:50%;box-shadow:0 0 0 5px var(--color-primary-soft)}h1{margin-bottom:22px;font-size:clamp(42px,5vw,68px);line-height:1.1;letter-spacing:-.065em}h1 span{position:relative;color:var(--color-primary)}h1 span:after{content:'';position:absolute;left:0;right:0;bottom:-3px;height:8px;background:url("data:image/svg+xml,%3Csvg xmlns='http://www.w3.org/2000/svg' width='180' height='8'%3E%3Cpath d='M1 6 Q80 0 179 4' stroke='%23ffc857' stroke-width='4' fill='none' stroke-linecap='round'/%3E%3C/svg%3E") center/100% 100% no-repeat}h1+p{max-width:520px;margin-bottom:26px;color:var(--color-ink-soft);font-size:17px;line-height:1.8}.hero-search{max-width:570px;height:62px;padding:7px 7px 7px 18px;background:#fff;border:1px solid var(--color-line);border-radius:var(--radius-pill);box-shadow:var(--shadow-card);display:flex;align-items:center;gap:10px}.hero-search svg{color:var(--color-ink-soft)}.hero-search input{min-width:0;flex:1;border:0;outline:0}.hero-search button{height:48px;padding:0 20px;border:0;border-radius:var(--radius-pill);background:var(--color-primary);color:#fff;font-weight:800;display:flex;align-items:center;gap:7px}.trending{display:flex;flex-wrap:wrap;gap:12px;margin-top:17px;color:var(--color-ink-soft);font-size:12px}.trending b{color:var(--color-ink)}.trending a:hover{color:var(--color-primary)}.feed-head{display:flex;align-items:flex-end;justify-content:space-between;gap:18px}.feed-tabs{display:flex;gap:4px;padding:4px;border:1px solid var(--color-line);border-radius:var(--radius-pill);background:#fff}.feed-tabs button{border:0;border-radius:var(--radius-pill);background:transparent;padding:9px 14px;color:var(--color-ink-soft);font-size:12px;font-weight:800}.feed-tabs button.active{background:var(--color-ink);color:#fff}.hero-visual{position:relative;height:500px}.hero-visual>img{width:100%;height:100%;object-fit:cover;border-radius:120px 28px 120px 28px;box-shadow:var(--shadow-float)}.hero-label{position:absolute;right:18px;top:22px;padding:10px 14px;border-radius:var(--radius-pill);background:rgba(23,34,56,.76);backdrop-filter:blur(7px);color:#fff;font-size:12px;display:flex;gap:6px}.floating-card{position:absolute;padding:13px 16px;background:rgba(255,255,255,.93);backdrop-filter:blur(12px);border:1px solid rgba(255,255,255,.9);border-radius:16px;box-shadow:var(--shadow-card);display:flex;align-items:center;gap:11px}.floating-card div{display:flex;flex-direction:column}.floating-card small{color:var(--color-ink-soft);font-size:10px}.floating-card b{font-size:13px}.float-top{left:-36px;top:80px}.float-bottom{right:-30px;bottom:50px}.float-icon{width:36px;height:36px;display:grid!important;place-items:center;border-radius:10px;background:var(--color-primary-soft);color:var(--color-primary)}.avatars{flex-direction:row!important}.avatars img{width:30px;height:30px;border:2px solid #fff;border-radius:50%;margin-left:-8px}.avatars img:first-child{margin-left:0}.hero-orb{position:absolute;border-radius:50%;filter:blur(1px)}.orb-one{width:340px;height:340px;right:-100px;top:-100px;background:rgba(255,200,87,.18)}.orb-two{width:220px;height:220px;left:45%;bottom:-140px;background:rgba(34,184,167,.12)}.map-section{padding:62px 0;background:#edeae4}.map-feature{display:grid;grid-template-columns:.7fr 1.3fr;gap:55px;align-items:center}.map-copy h2{font-size:36px;line-height:1.2;letter-spacing:-.04em}.map-copy p{color:var(--color-ink-soft);line-height:1.8}.map-stats{display:flex;gap:40px;margin:26px 0}.map-stats div{display:flex;flex-direction:column}.map-stats b{font-size:27px}.map-stats span{color:var(--color-ink-soft);font-size:11px}.team-preview-grid{display:grid;grid-template-columns:repeat(3,1fr);gap:18px}.team-preview{position:relative;height:360px;overflow:hidden;border-radius:var(--radius-lg);color:#fff}.team-preview>img{width:100%;height:100%;object-fit:cover;transition:.4s}.team-preview:hover>img{transform:scale(1.05)}.team-shade{position:absolute;inset:0;background:linear-gradient(180deg,transparent 25%,rgba(10,17,28,.9))}.team-copy{position:absolute;left:22px;right:22px;bottom:22px}.team-copy h3{margin:8px 0;font-size:23px}.team-copy p{color:#e1e3e8;font-size:13px}.member-count{display:flex;align-items:center;gap:6px;font-size:12px}.ai-banner{position:relative;overflow:hidden;margin-top:36px;margin-bottom:90px;padding:48px 55px;border-radius:var(--radius-xl);background:linear-gradient(120deg,#6f4ad8,#4430a3);color:#fff;display:flex;align-items:center;justify-content:space-between}.ai-banner h2{margin:15px 0 10px;font-size:32px;line-height:1.3}.ai-banner p{margin:0;color:#dad2f6}.ai-badge{display:inline-flex;align-items:center;gap:6px;padding:7px 10px;border-radius:var(--radius-pill);background:rgba(255,255,255,.13);font-size:11px;font-weight:800}.ai-shine{position:absolute;width:350px;height:350px;right:10%;top:-280px;border:70px solid rgba(255,255,255,.08);border-radius:50%}
@media(max-width:900px){.hero-inner{grid-template-columns:1fr;gap:40px}.hero-visual{height:420px}.map-feature{grid-template-columns:1fr}.team-preview-grid{grid-template-columns:1fr 1fr}.team-preview:last-child{display:none}}@media(max-width:600px){.home-hero{padding:42px 0}.hero-search{height:56px}.hero-search button{font-size:0;width:45px;padding:0;justify-content:center}.hero-visual{height:330px}.float-top{left:-4px}.float-bottom{right:-4px}.team-preview-grid{grid-template-columns:1fr}.team-preview:last-child{display:block}.ai-banner{padding:34px 26px;align-items:flex-start;flex-direction:column;gap:25px}.ai-banner h2{font-size:26px}.map-copy h2{font-size:30px}}
</style>
