import axios from 'axios'

// ===================== 基础配置 =====================
// 修改 BASE_URL 以适配后端地址
const BASE_URL = '/api'

const http = axios.create({
  baseURL: BASE_URL,
  timeout: 30000,
})

function sanitizeApiErrorMessage(message, fallback = '请求失败，请稍后重试') {
  const text = String(message || '').trim()
  if (!text) return fallback
  const technicalPatterns = [
    /org\.postgresql/i,
    /PSQLException/i,
    /SQLException/i,
    /Error querying database/i,
    /nested exception/i,
    /java\./i,
    /AxiosError/i,
    /stack trace/i,
    /SELECT\s+.+\s+FROM/i,
    /relation ".+" does not exist/i,
    /JSON parse/i
  ]
  return technicalPatterns.some(pattern => pattern.test(text)) ? fallback : text
}

// 请求拦截器：自动附带 token
http.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// 响应拦截器：统一提取 data，处理 401
http.interceptors.response.use(
  (res) => res.data,
  (err) => {
    if (err.response?.status === 401 || err.response?.status === 403) {
      localStorage.removeItem('token')
      if (window.location.pathname !== '/login') {
        const redirect = `${window.location.pathname}${window.location.search}`
        window.location.href = `/login?redirect=${encodeURIComponent(redirect)}`
      }
    }
    return Promise.reject(sanitizeApiErrorMessage(err.response?.data?.message || err.message))
  }
)

// ===================== 认证接口 =====================
export const authApi = {
  // 登录：{ username, password } -> { token }
  login: (data) => http.post('/auth/login', data),
  // 注册：{ username, email, nickname, password, confirmPassword }
  register: (data) => http.post('/auth/register', data),
  // 登出：{}
  logout: () => http.post('/auth/logout'),
}

// ===================== 用户接口 =====================
export const userApi = {
  // 获取当前用户信息 -> { id, username, nickname, email, avatarType, avatarUrl, avatarPresetKey, avatarHistory }
  getProfile: () => http.get('/user/profile'),
  // 更新个人信息：{ username, nickname, email, avatarType?, avatarPresetKey?, avatarUrl?, avatarImageData? }
  updateProfile: (data) => http.put('/user/profile', data),
  // 删除当前用户上传过的头像：{ avatarUrl }
  deleteUploadedAvatar: (data) => http.delete('/user/avatars', { data }),
  // 修改密码：{ oldPassword, newPassword }
  changePassword: (data) => http.put('/user/password', data),
}

// ===================== 浏览板块接口 =====================
export const browseApi = {
  // 获取精选合约组列表 -> [{ id, name, description }]
  getGroups: () => http.get('/groups'),
  // 获取组内合约列表 -> [ContractCardVo]
  getGroupContracts: (groupId, params) => http.get(`/groups/${groupId}/contracts`, { params }),
}

// ===================== 搜索板块接口 =====================
export const searchApi = {
  /**
   * 搜索合约
   * @param {Object} data - { input, searchMode: 'semantic'|'code', limit }
   * @returns { data: SmartContractSearchReturnVo }
   */
  searchContracts: (data) => http.post('/search', data),
}

// ===================== AI Chat 接口 =====================
export const aiApi = {
  chat: (data) => http.post('/ai/chat', data, { timeout: 60000 }),
}

// ===================== 新建对话接口 =====================
export const conversationApi = {
  chat: (data) => http.post('/conversation/chat', data, { timeout: 60000 }),
  getSessions: () => http.get('/conversation/sessions'),
  getSession: (id) => http.get(`/conversation/sessions/${id}`),
  deleteSession: (id) => http.delete(`/conversation/sessions/${id}`),
  parseAttachment: (data) => http.post('/conversation/attachments/parse', data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  }),
}

