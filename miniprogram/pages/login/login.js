const api = require('../../utils/api')
const storage = require('../../utils/storage')

Page({
  data: {
    email: '',
    password: '',
    loading: false,
  },

  onLoad() {
    if (storage.getToken()) wx.switchTab({ url: '/pages/home/home' })
  },

  onEmailInput(event) {
    this.setData({ email: event.detail.value })
  },

  onPasswordInput(event) {
    this.setData({ password: event.detail.value })
  },

  fillDemo() {
    this.setData({ email: 'demo@quju.cn', password: '12345678' })
  },

  async submitLogin(event) {
    const form = event.detail.value || {}
    const email = (form.email || this.data.email || '').trim()
    const password = form.password || this.data.password || ''
    if (!email || !password) {
      wx.showToast({ title: '请输入邮箱和密码', icon: 'none' })
      return
    }
    this.setData({ loading: true })
    try {
      const result = await api.post('/auth/login', { email, password, adminLogin: false })
      storage.setSession(result.token, result.user)
      getApp().refreshAuthState()
      wx.switchTab({ url: '/pages/home/home' })
    } catch (error) {
      wx.showToast({ title: error.message || '登录失败', icon: 'none' })
    } finally {
      this.setData({ loading: false })
    }
  },
})
