<script setup>
import { computed, nextTick, onBeforeUnmount, onMounted, reactive, ref } from 'vue'

const input = ref('')
const composerInput = ref(null)
const fileInput = ref(null)
const loading = ref(false)
const error = ref('')
const conversations = ref([])
const activeConversationId = ref('')
const chatMain = ref(null)
const deepThinking = ref(false)
const webSearch = ref(false)
const openMenuId = ref('')
const menuPosition = ref({ top: 0, left: 0 })
const accountMenuOpen = ref(false)
const settingsOpen = ref(false)
const copiedMessageId = ref('')
const tokenQueue = ref([])
const typewriterTimer = ref(null)
const typewriterResolve = ref(null)
const attachments = ref([])
const dragActive = ref(false)
const expandedThoughts = reactive({})

const maxAttachments = 8
const maxImageSize = 5 * 1024 * 1024
const maxTextSize = 200 * 1024
const textExtensions = new Set([
  'txt',
  'md',
  'java',
  'js',
  'ts',
  'vue',
  'json',
  'xml',
  'yml',
  'yaml',
  'sql',
  'css',
  'html',
  'properties',
  'env',
  'py',
])

const activeConversation = computed(() =>
  conversations.value.find((conversation) => conversation.id === activeConversationId.value)
)

const activeMenuConversation = computed(() =>
  conversations.value.find((conversation) => conversation.id === openMenuId.value)
)

const messages = computed(() => activeConversation.value?.messages || [])
const hasMessages = computed(() => messages.value.length > 0)
const canSend = computed(() => input.value.trim().length > 0 || attachments.value.length > 0)

const activeModeText = computed(() => {
  if (deepThinking.value && webSearch.value) return '深度思考 · 联网搜索'
  if (deepThinking.value) return '深度思考'
  if (webSearch.value) return '联网搜索'
  return '快速模式'
})

const groupedConversations = computed(() => {
  const groups = [
    { key: 'pinned', label: '置顶', items: [] },
    { key: 'today', label: '今天', items: [] },
    { key: 'week', label: '7 天内', items: [] },
    { key: 'month', label: '30 天内', items: [] },
  ]
  const now = Date.now()
  const day = 24 * 60 * 60 * 1000

  conversations.value.forEach((conversation) => {
    if (conversation.pinned) {
      groups[0].items.push(conversation)
      return
    }

    const age = now - conversation.updatedAt
    if (age < day) groups[1].items.push(conversation)
    else if (age < 7 * day) groups[2].items.push(conversation)
    else groups[3].items.push(conversation)
  })

  return groups.filter((group) => group.items.length > 0)
})

onMounted(async () => {
  await loadConversations()
  const params = new URLSearchParams(window.location.search)
  const sharedId = params.get('conversationId')
  if (sharedId && conversations.value.some((conversation) => conversation.id === sharedId)) {
    activeConversationId.value = sharedId
  }
})

onBeforeUnmount(() => {
  stopTypewriter()
})

async function apiRequest(url, options = {}) {
  const response = await fetch(url, {
    credentials: 'include',
    headers: {
      'Content-Type': 'application/json',
      ...(options.headers || {}),
    },
    ...options,
  })

  if (response.status === 204) return null

  const text = await response.text()
  const data = text ? JSON.parse(text) : null
  if (!response.ok) {
    throw new Error(data?.error || data?.message || '请求失败')
  }
  return data
}

async function loadConversations() {
  error.value = ''
  try {
    conversations.value = await apiRequest('/api/conversations')
    if (conversations.value.length === 0) {
      const conversation = await apiRequest('/api/conversations', { method: 'POST' })
      conversations.value = [conversation]
    }
    if (!activeConversationId.value || !conversations.value.some((item) => item.id === activeConversationId.value)) {
      activeConversationId.value = conversations.value[0]?.id || ''
    }
    scrollToBottom()
  } catch (err) {
    error.value = err.message || '加载会话失败'
  }
}

async function createConversation() {
  error.value = ''
  try {
    const conversation = await apiRequest('/api/conversations', { method: 'POST' })
    conversations.value.unshift(conversation)
    activeConversationId.value = conversation.id
    input.value = ''
    openMenuId.value = ''
    scrollToBottom()
  } catch (err) {
    error.value = err.message || '创建会话失败'
  }
}

