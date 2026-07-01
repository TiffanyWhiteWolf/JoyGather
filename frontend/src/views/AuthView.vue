<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, Building2, CheckCircle2, Eye, EyeOff, LockKeyhole, Mail, UserRound } from 'lucide-vue-next'
import { apiGet, apiPost } from '@/lib/api'
import { useAppStore } from '@/stores/app'
import type { User } from '@/types'

const router = useRouter()
const route = useRoute()
const app = useAppStore()
const mode = ref<'login' | 'register'>('login')
const role = ref<'个人用户' | '商家用户'>('个人用户')
const email = ref('')
const password = ref('')
const confirmPassword = ref('')
const nickname = ref('')
const merchantName = ref('')
const license = ref('')
const showPassword = ref(false)
const licenseInput = ref<HTMLInputElement | null>(null)
const submitted = ref(false)
const error = ref('')
const isRegister = computed(() => mode.value === 'register')
const isAdminLogin = computed(() => route.query.role === 'admin' && !isRegister.value)

watch(isAdminLogin, () => { error.value = '' })

interface AuthResponse { token: string; user: User }
interface ActivationResponse { userId: string; status: string }

async function submit() {
  error.value = ''
  if (!/^\S+@\S+\.\S+$/.test(email.value)) error.value = '请输入有效的邮箱地址。'
  else if (password.value.length < 8) error.value = '邮箱或密码错误。'
  else if (isRegister.value && password.value !== confirmPassword.value) error.value = '两次密码不一致。'
  else if (isRegister.value && !nickname.value.trim()) error.value = '请输入全平台唯一昵称。'
  else if (isRegister.value && role.value === '商家用户' && (!merchantName.value.trim() || !license.value)) error.value = '商家注册需要填写商家名称并上传营业凭证。'
  if (error.value) return
  try {
    if (isRegister.value) {
      const result = await apiPost<ActivationResponse>('/auth/register', { email: email.value, password: password.value, confirmPassword: confirmPassword.value, nickname: nickname.value, role: role.value, merchantName: merchantName.value, licenseName: license.value })
      submitted.value = true
    } else {
      const result = await apiPost<AuthResponse>('/auth/login', { email: email.value, password: password.value, adminLogin: isAdminLogin.value })
      localStorage.setItem('quju:token', result.token)
      localStorage.setItem('quju:session', JSON.stringify(result.user))
      window.dispatchEvent(new CustomEvent('quju:auth-changed'))
      await app.refreshUserState()
      router.push(String(route.query.redirect || (result.user.role === '管理员' ? '/admin' : '/')))
    }
  } catch (err) {
    error.value = err instanceof Error ? err.message : '请求失败，请稍后重试。'
  }
}

function switchMode(next: 'login' | 'register') { mode.value = next; submitted.value = false; error.value = ''; confirmPassword.value = '' }

onMounted(async () => {
  const token = String(route.query.activate || '')
  if (!token) return
  try {
    await apiGet<User>(`/auth/activate?token=${token}`)
    error.value = '账号已激活，请登录。'
  } catch (err) {
    error.value = err instanceof Error ? err.message : '激活失败，请稍后重试。'
  }
})
</script>