// ===================== Agent Workspace API（默认由后端 feature flag 关闭） =====================
export const agentApi = {
  createSession: (data) => http.post('/agent/v1/sessions', data),
  listSessions: () => http.get('/agent/v1/sessions'),
  getSession: (id) => http.get(`/agent/v1/sessions/${id}`),
  getSessionRuns: (id) => http.get(`/agent/v1/sessions/${id}/runs`),
  deleteSession: (id) => http.delete(`/agent/v1/sessions/${id}`),
  createRun: (data) => http.post('/agent/v1/runs', data, { timeout: 30000 }),
  getRun: (id) => http.get(`/agent/v1/runs/${id}`),
  getEvidence: (id) => http.get(`/agent/v1/runs/${id}/evidence`),
  getProposals: (id) => http.get(`/agent/v1/runs/${id}/proposals`),

  /**
   * Authenticated SSE client. Native EventSource is intentionally not used
   * because it cannot attach the existing bearer token.
   */
  streamEvents: async (runId, { after = 0, signal, onEvent } = {}) => {
    const token = localStorage.getItem('token')
    const response = await fetch(`/api/agent/v1/runs/${runId}/events?after=${after}`, {
      headers: token ? { Authorization: `Bearer ${token}` } : {},
      signal,
    })
    if (!response.ok || !response.body) {
      throw new Error(response.status === 401 ? '登录已失效' : 'Agent 事件流连接失败')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''
    while (true) {
      const { done, value } = await reader.read()
      if (done) break
      buffer += decoder.decode(value, { stream: true })
      const frames = buffer.split(/\r?\n\r?\n/)
      buffer = frames.pop() || ''
      for (const frame of frames) {
        const parsed = parseSseFrame(frame)
        if (parsed && onEvent) onEvent(parsed)
      }
    }
  },
}

function parseSseFrame(frame) {
  const result = { id: null, event: 'message', data: null }
  const data = []
  for (const line of frame.split(/\r?\n/)) {
    if (line.startsWith('id:')) result.id = line.slice(3).trim()
    else if (line.startsWith('event:')) result.event = line.slice(6).trim()
    else if (line.startsWith('data:')) data.push(line.slice(5).trimStart())
  }
  if (!data.length) return null
  const raw = data.join('\n')
  try {
    result.data = JSON.parse(raw)
  } catch {
    result.data = raw
  }
  return result
}

// ===================== 合约详情接口 =====================
export const contractApi = {
  getAllContracts: () => http.get('/contracts'),
  getContractDetail: (id) => http.get(`/contracts/${id}`),
  favoriteContract: (id, data = {}) => http.put(`/contracts/${id}/favorite`, data),
  unfavoriteContract: (id) => http.delete(`/contracts/${id}/favorite`),
  getFavorites: () => http.get(`/contracts/favorites`),
  getFavoriteFolders: () => http.get(`/contracts/favorite-folders`),
  createFavoriteFolder: (data) => http.post(`/contracts/favorite-folders`, data),
  renameFavoriteFolder: (id, data) => http.put(`/contracts/favorite-folders/${id}`, data),
  deleteFavoriteFolder: (id) => http.delete(`/contracts/favorite-folders/${id}`),
  getFavoriteFolderContracts: (id) => http.get(`/contracts/favorite-folders/${id}/contracts`),
  uploadReviewedContract: (data) => http.post(`/contracts/uploads`, data),
  getMyUploadedContracts: () => http.get(`/contracts/uploads/mine`),
  deleteUploadedContract: (id) => http.delete(`/contracts/uploads/${id}`),
  saveDraftContract: (data) => http.post(`/contracts/drafts`, data),
  getDraftContracts: () => http.get(`/contracts/drafts`),
  getDraftContract: (id) => http.get(`/contracts/drafts/${id}`),
  deleteDraftContract: (id) => http.delete(`/contracts/drafts/${id}`),
  submitContractForReview: (data) => http.post(`/contracts/submissions`, data),
  getContractSubmissions: () => http.get(`/contracts/submissions`),
  getContractSubmission: (id) => http.get(`/contracts/submissions/${id}`),
  withdrawContractSubmission: (id) => http.delete(`/contracts/submissions/${id}`),
  checkContract: (data) => http.post(`/contracts/check`, data, { timeout: 60000 }),
  checkContractFile: (data) => http.post(`/contracts/check/upload`, data, {
    headers: { 'Content-Type': 'multipart/form-data' },
    timeout: 60000,
  }),
}