function selectConversation(id) {
  activeConversationId.value = id
  input.value = ''
  error.value = ''
  openMenuId.value = ''
  scrollToBottom()
}

function upsertConversation(updated) {
  const index = conversations.value.findIndex((conversation) => conversation.id === updated.id)
  if (index === -1) {
    conversations.value.unshift(updated)
  } else {
    conversations.value[index] = updated
  }
}

async function refreshActiveConversation() {
  if (!activeConversationId.value) return
  const conversation = await apiRequest(`/api/conversations/${activeConversationId.value}`)
  upsertConversation(conversation)
}

async function sendMessage() {
  const content = input.value.trim()
  if (!canSend.value || loading.value || !activeConversation.value) return

  error.value = ''
  input.value = ''
  const sendingAttachments = attachments.value.map(({ id, previewUrl, ...attachment }) => attachment)
  const displayContent = buildDisplayContent(content, sendingAttachments)
  attachments.value = []
  loading.value = true
  tokenQueue.value = []
  stopTypewriter()

  const userMessage = {
    id: `local-user-${Date.now()}`,
    role: 'user',
    content: displayContent,
    timestamp: Date.now(),
  }
  const assistantMessage = {
    id: `local-assistant-${Date.now()}`,
    role: 'assistant',
    content: '',
    timestamp: Date.now(),
    streaming: true,
  }

  expandedThoughts[assistantMessage.id] = true
  activeConversation.value.messages.push(userMessage, assistantMessage)
  scrollToBottom()

  try {
    await streamChat(content, sendingAttachments, assistantMessage)
    await waitForTokenQueue()
    assistantMessage.streaming = false
    await refreshActiveConversation()
  } catch (err) {
    assistantMessage.streaming = false
    error.value = err.message || 'AI暂时不在家'
    await refreshActiveConversation().catch(() => {})
  } finally {
    loading.value = false
    scrollToBottom()
  }
}

async function streamChat(content, sendingAttachments, assistantMessage) {
  const response = await fetch('/api/chat/stream', {
    method: 'POST',
    credentials: 'include',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({
      message: content,
      conversationId: activeConversationId.value,
      deepThinking: deepThinking.value,
      webSearch: webSearch.value,
      attachments: sendingAttachments,
    }),
  })

  if (!response.ok || !response.body) {
    const data = await response.json().catch(() => null)
    throw new Error(data?.error || '请求失败')
  }

  const reader = response.body.getReader()
  const decoder = new TextDecoder('utf-8')
  let buffer = ''

  while (true) {
    const { value, done } = await reader.read()
    if (done) break
    buffer += decoder.decode(value, { stream: true })
    const parsed = extractSseEvents(buffer)
    buffer = parsed.remaining
    for (const eventText of parsed.events) {
      handleSseEvent(eventText, assistantMessage)
    }
  }

  if (buffer.trim()) {
    handleSseEvent(buffer, assistantMessage)
  }
}

function extractSseEvents(buffer) {
  const events = []
  let remaining = buffer

  while (true) {
    const lfIndex = remaining.indexOf('\n\n')
    const crlfIndex = remaining.indexOf('\r\n\r\n')
    const indexes = [lfIndex, crlfIndex].filter((index) => index >= 0)
    if (indexes.length === 0) break

    const eventEnd = Math.min(...indexes)
    const separatorLength = remaining.startsWith('\r\n\r\n', eventEnd) ? 4 : 2
    events.push(remaining.slice(0, eventEnd))
    remaining = remaining.slice(eventEnd + separatorLength)
  }

  return { events, remaining }
}

function handleSseEvent(eventText, assistantMessage) {
  const lines = eventText.replace(/\r\n/g, '\n').split('\n')
  const eventName = lines.find((line) => line.startsWith('event:'))?.slice(6).trim()
  const data = lines
    .filter((line) => line.startsWith('data:'))
    .map((line) => line.slice(5).trimStart())
    .join('\n')

  if (eventName === 'token') {
    enqueueTokens(data, assistantMessage)
  }
  if (eventName === 'error') {
    throw new Error(data || 'AI调用失败')
  }
}

