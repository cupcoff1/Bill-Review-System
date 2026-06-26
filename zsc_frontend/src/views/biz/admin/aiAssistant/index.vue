<template>
  <div class="app-container ai-assistant">
    <div class="chat-container">
      <!-- 顶部标题栏 -->
      <div class="chat-header">
        <div class="header-left">
          <el-icon :size="22" color="var(--el-color-primary)"><ChatDotRound /></el-icon>
          <span class="header-title">AI 管理助手</span>
          <el-tag size="small" type="info" effect="plain">内测版</el-tag>
        </div>
        <div class="header-right">
          <el-button size="small" icon="Delete" text @click="resetSession">新会话</el-button>
        </div>
      </div>

      <!-- 消息区域 -->
      <div ref="messagesContainer" class="chat-messages">
        <!-- 空状态：建议问题 -->
        <div v-if="messages.length === 0 && !isStreaming" class="empty-state">
          <div class="ai-avatar-large">
            <el-icon :size="48" color="var(--el-color-primary)"><ChatDotRound /></el-icon>
          </div>
          <h3>AI 管理助手</h3>
          <p>我可以帮您查询系统数据、分析审核员工作量、生成日报周报等</p>
          <div class="suggested-prompts">
            <el-tag
              v-for="item in suggestions"
              :key="item.text"
              class="prompt-tag"
              type="info"
              effect="plain"
              @click="sendMessage(item.text)"
            >
              {{ item.label }}
            </el-tag>
          </div>
        </div>

        <!-- 消息列表 -->
        <div
          v-for="msg in messages"
          :key="msg.id"
          class="message-item"
          :class="msg.role"
        >
          <div class="message-avatar">
            <el-icon v-if="msg.role === 'assistant'" :size="20" color="var(--el-color-primary)"><ChatDotRound /></el-icon>
            <el-icon v-else :size="20"><User /></el-icon>
          </div>
          <div class="message-body">
            <div class="message-bubble" :class="msg.role">
              <!-- AI 思考中：三点动画 -->
              <div v-if="msg.isStreaming && !msg.content" class="typing-dots">
                <span class="dot" />
                <span class="dot" />
                <span class="dot" />
              </div>
              <!-- 错误消息 -->
              <div v-else-if="msg.isError" class="message-error">{{ msg.content }}</div>
              <!-- AI Markdown 渲染（光标内嵌，不另起空行） -->
              <div v-else-if="msg.role === 'assistant'" class="message-text">
                <span class="markdown-body" v-html="renderMarkdown(msg.content, msg.isStreaming)"></span>
              </div>
              <!-- 用户消息（纯文本） -->
              <div v-else class="message-text">{{ msg.content }}</div>
            </div>
            <div class="message-time">{{ msg.time }}</div>
          </div>
        </div>

      </div>

      <!-- 底部输入区 -->
      <div class="chat-input-area">
        <el-input
          v-model="inputText"
          type="textarea"
          :rows="2"
          :disabled="isStreaming"
          placeholder="输入问题，例如：今天系统情况怎么样？"
          resize="none"
          @keydown.enter.exact="sendMessage()"
        />
        <div class="input-actions">
          <span class="input-hint" v-if="!isStreaming">Enter 发送，Shift+Enter 换行</span>
          <el-button
            v-if="!isStreaming"
            type="primary"
            :icon="inputText.trim() ? 'Promotion' : 'Position'"
            :disabled="!inputText.trim()"
            @click="sendMessage()"
          >
            发送
          </el-button>
          <el-button
            v-else
            type="danger"
            icon="VideoPause"
            @click="stopGeneration"
          >
            停止生成
          </el-button>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, onMounted, onUnmounted, nextTick } from 'vue'
import { ChatDotRound, User } from '@element-plus/icons-vue'
import { ElMessage } from 'element-plus'
import { marked } from 'marked'
import useSettingsStore from '@/store/modules/settings'
import { aiChatStream } from '@/api/biz/admin'

const settingsStore = useSettingsStore()
const isDark = computed(() => settingsStore.isDark)

// ==================== 会话管理 ====================
const memoryId = ref('')

function initMemoryId() {
  const saved = sessionStorage.getItem('ai_memoryId')
  if (saved) {
    memoryId.value = saved
  } else {
    memoryId.value = `admin_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`
    sessionStorage.setItem('ai_memoryId', memoryId.value)
  }
}

function resetSession() {
  messages.value = []
  memoryId.value = `admin_${Date.now()}_${Math.random().toString(36).slice(2, 9)}`
  sessionStorage.setItem('ai_memoryId', memoryId.value)
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
  isStreaming.value = false
  inputText.value = ''
}

// ==================== 消息状态 ====================
const messages = ref([])
const inputText = ref('')
const isStreaming = ref(false)
const abortController = ref(null)
const messagesContainer = ref(null)

