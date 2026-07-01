const { API_BASE_URL } = require('./config')
const storage = require('./storage')

function buildUrl(baseUrl, path) {
  const base = String(baseUrl || '').replace(/\/+$/, '')
  const suffix = String(path || '').replace(/^\/+/, '')
  return `${base}/${suffix}`
}

function buildHeaders(token, extra) {
  const headers = {
    'Content-Type': 'application/json',
    ...(extra || {}),
  }
  if (token) headers.Authorization = `Bearer ${token}`
  return headers
}

function unwrapEnvelope(body) {
  if (!body || typeof body !== 'object') throw new Error('服务器返回格式异常')
  if (body.code !== 0 && body.code !== 200) throw new Error(body.message || '请求失败')
  return body.data
}

function request(path, options = {}) {
  const token = storage.getToken()
  const url = buildUrl(API_BASE_URL, path)
  console.info('[JoyGather API]', options.method || 'GET', url)
  return new Promise((resolve, reject) => {
    wx.request({
      url,
      method: options.method || 'GET',
      data: options.data,
      header: buildHeaders(token, options.header),
      success(res) {
        if (res.statusCode < 200 || res.statusCode >= 300) {
          const message = res.data && res.data.message ? res.data.message : `请求失败：${res.statusCode}`
          if (res.statusCode === 401) storage.clearSession()
          reject(new Error(message))
          return
        }
        try {
          resolve(unwrapEnvelope(res.data))
        } catch (error) {
          const message = error && error.message ? error.message : '请求失败'
          if (/请先登录|登录已过期|账号已被封禁|账号已注销/.test(message)) storage.clearSession()
          reject(new Error(message))
        }
      },
      fail(error) {
        reject(new Error(error.errMsg || '网络连接失败'))
      },
    })
  })
}

function get(path) {
  return request(path)
}

function post(path, data) {
  return request(path, { method: 'POST', data: data || {} })
}

function put(path, data) {
  return request(path, { method: 'PUT', data: data || {} })
}

function del(path, data) {
  return request(path, { method: 'DELETE', data })
}

module.exports = {
  buildUrl,
  buildHeaders,
  unwrapEnvelope,
  request,
  get,
  post,
  put,
  del,
}