function enqueueTokens(text, assistantMessage) {
  if (!text) return
  tokenQueue.value.push(...Array.from(text))
  startTypewriter(assistantMessage)
}

function startTypewriter(assistantMessage) {
  if (typewriterTimer.value) return

  typewriterTimer.value = window.setInterval(() => {
    const chunk = tokenQueue.value.splice(0, 2).join('')
    if (chunk) {
      assistantMessage.content += chunk
      scrollToBottom('auto')
    }

    if (tokenQueue.value.length === 0) {
      stopTypewriter()
      if (typewriterResolve.value) {
        typewriterResolve.value()
        typewriterResolve.value = null
      }
    }
  }, 20)
}

function stopTypewriter() {
  if (typewriterTimer.value) {
    window.clearInterval(typewriterTimer.value)
    typewriterTimer.value = null
  }
}

function waitForTokenQueue() {
  if (tokenQueue.value.length === 0 && !typewriterTimer.value) {
    return Promise.resolve()
  }
  return new Promise((resolve) => {
    typewriterResolve.value = resolve
  })
}

async function clearMemory() {
  if (!activeConversation.value) return
  error.value = ''
  try {
    await apiRequest('/api/chat/memory', {
      method: 'DELETE',
      body: JSON.stringify({ conversationId: activeConversationId.value }),
    })
    await refreshActiveConversation()
  } catch (err) {
    error.value = err.message || '清空记忆失败'
  } finally {
    scrollToBottom()
  }
}

function openConversationMenu(conversation, event) {
  const rect = event.currentTarget.getBoundingClientRect()
  accountMenuOpen.value = false
  openMenuId.value = openMenuId.value === conversation.id ? '' : conversation.id
  menuPosition.value = {
    top: rect.bottom + 6,
    left: Math.max(12, rect.right - 156),
  }
}

async function renameConversation(conversation) {
  openMenuId.value = ''
  const title = window.prompt('请输入新的会话标题', conversation.title)
  if (title === null) return
  try {
    const updated = await apiRequest(`/api/conversations/${conversation.id}/title`, {
      method: 'PATCH',
      body: JSON.stringify({ title }),
    })
    upsertConversation(updated)
  } catch (err) {
    error.value = err.message || '重命名失败'
  }
}

async function togglePin(conversation) {
  openMenuId.value = ''
  try {
    const updated = await apiRequest(`/api/conversations/${conversation.id}/pin`, {
      method: 'PATCH',
      body: JSON.stringify({ pinned: !conversation.pinned }),
    })
    upsertConversation(updated)
  } catch (err) {
    error.value = err.message || '置顶失败'
  }
}

async function deleteConversation(conversation) {
  openMenuId.value = ''
  if (!window.confirm(`确定删除“${conversation.title}”吗？`)) return
  try {
    await apiRequest(`/api/conversations/${conversation.id}`, { method: 'DELETE' })
    conversations.value = conversations.value.filter((item) => item.id !== conversation.id)
    if (activeConversationId.value === conversation.id) {
      if (conversations.value.length === 0) {
        await createConversation()
      } else {
        activeConversationId.value = conversations.value[0].id
      }
    }
  } catch (err) {
    error.value = err.message || '删除失败'
  }
}

async function copyMessage(message) {
  try {
    await navigator.clipboard.writeText(message.content)
    copiedMessageId.value = message.id
    setTimeout(() => {
      if (copiedMessageId.value === message.id) copiedMessageId.value = ''
    }, 1600)
  } catch {
    error.value = '复制失败'
  }
}

function editUserMessage(message) {
  input.value = message.content.replace(/\n?\[附件：.+?]/g, '').trim()
  nextTick(() => {
    composerInput.value?.focus()
  })
}

function buildDisplayContent(content, sendingAttachments) {
  const lines = []
  if (content) lines.push(content)
  sendingAttachments.forEach((attachment) => {
    lines.push(`[附件：${attachment.name}]`)
  })
  return lines.join('\n')
}

function triggerFilePicker() {
  if (loading.value) return
  fileInput.value?.click()
}