// ==================== 建议问题 ====================
const suggestions = [
  { label: '今日系统概况', text: '今天系统情况怎么样？' },
  { label: '待审核票据', text: '目前有多少待审核的票据？' },
  { label: '审核员工作量', text: '审核员工作量排名' },
  { label: '用户管理', text: '列出所有用户' },
  { label: '类别管理', text: '列出所有类别' },
  { label: '热门提交人', text: '谁是提交票据最多的用户？' },
  { label: '本周统计', text: '生成本周系统数据摘要' },
  { label: '月度趋势', text: '本月票据提交趋势如何？' }
]

// ==================== 打字机效果队列 ====================
const TYPEWRITER_INTERVAL = 10  // 每个字符间隔 ms
let typewriterTimer = null
let pendingQueue = ''            // 待输出的字符队列

/** 启动打字机定时器（如已存在则跳过） */
function ensureTypewriter() {
  if (typewriterTimer) return
  typewriterTimer = setInterval(() => {
    if (pendingQueue.length === 0) {
      clearInterval(typewriterTimer)
      typewriterTimer = null
      return
    }
    // 每次取 1 个字符，中文单字宽
    const chunk = pendingQueue.slice(0, 1)
    pendingQueue = pendingQueue.slice(1)
    // 必须通过 messages.value 访问以保持 Vue 响应式
    const msg = messages.value.find(m => m.isStreaming)
    if (msg) {
      msg.content += chunk
    }
    scrollToBottom()
  }, TYPEWRITER_INTERVAL)
}

/** 清空打字机队列（停止/结束/出错时立即刷出剩余字符） */
function flushTypewriter() {
  if (typewriterTimer) {
    clearInterval(typewriterTimer)
    typewriterTimer = null
  }
  if (pendingQueue) {
    const msg = messages.value.find(m => m.isStreaming)
    if (msg) {
      msg.content += pendingQueue
    }
    pendingQueue = ''
    scrollToBottom()
  }
}

/** 销毁打字机定时器 */
function destroyTypewriter() {
  if (typewriterTimer) {
    clearInterval(typewriterTimer)
    typewriterTimer = null
  }
  pendingQueue = ''
}

// ==================== 发送消息 ====================
function sendMessage(presetText) {
  const text = (presetText || inputText.value).trim()
  if (!text || isStreaming.value) return

  if (!presetText) {
    inputText.value = ''
  }

  // 添加用户消息
  const userMsg = {
    id: `user_${Date.now()}`,
    role: 'user',
    content: text,
    time: formatTime(new Date())
  }
  messages.value.push(userMsg)

  // 创建 AI 占位消息
  const assistantMsg = {
    id: `assistant_${Date.now()}`,
    role: 'assistant',
    content: '',
    time: '',
    isStreaming: true,
    isError: false
  }
  messages.value.push(assistantMsg)
  pendingQueue = ''

  isStreaming.value = true

  // 创建 AbortController
  const controller = new AbortController()
  abortController.value = controller

  aiChatStream(
    memoryId.value,
    text,
    {
      onToken(token) {
        pendingQueue += token
        ensureTypewriter()
      },
      onDone() {
        flushTypewriter()
        const msg = messages.value.find(m => m.isStreaming)
        if (msg) {
          msg.content = msg.content.trimEnd()
          msg.isStreaming = false
          msg.time = formatTime(new Date())
        }
        isStreaming.value = false
        abortController.value = null
      },
      onError(err) {
        flushTypewriter()
        const msg = messages.value.find(m => m.isStreaming)
        if (msg) {
          msg.isStreaming = false
          msg.isError = true
          if (!msg.content) {
            msg.content = err.message || '请求失败，请稍后重试'
          } else {
            msg.content += '\n\n⚠️ 连接中断：' + (err.message || '未知错误')
          }
          msg.time = formatTime(new Date())
        }
        isStreaming.value = false
        abortController.value = null
        ElMessage.error(err.message || 'AI服务请求失败')
      }
    },
    controller.signal
  )

  scrollToBottom()
}

// ==================== 停止生成 ====================
function stopGeneration() {
  if (abortController.value) {
    abortController.value.abort()
    abortController.value = null
  }
  flushTypewriter()
  const msg = messages.value.find(m => m.isStreaming)
  if (msg) {
    msg.isStreaming = false
    if (msg.content) {
      msg.content += '\n\n[已停止生成]'
    } else {
      msg.content = '[已停止生成]'
    }
    msg.time = formatTime(new Date())
    msg.isError = true
  }
  isStreaming.value = false
}

// ==================== Markdown 渲染 ====================
marked.setOptions({
  breaks: true,
  gfm: true
})

