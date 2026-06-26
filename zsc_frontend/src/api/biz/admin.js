import request from '@/utils/request'
import { getToken } from '@/utils/auth'

// 获取管理员统计数据
export function getAdminStats() {
  return request({ url: '/api/admin/stats', method: 'get' })
}

// 查询用户列表（含角色信息，分页）
export function listUser(query) {
  return request({ url: '/api/admin/users', method: 'get', params: query })
}

// 修改用户（角色分配）
export function updateUser(data) {
  return request({ url: '/system/user', method: 'put', data })
}

// 重置密码
export function resetUserPwd(userId, password) {
  return request({ url: '/system/user/resetPwd', method: 'put', data: { userId, password } })
}

// 修改用户状态
export function changeUserStatus(userId, status) {
  return request({ url: '/system/user/changeStatus', method: 'put', data: { userId, status } })
}

// 获取角色列表（用于下拉选择）
export function listRole() {
  return request({ url: '/api/admin/roles', method: 'get' })
}

// 获取待审核注册申请
export function listRegisterRequests() {
  return request({ url: '/api/admin/register-requests', method: 'get' })
}

// 通过注册申请
export function approveRegisterRequest(id, comment) {
  return request({ url: `/api/admin/register-requests/${id}/approve`, method: 'post', data: { comment } })
}

// 审核员工作量统计
export function getReviewerWorkload() {
  return request({ url: '/api/admin/reviewer-workload', method: 'get' })
}

// 普通用户已通过金额汇总
export function getUserAmountSummary() {
  return request({ url: '/api/admin/user-amount-summary', method: 'get' })
}

// 创建管理员账号（超管直接创建）
export function createAdmin(data) {
  return request({ url: '/api/admin/create-admin', method: 'post', data })
}

// 拒绝注册申请
export function rejectRegisterRequest(id, comment) {
  return request({ url: `/api/admin/register-requests/${id}/reject`, method: 'post', data: { comment } })
}

// ==================== AI 助手 SSE 流式对话 ====================

/**
 * 从一行数据中提取文本
 * 兼容多种后端输出格式:
 *   - "data: <content>"   SSE 标准包装
 *   - "<content>"          原始 Flux token
 * 忽略空行和控制行
 */
function extractLine(line) {
  const trimmed = line.trim()
  if (!trimmed) return ''

  // SSE "data:" 前缀 → 去壳
  if (trimmed.startsWith('data:')) {
    return trimmed.slice(5).trim()
  }

  // SSE 控制行 / JSON 噪音 → 跳过
  if (trimmed.startsWith('event:') ||
      trimmed.startsWith('id:') ||
      trimmed.startsWith('retry:') ||
      trimmed.startsWith(':') ||
      trimmed.startsWith('{') ||
      trimmed.startsWith('[')) {
    return ''
  }

  return trimmed
}

/**
 * AI 管理助手 SSE 流式对话
 *
 * 使用原生 fetch + ReadableStream 消费流式响应:
 *  - 支持自定义 Authorization 请求头 (EventSource 无法做到)
 *  - 支持 AbortController 中断
 *  - 兼容 Spring WebFlux 的 SSE 包装和原始 Flux<String> 两种输出格式
 *
 * @param {string} memoryId   会话ID，多轮对话复用
 * @param {string} message    用户输入的自然语言问题
 * @param {object} callbacks  { onToken(text), onDone(), onError(err) }
 * @param {AbortSignal} signal 用于中断请求
 */
export function aiChatStream(memoryId, message, { onToken, onDone, onError }, signal) {
  const token = getToken()
  const params = new URLSearchParams({memoryId, message})
  const baseUrl = import.meta.env.VITE_APP_BASE_API || '/dev-api'
  const url = `${baseUrl}/ai/admin/query?${params.toString()}`

  fetch(url, {
    method: 'GET',
    headers: {
      'Authorization': token ? `Bearer ${token}` : '',
      'Accept': 'text/event-stream'
    },
    signal
  }).then(response => {
    if (!response.ok) {
      const statusMessages = {
        401: '登录已过期，请重新登录',
        403: '无权限访问AI助手',
        500: 'AI服务内部错误'
      }
      throw new Error(statusMessages[response.status] || `请求失败 (${response.status})`)
    }
    if (!response.body) {
      throw new Error('浏览器不支持流式读取')
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder('utf-8')
    let buffer = ''
    let idleTimer = null
    let streamEnded = false
    const IDLE_TIMEOUT = 5000  // 5 秒无新数据则判定流结束

    function finish() {
      if (streamEnded) return
      streamEnded = true
      if (idleTimer) {
        clearTimeout(idleTimer)
        idleTimer = null
      }
      const remaining = extractLine(buffer)
      if (remaining) onToken(remaining)
      buffer = ''
      try {
        reader.cancel()
      } catch (_) { /* 忽略 */
      }
      onDone()
    }

    function pump() {
      // 每次 pump 先设空闲超时：3 秒后 reader.read() 仍未 resolve 则结束
      if (idleTimer) clearTimeout(idleTimer)
      idleTimer = setTimeout(() => {
        finish()
      }, IDLE_TIMEOUT)

      reader.read().then(({done, value}) => {
        // 收到数据，清除超时
        if (idleTimer) {
          clearTimeout(idleTimer)
          idleTimer = null
        }
        if (done) {
          finish()
          return
        }

        buffer += decoder.decode(value, {stream: true})

        // 切出完整的 SSE 事件（\n\n 分隔），保留最后一个不完整事件在 buffer
        const events = buffer.split('\n\n')
        buffer = events.pop() || ''

        for (const event of events) {
          const content = event
              .split('\n')
              .filter(l => l.startsWith('data:'))
              .map(l => l.slice(5))
              .join('\n')
          if (!content) continue
          // 空白事件 → 正文中的换行；否则原样发射
          onToken(content.trim() ? content : '\n')
        }

        pump()
      }).catch(err => {
        if (idleTimer) {
          clearTimeout(idleTimer)
          idleTimer = null
        }
        if (err.name === 'AbortError') return
        onError(err)
      })
    }

    pump()
  }).catch(err => {
    if (err.name === 'AbortError') return
    onError(err)
  })
}