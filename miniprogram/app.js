const storage = require('./utils/storage')

App({
  globalData: {
    token: '',
    user: null,
  },

  onLaunch() {
    this.refreshAuthState()
  },

  refreshAuthState() {
    this.globalData.token = storage.getToken()
    this.globalData.user = storage.getUser()
  },
})