function renderMarkdown(text, showCursor) {
  if (!text) return ''
  const safe = text.replace(/<script[^>]*>[\s\S]*?<\/script>/gi, '').trim()
  let html = marked.parse(safe)
    .replace(/(<br\s*\/?>\s*)+$/, '')
    .replace(/<p>\s*<\/p>\s*$/, '')
  if (showCursor) {
    html += '<span class="cursor">|</span>'
  }
  return html
}

// ==================== 自动滚动 ====================
function scrollToBottom() {
  nextTick(() => {
    const el = messagesContainer.value
    if (el) {
      el.scrollTop = el.scrollHeight
    }
  })
}

// ==================== 工具函数 ====================
function formatTime(date) {
  const h = String(date.getHours()).padStart(2, '0')
  const m = String(date.getMinutes()).padStart(2, '0')
  return `${h}:${m}`
}

// ==================== 生命周期 ====================
onMounted(() => {
  initMemoryId()
})

onUnmounted(() => {
  destroyTypewriter()
  if (abortController.value) {
    abortController.value.abort()
  }
})
</script>

<style scoped lang="scss">
.ai-assistant {
  height: calc(100vh - 84px);
  padding: 0;
}

.chat-container {
  display: flex;
  flex-direction: column;
  height: 100%;
  max-width: 900px;
  margin: 0 auto;
  background: var(--el-bg-color);
  border-radius: 8px;
  overflow: hidden;
  box-shadow: 0 1px 4px rgba(0, 0, 0, 0.06);
}

// ==================== 头部 ====================
.chat-header {
  display: flex;
  align-items: center;
  justify-content: space-between;
  padding: 14px 20px;
  border-bottom: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  flex-shrink: 0;

  .header-left {
    display: flex;
    align-items: center;
    gap: 8px;

    .header-title {
      font-size: 16px;
      font-weight: 600;
      color: var(--el-text-color-primary);
      margin-left: 4px;
    }
  }
}

// ==================== 消息区 ====================
.chat-messages {
  flex: 1;
  overflow-y: auto;
  padding: 20px;
  background: var(--el-fill-color-lighter);

  &::-webkit-scrollbar {
    width: 5px;
  }
  &::-webkit-scrollbar-thumb {
    background: var(--el-border-color);
    border-radius: 3px;
  }
}