<template>
  <div class="auth-page">
    <RouterLink to="/" class="auth-back"><ArrowLeft :size="17" />返回趣聚</RouterLink>
    <section class="auth-side"><div class="brand-light"><span class="brand-mark"><span></span><span></span><span></span></span><b>趣聚</b></div><h1>兴趣不只停在<br />屏幕里面。</h1><p>找到活动，认识同频伙伴，把普通的一天变成值得记住的共同经历。</p><div class="auth-proof"><span>28,642<small>平台用户</small></span><span>1,284<small>本月活动</small></span><span>386<small>活跃小队</small></span></div></section>
    <main class="auth-card">
      <template v-if="!submitted">
        <div class="auth-tabs"><button :class="{active:mode==='login'}" @click="switchMode('login')">登录</button><button v-if="!isAdminLogin" :class="{active:mode==='register'}" @click="switchMode('register')">注册</button></div>
        <div class="auth-title"><span>{{ isRegister ? '创建新账户' : isAdminLogin ? '管理员登录' : '欢迎回来' }}</span><h2>{{ isRegister ? '从一场活动开始认识彼此' : isAdminLogin ? '进入运营后台处理平台事务' : '继续发现有趣的人和事' }}</h2></div>
        <div v-if="isRegister" class="role-switch"><button :class="{active:role==='个人用户'}" @click="role='个人用户'"><UserRound />个人用户</button><button :class="{active:role==='商家用户'}" @click="role='商家用户'"><Building2 />商家用户</button></div>
        <form @submit.prevent="submit">
          <label v-if="isRegister"><span>昵称 *</span><div><UserRound /><input v-model.trim="nickname" autocomplete="nickname" placeholder="全平台唯一" /></div></label>
          <label><span>邮箱 *</span><div><Mail /><input v-model.trim="email" type="email" autocomplete="email" placeholder="name@example.com" /></div></label>
          <label><span>密码 *</span><div><LockKeyhole /><input v-model="password" :type="showPassword?'text':'password'" :autocomplete="isRegister?'new-password':'current-password'" placeholder="至少 8 位" /><button type="button" @click="showPassword=!showPassword"><EyeOff v-if="showPassword" /><Eye v-else /></button></div></label>
          <label v-if="isRegister"><span>确认密码 *</span><div><LockKeyhole /><input v-model="confirmPassword" :type="showPassword?'text':'password'" autocomplete="new-password" placeholder="再次输入密码" /></div></label>
          <template v-if="isRegister && role==='商家用户'"><label><span>商家名称 *</span><div><Building2 /><input v-model.trim="merchantName" placeholder="营业执照上的主体名称" /></div></label><label><span>营业执照 / 营业凭证 *</span><input ref="licenseInput" class="hidden-file" type="file" accept="image/*,.pdf" @change="license=($event.target as HTMLInputElement).files?.[0]?.name||''" /><div class="license-row"><button type="button" class="btn btn-outline btn-sm" @click="licenseInput?.click()"><Building2 :size="15" />{{ license || '选择文件' }}</button><small v-if="!license">支持 JPG、PNG、PDF，提交后由后台人工审核</small></div></label></template>
          <p v-if="error" class="auth-error">{{ error }}</p>
          <button class="auth-submit" type="submit">{{ isRegister ? '注册并发送激活邮件' : '登录' }}</button>
        </form>
        <p class="admin-hint"><RouterLink v-if="!isAdminLogin" to="/auth?role=admin">管理员登录</RouterLink><template v-else><span>管理员账号由系统预先创建，不提供公开注册。</span><RouterLink to="/auth" class="back-link">返回用户登录 / 注册</RouterLink></template></p>
      </template>
      <template v-else><div class="activation"><CheckCircle2 /><h2>激活邮件已发送</h2><p>我们已向 <b>{{ email }}</b> 发送激活链接。请查收邮件并点击链接完成激活后登录；商家资料会在激活后进入人工审核。</p><button class="auth-secondary" @click="switchMode('login')">返回登录</button></div></template>
    </main>
  </div>
</template>

