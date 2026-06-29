<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import { ArrowLeft, Building2, CheckCircle2, Eye, EyeOff, LockKeyhole, Mail, UserRound } from 'lucide-vue-next'

const router = useRouter()
const mode = ref<'login' | 'register'>('login')
const role = ref<'个人用户' | '商家用户'>('个人用户')
const email = ref('demo@quju.cn')
const password = ref('12345678')
const nickname = ref('')
const merchantName = ref('')
const license = ref('')
const showPassword = ref(false)
const submitted = ref(false)
const error = ref('')
const isRegister = computed(() => mode.value === 'register')

function submit() {
  error.value = ''
  if (!/^\S+@\S+\.\S+$/.test(email.value)) error.value = '请输入有效的邮箱地址。'
  else if (password.value.length < 8) error.value = '密码至少需要 8 位。'
  else if (isRegister.value && !nickname.value.trim()) error.value = '请输入全平台唯一昵称。'
  else if (isRegister.value && role.value === '商家用户' && (!merchantName.value.trim() || !license.value)) error.value = '商家注册需要填写商家名称并上传营业凭证。'
  if (error.value) return
  if (isRegister.value) submitted.value = true
  else { localStorage.setItem('quju:session', JSON.stringify({ email: email.value, role: '个人用户' })); router.push('/') }
}

function switchMode(next: 'login' | 'register') { mode.value = next; submitted.value = false; error.value = '' }
</script>

<template>
  <div class="auth-page">
    <RouterLink to="/" class="auth-back"><ArrowLeft :size="17" />返回趣聚</RouterLink>
    <section class="auth-side"><div class="brand-light"><span class="brand-mark"><span></span><span></span><span></span></span><b>趣聚</b></div><h1>兴趣不只停在<br />屏幕里面。</h1><p>找到活动，认识同频伙伴，把普通的一天变成值得记住的共同经历。</p><div class="auth-proof"><span>28,642<small>平台用户</small></span><span>1,284<small>本月活动</small></span><span>386<small>活跃小队</small></span></div></section>
    <main class="auth-card">
      <template v-if="!submitted">
        <div class="auth-tabs"><button :class="{active:mode==='login'}" @click="switchMode('login')">登录</button><button :class="{active:mode==='register'}" @click="switchMode('register')">注册</button></div>
        <div class="auth-title"><span>{{ isRegister ? '创建新账户' : '欢迎回来' }}</span><h2>{{ isRegister ? '从一场活动开始认识彼此' : '继续发现有趣的人和事' }}</h2></div>
        <div v-if="isRegister" class="role-switch"><button :class="{active:role==='个人用户'}" @click="role='个人用户'"><UserRound />个人用户</button><button :class="{active:role==='商家用户'}" @click="role='商家用户'"><Building2 />商家用户</button></div>
        <form @submit.prevent="submit">
          <label v-if="isRegister"><span>昵称 *</span><div><UserRound /><input v-model.trim="nickname" autocomplete="nickname" placeholder="全平台唯一" /></div></label>
          <label><span>邮箱 *</span><div><Mail /><input v-model.trim="email" type="email" autocomplete="email" placeholder="name@example.com" /></div></label>
          <label><span>密码 *</span><div><LockKeyhole /><input v-model="password" :type="showPassword?'text':'password'" :autocomplete="isRegister?'new-password':'current-password'" placeholder="至少 8 位" /><button type="button" @click="showPassword=!showPassword"><EyeOff v-if="showPassword" /><Eye v-else /></button></div></label>
          <template v-if="isRegister && role==='商家用户'"><label><span>商家名称 *</span><div><Building2 /><input v-model.trim="merchantName" placeholder="营业执照上的主体名称" /></div></label><label><span>营业执照 / 营业凭证 *</span><input class="file-input" type="file" accept="image/*,.pdf" @change="license=($event.target as HTMLInputElement).files?.[0]?.name||''" /><small>{{ license || '支持 JPG、PNG、PDF，提交后由后台人工审核' }}</small></label></template>
          <p v-if="error" class="auth-error">{{ error }}</p>
          <button class="auth-submit">{{ isRegister ? '注册并发送激活邮件' : '登录' }}</button>
        </form>
        <p class="admin-hint">管理员账号由系统预先创建，不提供公开注册。</p>
      </template>
      <template v-else><div class="activation"><CheckCircle2 /><h2>激活邮件已发送</h2><p>我们已向 <b>{{ email }}</b> 发送激活链接。激活后即可登录；商家资料会在激活后进入人工审核。</p><button class="auth-submit" @click="switchMode('login')">返回登录</button></div></template>
    </main>
  </div>