async function handleFileChange(event) {
  await addFiles(event.target.files)
  event.target.value = ''
}

async function handleDrop(event) {
  dragActive.value = false
  await addFiles(event.dataTransfer?.files)
}

async function handlePaste(event) {
  const files = Array.from(event.clipboardData?.files || [])
  if (files.length === 0) return
  event.preventDefault()
  await addFiles(files)
}

async function addFiles(fileList) {
  const files = Array.from(fileList || [])
  if (files.length === 0) return

  for (const file of files) {
    if (attachments.value.length >= maxAttachments) {
      error.value = '一次最多上传 8 个附件'
      break
    }

    try {
      attachments.value.push(await normalizeFile(file))
      error.value = ''
    } catch (err) {
      error.value = err.message || '附件读取失败'
    }
  }
}

async function normalizeFile(file) {
  const name = file.name || '未命名附件'
  if (isImageFile(file)) {
    if (file.size > maxImageSize) throw new Error('单张图片不能超过 5MB')
    const data = await readAsDataUrl(file)
    return {
      id: `${Date.now()}-${Math.random()}`,
      name,
      type: 'image',
      mimeType: file.type || 'image/png',
      data,
      size: file.size,
      previewUrl: data,
    }
  }

  if (isTextFile(file)) {
    if (file.size > maxTextSize) throw new Error('单个文本文件不能超过 200KB')
    const data = await readAsText(file)
    return {
      id: `${Date.now()}-${Math.random()}`,
      name,
      type: 'text',
      mimeType: file.type || guessTextMimeType(name),
      data,
      size: file.size,
    }
  }

  throw new Error(`暂不支持 ${name}，请上传图片或文本/代码文件`)
}

function removeAttachment(id) {
  attachments.value = attachments.value.filter((attachment) => attachment.id !== id)
}

function isImageFile(file) {
  return ['image/png', 'image/jpeg', 'image/webp', 'image/gif'].includes(file.type)
}

function isTextFile(file) {
  if (file.type?.startsWith('text/')) return true
  const extension = file.name.split('.').pop()?.toLowerCase()
  return textExtensions.has(extension)
}

function guessTextMimeType(name) {
  const extension = name.split('.').pop()?.toLowerCase()
  if (extension === 'json') return 'application/json'
  if (extension === 'xml') return 'application/xml'
  return 'text/plain'
}

function readAsDataUrl(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(new Error('图片读取失败'))
    reader.readAsDataURL(file)
  })
}

function readAsText(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = () => resolve(reader.result)
    reader.onerror = () => reject(new Error('文件读取失败'))
    reader.readAsText(file, 'UTF-8')
  })
}

function formatFileSize(size) {
  if (size < 1024) return `${size} B`
  if (size < 1024 * 1024) return `${(size / 1024).toFixed(1)} KB`
  return `${(size / 1024 / 1024).toFixed(1)} MB`
}

function toggleAccountMenu() {
  openMenuId.value = ''
  accountMenuOpen.value = !accountMenuOpen.value
}

function openSettings() {
  accountMenuOpen.value = false
  settingsOpen.value = true
}

function splitAssistantContent(message) {
  if (message.role !== 'assistant') {
    return { thinking: '', answer: message.content }
  }

  const content = message.content || ''
  const answerMarkers = ['正式回答：', '正式回答:', '回答：', '回答:']
  const thinkingMarkers = ['思路摘要：', '思路摘要:', '思考过程：', '思考过程:']
  const answerIndex = answerMarkers
    .map((marker) => ({ marker, index: content.indexOf(marker) }))
    .filter((item) => item.index >= 0)
    .sort((a, b) => a.index - b.index)[0]

  if (!answerIndex) return { thinking: '', answer: content }

  let thinking = content.slice(0, answerIndex.index).trim()
  for (const marker of thinkingMarkers) {
    if (thinking.startsWith(marker)) thinking = thinking.slice(marker.length).trim()
  }

  const answer = content.slice(answerIndex.index + answerIndex.marker.length).trim()
  return { thinking, answer: answer || content.slice(answerIndex.index).trim() }
}

function escapeHtml(text) {
  return String(text || '')
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;')
}