<style scoped>
.auth-page{min-height:100vh;padding:40px;display:grid;grid-template-columns:1fr minmax(420px,520px);align-items:center;gap:8vw;background:linear-gradient(135deg,#172238 0 46%,#f7f5f1 46%)}.auth-back{position:fixed;z-index:10;left:32px;top:25px;color:#c7ced9;display:flex;align-items:center;gap:6px;font-size:12px}.auth-side{max-width:520px;margin-left:8vw;color:#fff}.brand-light{display:flex;align-items:center;gap:12px;font-size:24px}.auth-side h1{margin:45px 0 18px;font-size:52px;line-height:1.15;letter-spacing:-.05em}.auth-side>p{max-width:440px;color:#b9c1cf;line-height:1.8}.auth-proof{display:flex;gap:45px;margin-top:55px}.auth-proof span{font-size:22px;font-weight:900}.auth-proof small{display:block;margin-top:5px;color:#8993a4;font-size:9px;font-weight:500}.auth-card{padding:42px;background:#fff;border:1px solid var(--color-line);border-radius:24px;box-shadow:var(--shadow-card)}.auth-tabs{padding:4px;border-radius:12px;background:var(--color-bg);display:flex}.auth-tabs button{flex:1;padding:10px;border:0;border-radius:9px;background:none;font-weight:800;font-size:16px}.auth-tabs button.active{background:#fff;box-shadow:var(--shadow-soft);color:var(--color-primary)}.auth-title{margin:28px 0 20px}.auth-title>span{color:var(--color-primary);font-size:15px;font-weight:900;letter-spacing:.12em}.auth-title h2{margin:8px 0;font-size:28px}.role-switch{display:grid;grid-template-columns:1fr 1fr;gap:8px;margin-bottom:18px}.role-switch button{padding:12px;border:1px solid var(--color-line);border-radius:10px;background:#fff;display:flex;align-items:center;justify-content:center;gap:7px;font-size:13px}.role-switch svg{width:17px}.role-switch button.active{border-color:var(--color-primary);background:var(--color-primary-soft);color:var(--color-primary);font-weight:800}form label{display:block;margin-bottom:14px}form label>span{display:block;margin-bottom:6px;font-size:13px;font-weight:800}form label>div{padding:0 12px;border:1px solid var(--color-line);border-radius:10px;display:flex;align-items:center;gap:8px}form label>div:focus-within{border-color:var(--color-primary);box-shadow:0 0 0 3px var(--color-primary-soft)}form label svg{width:16px;color:var(--color-ink-soft)}form input:not(.file-input){min-width:0;flex:1;padding:12px 0;border:0;outline:0;font-size:14px}form label button{border:0;background:none}.file-input{width:100%;padding:10px;border:1px dashed var(--color-line);border-radius:9px}.file-input+small{display:block;margin-top:5px;color:var(--color-ink-soft);font-size:8px}.auth-submit{width:100%;padding:13px;border:0;border-radius:10px;background:var(--color-primary);color:#fff;font-weight:900;font-size:15px;box-shadow:0 8px 20px rgba(255,107,69,.2)}.auth-error{padding:9px;border-radius:8px;background:#ffeaed;color:var(--color-danger);font-size:10px}.admin-hint{margin:16px 0 0;text-align:center;color:var(--color-ink-soft);font-size:9px}.activation{text-align:center;padding:35px 10px}.activation>svg{width:65px;height:65px;color:var(--color-mint)}.activation p{margin:15px 0 28px;color:var(--color-ink-soft);font-size:12px;line-height:1.8}
.auth-secondary{width:100%;margin-top:10px;padding:12px;border:1px solid var(--color-line);border-radius:10px;background:#fff;color:var(--color-ink);font-weight:800}.admin-hint{font-size:13px}.admin-hint a{color:var(--color-primary);font-weight:800}.admin-hint .back-link{display:block;margin-top:6px}.auth-error{margin:14px 0;font-size:14px;line-height:1.6}
.auth-page{grid-template-columns:minmax(380px,1fr) minmax(420px,520px);gap:7vw;background:linear-gradient(90deg,#172238 0 50%,#f7f5f1 50%)}.auth-side{max-width:440px;margin-left:5vw}.auth-side h1{font-size:46px}
@media(max-width:900px){.auth-page{padding:70px 20px 30px;grid-template-columns:1fr;background:linear-gradient(170deg,#172238 0 30%,#f7f5f1 30%)}.auth-side{margin:0;text-align:center}.auth-side h1{margin:20px 0;font-size:34px}.auth-side>p,.auth-proof{display:none}.brand-light{justify-content:center}.auth-card{width:min(100%,520px);margin:auto}}@media(max-width:500px){.auth-card{padding:28px 20px}.auth-page{padding-left:12px;padding-right:12px}.auth-back{left:18px}.role-switch{grid-template-columns:1fr}}.hidden-file{display:none}.license-row{display:flex;align-items:center;gap:10px;margin-top:4px}.license-row .btn{white-space:nowrap}.license-row small{margin:0;color:var(--color-ink-soft);font-size:11px}
</style>