</template>

<style scoped>
.auth-page{min-height:100vh;padding:40px;display:grid;grid-template-columns:1fr minmax(420px,520px);align-items:center;gap:8vw;background:linear-gradient(135deg,#172238 0 46%,#f7f5f1 46%)}.auth-back{position:fixed;left:32px;top:25px;color:#c7ced9;display:flex;align-items:center;gap:6px;font-size:12px}.auth-side{max-width:520px;margin-left:8vw;color:#fff}.brand-light{display:flex;align-items:center;gap:12px;font-size:24px}.auth-side h1{margin:45px 0 18px;font-size:52px;line-height:1.15;letter-spacing:-.05em}.auth-side>p{max-width:440px;color:#b9c1cf;line-height:1.8}.auth-proof{display:flex;gap:45px;margin-top:55px}.auth-proof span{font-size:22px;font-weight:900}.auth-proof small{display:block;margin-top:5px;color:#8993a4;font-size:9px;font-weight:500}.auth-card{padding:42px;background:#fff;border:1px solid var(--color-line);border-radius:24px;box-shadow:var(--shadow-card)}.auth-tabs{padding:4px;border-radius:12px;background:var(--color-bg);display:flex}.auth-tabs button{flex:1;padding:10px;border:0;border-radius:9px;background:none;font-weight:800}.auth-tabs button.active{background:#fff;box-shadow:var(--shadow-soft);color:var(--color-primary)}.auth-title{margin:28px 0 20px}.auth-title>span{color:var(--color-primary);font-size:10px;font-weight:900;letter-spacing:.12em}.auth-title h2{margin:8px 0;font-size:24px}.role-switch{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:18px}.role-switch button{padding:12px;border:1px solid var(--color-line);border-radius:10px;background:#fff;display:flex;align-items:center;justify-content:center;gap:7px;font-size:11px}.role-switch svg{width:17px}.role-switch button.active{border-color:var(--color-primary);background:var(--color-primary-soft);color:var(--color-primary);font-weight:800}form label{display:block;margin-bottom:14px}form label>span{display:block;margin-bottom:6px;font-size:10px;font-weight:800}form label>div{padding:0 12px;border:1px solid var(--color-line);border-radius:10px;display:flex;align-items:center;gap:8px}form label>div:focus-within{border-color:var(--color-primary);box-shadow:0 0 0 3px var(--color-primary-soft)}form label svg{width:16px;color:var(--color-ink-soft)}form input:not(.file-input){min-width:0;flex:1;padding:12px 0;border:0;outline:0}form label button{border:0;background:none}.file-input{width:100%;padding:10px;border:1px dashed var(--color-line);border-radius:9px}.file-input+small{display:block;margin-top:5px;color:var(--color-ink-soft);font-size:8px}.auth-submit{width:100%;padding:13px;border:0;border-radius:10px;background:var(--color-primary);color:#fff;font-weight:900;box-shadow:0 8px 20px rgba(255,107,69,.2)}.auth-error{padding:9px;border-radius:8px;background:#ffeaed;color:var(--color-danger);font-size:10px}.admin-hint{margin:16px 0 0;text-align:center;color:var(--color-ink-soft);font-size:9px}.activation{text-align:center;padding:35px 10px}.activation>svg{width:65px;height:65px;color:var(--color-mint)}.activation p{margin:15px 0 28px;color:var(--color-ink-soft);font-size:12px;line-height:1.8}
.auth-page{grid-template-columns:minmax(380px,1fr) minmax(420px,520px);gap:7vw;background:linear-gradient(90deg,#172238 0 50%,#f7f5f1 50%)}.auth-side{max-width:440px;margin-left:5vw}.auth-side h1{font-size:46px}
@media(max-width:900px){.auth-page{padding:70px 20px 30px;grid-template-columns:1fr;background:linear-gradient(170deg,#172238 0 30%,#f7f5f1 30%)}.auth-side{margin:0;text-align:center}.auth-side h1{margin:20px 0;font-size:34px}.auth-side>p,.auth-proof{display:none}.brand-light{justify-content:center}.auth-card{width:min(100%,520px);margin:auto}}@media(max-width:500px){.auth-card{padding:28px 20px}.auth-page{padding-left:12px;padding-right:12px}.auth-back{left:18px}.role-switch{grid-template-columns:1fr}}
</style>