function renderInlineMarkdown(text) {
  return escapeHtml(text)
    .replace(/\*\*([^*]+)\*\*/g, '<strong>$1</strong>')
    .replace(/`([^`]+)`/g, '<code>$1</code>')
    .replace(
      /(https?:\/\/[^\s<]+)/g,
      '<a href="$1" target="_blank" rel="noopener noreferrer">$1</a>'
    )
}

function renderRichText(text) {
  const lines = String(text || '').split(/\r?\n/)
  const html = []
  let listType = ''
  let paragraph = []

  const closeParagraph = () => {
    if (paragraph.length === 0) return
    html.push(`<p>${paragraph.map(renderInlineMarkdown).join('<br>')}</p>`)
    paragraph = []
  }

  const closeList = () => {
    if (!listType) return
    html.push(`</${listType}>`)
    listType = ''
  }

  const openList = (type) => {
    if (listType === type) return
    closeParagraph()
    closeList()
    listType = type
    html.push(`<${type}>`)
  }

  lines.forEach((rawLine) => {
    const line = rawLine.trim()

    if (!line) {
      closeParagraph()
      closeList()
      return
    }

    const heading = line.match(/^#{1,6}\s+(.+)$/)
    if (heading) {
      closeParagraph()
      closeList()
      html.push(`<h3>${renderInlineMarkdown(heading[1])}</h3>`)
      return
    }

    const unordered = line.match(/^[-*]\s+(.+)$/)
    if (unordered) {
      openList('ul')
      html.push(`<li>${renderInlineMarkdown(unordered[1])}</li>`)
      return
    }

    const ordered = line.match(/^\d+[.、]\s+(.+)$/)
    if (ordered) {
      openList('ol')
      html.push(`<li>${renderInlineMarkdown(ordered[1])}</li>`)
      return
    }

    closeList()
    paragraph.push(line)
  })

  closeParagraph()
  closeList()

  return html.join('')
}

function toggleThought(messageId) {
  expandedThoughts[messageId] = !expandedThoughts[messageId]
}

function formatTime(timestamp) {
  return new Date(timestamp).toLocaleTimeString('zh-CN', {
    hour: '2-digit',
    minute: '2-digit',
  })
}

function scrollToBottom(behavior = 'smooth') {
  nextTick(() => {
    if (chatMain.value) {
      chatMain.value.scrollTo({
        top: chatMain.value.scrollHeight,
        behavior,
      })
    }
  })
}

function handleKeydown(event) {
  if (event.key === 'Enter' && !event.shiftKey) {
    event.preventDefault()
    sendMessage()
  }
}

function handleComposerWheel(event) {
  const target = event.target
  const textarea = composerInput.value
  const textareaCanScroll =
    textarea &&
    target === textarea &&
    textarea.scrollHeight > textarea.clientHeight &&
    ((event.deltaY < 0 && textarea.scrollTop > 0) ||
      (event.deltaY > 0 && textarea.scrollTop + textarea.clientHeight < textarea.scrollHeight))

  if (textareaCanScroll || !chatMain.value) return

  event.preventDefault()
  chatMain.value.scrollTop += event.deltaY
}
</script>

<template>
  <div class="app-shell" @click="openMenuId = ''; accountMenuOpen = false">
    <aside class="sidebar" aria-label="会话侧边栏">
      <div class="brand-row">
        <div class="brand-mark" aria-hidden="true">
          <svg viewBox="0 0 24 24" role="img">
            <path d="M12.2 2.5c4.9 0 8.9 3.6 8.9 8.1 0 2.6-1.4 5-3.6 6.5.2 1.2.8 2.3 1.8 3.1-1.9.2-3.6-.3-5-1.4-.7.1-1.4.2-2.1.2-4.9 0-8.9-3.6-8.9-8.1s4-8.4 8.9-8.4Zm-3 8.1c.7 0 1.2-.5 1.2-1.1s-.5-1.1-1.2-1.1S8 8.9 8 9.5s.5 1.1 1.2 1.1Zm5.8 0c.7 0 1.2-.5 1.2-1.1s-.5-1.1-1.2-1.1-1.2.5-1.2 1.1.5 1.1 1.2 1.1Z" />
          </svg>
        </div>
        <span>CodeMate AI</span>
        <button class="icon-button" type="button" aria-label="刷新会话" @click.stop="loadConversations">
          <svg viewBox="0 0 24 24">
            <path d="M20 6v5h-5M4 18v-5h5M18.5 9A7 7 0 0 0 6.1 6.6M5.5 15a7 7 0 0 0 12.4 2.4" />
          </svg>
        </button>
      </div>

      <button class="new-chat" type="button" @click="createConversation">
        <svg viewBox="0 0 24 24" aria-hidden="true">
          <path d="M12 5v14M5 12h14" />
        </svg>
        开启新对话
      </button>

      <nav class="history-list" aria-label="历史会话">
        <p v-if="groupedConversations.length === 0" class="empty-history">暂无历史对话</p>
        <section v-for="group in groupedConversations" :key="group.key" class="history-group">
          <h2>{{ group.label }}</h2>
          <div
            v-for="conversation in group.items"
            :key="conversation.id"
            class="history-item-wrap"
            :class="{ active: conversation.id === activeConversationId }"
          >
            <button class="history-item" type="button" @click="selectConversation(conversation.id)">
              <span v-if="conversation.pinned" class="pin-mark">置顶</span>
              {{ conversation.title }}
            </button>
            <button
              class="history-menu-button"
              type="button"
              aria-label="会话操作"
              @click.stop="openConversationMenu(conversation, $event)"
            >
              ...
            </button>
          </div>
        </section>
      </nav>

      <div class="profile-area" @click.stop>
        <div class="account-menu" v-if="accountMenuOpen">
          <button type="button" @click="openSettings">
            <svg viewBox="0 0 24 24" aria-hidden="true">
              <path d="M12 15.5A3.5 3.5 0 1 0 12 8a3.5 3.5 0 0 0 0 7.5ZM19.4 15a1.7 1.7 0 0 0 .3 1.9l.1.1-2.2 2.2-.1-.1a1.7 1.7 0 0 0-1.9-.3 1.7 1.7 0 0 0-1 1.6v.2h-3.2v-.2a1.7 1.7 0 0 0-1-1.6 1.7 1.7 0 0 0-1.9.3l-.1.1L6.2 17l.1-.1a1.7 1.7 0 0 0 .3-1.9 1.7 1.7 0 0 0-1.6-1H4.8v-3.2H5a1.7 1.7 0 0 0 1.6-1 1.7 1.7 0 0 0-.3-1.9l-.1-.1 2.2-2.2.1.1a1.7 1.7 0 0 0 1.9.3 1.7 1.7 0 0 0 1-1.6v-.2h3.2v.2a1.7 1.7 0 0 0 1 1.6 1.7 1.7 0 0 0 1.9-.3l.1-.1 2.2 2.2-.1.1a1.7 1.7 0 0 0-.3 1.9 1.7 1.7 0 0 0 1.6 1h.2V14H21a1.7 1.7 0 0 0-1.6 1Z" />
            </svg>
            系统设置
          </button>
        </div>

        <div class="profile-row">
          <div class="avatar" aria-hidden="true">C</div>
          <span>学习者</span>
          <button class="icon-button" type="button" aria-label="账号菜单" @click.stop="toggleAccountMenu">
            <svg viewBox="0 0 24 24">
              <path d="M5 12h.01M12 12h.01M19 12h.01" />
            </svg>
          </button>
        </div>
      </div>
    </aside>

    <main ref="chatMain" class="chat-main">
      <header class="chat-header">
        <div>
          <h1>{{ activeConversation?.title || 'CodeMate AI' }}</h1>
          <p>
            <span aria-hidden="true">✦</span>
            {{ activeModeText }}
          </p>
        </div>
        <button class="header-action" type="button" @click="clearMemory">清空记忆</button>
      </header>

      <section v-if="!hasMessages" class="hero-panel" aria-label="开始对话">
        <div class="hero-title">
          <div class="hero-logo" aria-hidden="true">
            <svg viewBox="0 0 24 24">
              <path d="M12.2 2.5c4.9 0 8.9 3.6 8.9 8.1 0 2.6-1.4 5-3.6 6.5.2 1.2.8 2.3 1.8 3.1-1.9.2-3.6-.3-5-1.4-.7.1-1.4.2-2.1.2-4.9 0-8.9-3.6-8.9-8.1s4-8.4 8.9-8.4Z" />
            </svg>
          </div>
          <h2>我是 CodeMate AI，很高兴见到你</h2>
        </div>
      </section>

      <section v-else class="message-list" aria-label="聊天记录">
        <article
          v-for="message in messages"
          :key="message.id"
          class="message-row"
          :class="message.role"
        >
          <div class="message-stack">
            <div v-if="message.role === 'assistant'" class="assistant-message">
              <template v-if="splitAssistantContent(message).thinking">
                <button class="thinking-header" type="button" @click="toggleThought(message.id)">
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M12 3 4 9l8 12 8-12-8-6Z" />
                  </svg>
                  <span>已思考</span>
                  <small>{{ expandedThoughts[message.id] ? '收起' : '展开' }}</small>
                </button>
                <div v-if="expandedThoughts[message.id]" class="thinking-panel">
                  {{ splitAssistantContent(message).thinking }}
                </div>
              </template>
              <div class="assistant-answer rich-text">
                <div
                  v-if="splitAssistantContent(message).answer"
                  v-html="renderRichText(splitAssistantContent(message).answer)"
                ></div>
                <div v-else class="typing">
                  <span></span>
                  <span></span>
                  <span></span>
                </div>
              </div>
              <div v-if="message.content" class="message-meta">
                <time>{{ formatTime(message.timestamp) }}</time>
                <button class="copy-button" type="button" @click="copyMessage(message)">
                  {{ copiedMessageId === message.id ? '已复制' : '复制' }}
                </button>
              </div>
            </div>

            <template v-else>
              <div class="message-bubble">
                <p>{{ message.content }}</p>
              </div>
              <div class="user-message-actions">
                <button type="button" title="复制" aria-label="复制用户消息" @click="copyMessage(message)">
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M9 9h10v10H9zM5 15H4a1 1 0 0 1-1-1V5a1 1 0 0 1 1-1h9a1 1 0 0 1 1 1v1" />
                  </svg>
                  <span>{{ copiedMessageId === message.id ? '已复制' : '复制' }}</span>
                </button>
                <button type="button" title="重新编辑" aria-label="重新编辑用户消息" @click="editUserMessage(message)">
                  <svg viewBox="0 0 24 24" aria-hidden="true">
                    <path d="M12 20h9M16.5 3.5a2.1 2.1 0 0 1 3 3L7 19l-4 1 1-4Z" />
                  </svg>
                  <span>编辑</span>
                </button>
              </div>
            </template>
          </div>
        </article>
      </section>

      <div class="composer-wrap" @wheel="handleComposerWheel">
        <p v-if="error" class="error-banner">{{ error }}</p>
        <form
          class="composer"
          :class="{ 'drag-active': dragActive }"
          @submit.prevent="sendMessage"
          @dragenter.prevent="dragActive = true"
          @dragover.prevent="dragActive = true"
          @dragleave.prevent="dragActive = false"
          @drop.prevent="handleDrop"
        >
          <input
            ref="fileInput"
            class="file-input"
            type="file"
            multiple
            accept="image/png,image/jpeg,image/webp,image/gif,text/*,.md,.java,.js,.ts,.vue,.json,.xml,.yml,.yaml,.sql,.css,.html,.properties,.env,.py"
            @change="handleFileChange"
          />
          <div v-if="attachments.length" class="attachment-list" aria-label="待发送附件">
            <div
              v-for="attachment in attachments"
              :key="attachment.id"
              class="attachment-item"
              :class="attachment.type"
            >
              <img
                v-if="attachment.type === 'image'"
                :src="attachment.previewUrl"
                :alt="attachment.name"
              />
              <span v-else class="file-icon" aria-hidden="true">
                <svg viewBox="0 0 24 24">
                  <path d="M14 2H7a2 2 0 0 0-2 2v16a2 2 0 0 0 2 2h10a2 2 0 0 0 2-2V7Z" />
                  <path d="M14 2v5h5M9 13h6M9 17h4" />
                </svg>
              </span>
              <span class="attachment-info">
                <strong>{{ attachment.name }}</strong>
                <small>{{ attachment.type === 'image' ? '图片' : '文件' }} · {{ formatFileSize(attachment.size) }}</small>
              </span>
              <button type="button" aria-label="移除附件" @click="removeAttachment(attachment.id)">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M18 6 6 18M6 6l12 12" />
                </svg>
              </button>
            </div>
          </div>
          <textarea
            ref="composerInput"
            v-model="input"
            placeholder="给 CodeMate AI 发送消息"
            rows="2"
            aria-label="输入聊天消息"
            @keydown="handleKeydown"
            @paste="handlePaste"
          ></textarea>

          <div class="composer-footer">
            <div class="tool-chips" aria-label="快捷工具">
              <button
                type="button"
                class="tool-chip"
                :class="{ active: deepThinking }"
                :aria-pressed="deepThinking"
                :disabled="loading"
                :title="deepThinking ? '深度思考已开启' : '深度思考未开启'"
                @click="deepThinking = !deepThinking"
              >
                <span class="chip-icon" aria-hidden="true">◇</span>
                深度思考
              </button>
              <button
                type="button"
                class="tool-chip"
                :class="{ active: webSearch }"
                :aria-pressed="webSearch"
                :disabled="loading"
                :title="webSearch ? '联网搜索已开启' : '联网搜索未开启'"
                @click="webSearch = !webSearch"
              >
                <span class="chip-icon" aria-hidden="true">◎</span>
                联网搜索
              </button>
            </div>

            <div class="composer-actions">
              <button
                class="attach-button"
                type="button"
                aria-label="添加附件"
                :disabled="loading"
                @click="triggerFilePicker"
              >
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="m21.4 11.6-8.5 8.5a6 6 0 0 1-8.5-8.5l9.2-9.2a4 4 0 0 1 5.7 5.7l-9.2 9.2a2 2 0 0 1-2.8-2.8l8.5-8.5" />
                </svg>
              </button>
              <button class="send-button" type="submit" :disabled="loading || !canSend" aria-label="发送消息">
                <svg viewBox="0 0 24 24" aria-hidden="true">
                  <path d="M12 19V5m0 0-6 6m6-6 6 6" />
                </svg>
              </button>
            </div>
          </div>
        </form>
        <p class="disclaimer">内容由 AI 生成，请仔细甄别</p>
      </div>
    </main>

    <teleport to="body">
      <div
        v-if="openMenuId && activeMenuConversation"
        class="history-menu floating-history-menu"
        :style="{ top: `${menuPosition.top}px`, left: `${menuPosition.left}px` }"
        @click.stop
      >
        <button type="button" @click="renameConversation(activeMenuConversation)">重命名</button>
        <button type="button" @click="togglePin(activeMenuConversation)">
          {{ activeMenuConversation.pinned ? '取消置顶' : '置顶' }}
        </button>
        <button type="button" class="danger" @click="deleteConversation(activeMenuConversation)">删除</button>
      </div>

      <div v-if="settingsOpen" class="modal-backdrop" @click="settingsOpen = false">
        <section class="settings-modal" role="dialog" aria-modal="true" aria-label="系统设置" @click.stop>
          <header class="settings-header">
            <h2>系统设置</h2>
            <button class="icon-button" type="button" aria-label="关闭系统设置" @click="settingsOpen = false">
              <svg viewBox="0 0 24 24">
                <path d="M18 6 6 18M6 6l12 12" />
              </svg>
            </button>
          </header>

          <div class="settings-list">
            <label class="setting-row">
              <span>
                <strong>默认深度思考</strong>
                <small>发送消息时默认输出思路摘要。</small>
              </span>
              <input v-model="deepThinking" type="checkbox" :disabled="loading" />
            </label>
            <label class="setting-row">
              <span>
                <strong>默认联网搜索</strong>
                <small>发送消息时默认携带联网搜索结果。</small>
              </span>
              <input v-model="webSearch" type="checkbox" :disabled="loading" />
            </label>
          </div>
        </section>
      </div>
    </teleport>
  </div>
</template>