// ==================== 空状态 ====================
.empty-state {
  display: flex;
  flex-direction: column;
  align-items: center;
  justify-content: center;
  height: 100%;
  text-align: center;
  padding: 40px 20px;

  .ai-avatar-large {
    width: 96px;
    height: 96px;
    border-radius: 50%;
    background: linear-gradient(135deg, #409eff 0%, #0066cc 100%);
    display: flex;
    align-items: center;
    justify-content: center;
    margin-bottom: 20px;

    .el-icon {
      color: #fff !important;
    }
  }

  h3 {
    font-size: 22px;
    color: var(--el-text-color-primary);
    margin: 0 0 8px;
  }

  p {
    font-size: 14px;
    color: var(--el-text-color-secondary);
    margin: 0 0 24px;
    line-height: 1.6;
  }

  .suggested-prompts {
    display: flex;
    flex-wrap: wrap;
    gap: 10px;
    justify-content: center;

    .prompt-tag {
      cursor: pointer;
      font-size: 13px;
      padding: 6px 14px;
      border-radius: 16px;
      transition: all 0.2s;

      &:hover {
        color: var(--el-color-primary);
        border-color: var(--el-color-primary);
        background: var(--el-color-primary-light-9);
      }
    }
  }
}

// ==================== 消息项 ====================
.message-item {
  display: flex;
  gap: 10px;
  margin-bottom: 18px;
  max-width: 85%;

  &.user {
    margin-left: auto;
    flex-direction: row-reverse;

    .message-bubble {
      background: var(--el-color-primary);
      color: #fff;
      border-bottom-right-radius: 4px;
    }

    .message-time {
      text-align: right;
    }
  }

  &.assistant {
    margin-right: auto;

    .message-bubble {
      background: var(--el-bg-color);
      color: var(--el-text-color-primary);
      border: 1px solid var(--el-border-color-light);
      border-bottom-left-radius: 4px;
    }

    .message-time {
      text-align: left;
    }
  }
}

.message-avatar {
  width: 36px;
  height: 36px;
  border-radius: 50%;
  display: flex;
  align-items: center;
  justify-content: center;
  background: var(--el-fill-color);
  flex-shrink: 0;
  margin-top: 2px;
}

.message-body {
  display: flex;
  flex-direction: column;
  gap: 4px;
  min-width: 0;
}

.message-bubble {
  padding: 10px 15px;
  border-radius: 16px;
  line-height: 1.65;
  font-size: 14px;
  word-break: break-word;
  position: relative;

  .streaming-cursor {
    display: inline;
    animation: blink 0.8s infinite;
    color: var(--el-color-primary);
    font-weight: bold;
  }

  &.user .streaming-cursor {
    color: #fff;
  }

  .message-error {
    color: var(--el-color-danger);
  }
}

.message-time {
  font-size: 12px;
  color: var(--el-text-color-placeholder);
  padding: 0 4px;
}

// 打字动画（AI 思考中）
.typing-dots {
  display: flex;
  align-items: center;
  gap: 5px;

  .dot {
    width: 8px;
    height: 8px;
    border-radius: 50%;
    background: var(--el-text-color-placeholder);
    animation: bounce 1.4s infinite both;

    &:nth-child(2) {
      animation-delay: 0.2s;
    }
    &:nth-child(3) {
      animation-delay: 0.4s;
    }
  }
}

// ==================== Markdown 内容样式 ====================
.message-item.assistant .markdown-body {
  :deep(h2) {
    font-size: 15px; font-weight: 600;
    margin: 14px 0 8px;
    padding-bottom: 4px;
    border-bottom: 1px solid var(--el-border-color-light);
    &:first-child { margin-top: 0; }
  }
  :deep(h3) {
    font-size: 14px; font-weight: 600;
    margin: 10px 0 6px;
  }
  :deep(strong) {
    font-weight: 600;
    color: var(--el-color-primary);
  }
  :deep(ul), :deep(ol) {
    margin: 4px 0; padding-left: 16px;
  }
  :deep(li) {
    margin: 2px 0; line-height: 1.5;
  }

  :deep(table) {
    width: 100%;
    margin: 10px 0;
    border-collapse: collapse;
    font-size: 13px;
    border-radius: 4px;
    overflow: hidden;
    box-shadow: 0 1px 3px rgba(0,0,0,0.06);
  }
  :deep(thead) {
    border-bottom: 2px solid var(--el-color-primary);
  }
  :deep(th) {
    background: var(--el-color-primary-light-9);
    color: var(--el-color-primary);
    font-weight: 600;
    padding: 8px 10px;
    text-align: left;
    font-size: 12px;
    white-space: nowrap;
  }
  :deep(td) {
    padding: 7px 10px;
    border-bottom: 1px solid var(--el-border-color-lighter);
    vertical-align: middle;
    line-height: 1.4;
  }
  :deep(tr:last-child td) {
    border-bottom: none;
  }
  :deep(tr:nth-child(even) td) {
    background: var(--el-fill-color-lighter);
  }
  :deep(tr:hover td) {
    background: var(--el-color-primary-light-9);
  }

  :deep(code) {
    background: var(--el-fill-color);
    padding: 1px 4px; border-radius: 3px;
    font-size: 12px; font-family: monospace;
  }
  :deep(pre) {
    background: var(--el-fill-color);
    padding: 8px 12px; border-radius: 5px;
    overflow-x: auto; margin: 6px 0;
    code { background: none; padding: 0; }
  }
  :deep(hr) {
    border: none; border-top: 1px solid var(--el-border-color-light);
    margin: 10px 0;
  }
  :deep(p) {
    margin: 4px 0;
    &:first-child { margin-top: 0; }
  }
  :deep(*:last-child) {
    margin-bottom: 0;  // 末尾不留白
  }
}

// ==================== 输入区 ====================
.chat-input-area {
  padding: 14px 20px;
  border-top: 1px solid var(--el-border-color-light);
  background: var(--el-bg-color);
  flex-shrink: 0;

  :deep(.el-textarea__inner) {
    border-radius: 8px;
    font-size: 14px;
    line-height: 1.6;
  }

  .input-actions {
    display: flex;
    align-items: center;
    justify-content: flex-end;
    gap: 12px;
    margin-top: 8px;

    .input-hint {
      font-size: 12px;
      color: var(--el-text-color-placeholder);
      margin-right: auto;
    }
  }
}

// ==================== 动画 ====================
@keyframes blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}

@keyframes bounce {
  0%, 80%, 100% {
    transform: scale(0.6);
    opacity: 0.4;
  }
  40% {
    transform: scale(1);
    opacity: 1;
  }
}

// ==================== 暗黑模式微调 ====================
html.dark {
  .chat-container {
    box-shadow: 0 1px 4px rgba(0, 0, 0, 0.2);
  }

  .message-item.assistant .message-bubble {
    background: var(--el-bg-color-overlay);
  }
}
</style>

<!-- 光标动画（非 scoped，v-html 动态插入的元素需要全局样式） -->
<style>
.markdown-body .cursor {
  display: inline;
  animation: ai-blink 0.8s infinite;
  color: var(--el-color-primary);
  font-weight: bold;
}
@keyframes ai-blink {
  0%, 50% { opacity: 1; }
  51%, 100% { opacity: 0; }
}
</style>
