<script setup>
import { ref, nextTick, onMounted } from 'vue'
import { marked } from 'marked'
import hljs from 'highlight.js'
import 'highlight.js/styles/github-dark.css'

marked.setOptions({
  breaks: true,
  highlight(code, lang) {
    if (lang && hljs.getLanguage(lang)) {
      return hljs.highlight(code, { language: lang }).value
    }
    return hljs.highlightAuto(code).value
  }
})

const messages = ref([])
const inputText = ref('')
const isLoading = ref(false)
const error = ref('')
const showApiKeyModal = ref(false)
const apiKeyInput = ref('')
const apiKey = ref(localStorage.getItem('mimo_api_key') || '')

const chatContainer = ref(null)

function scrollToBottom() {
  nextTick(() => {
    if (chatContainer.value) {
      chatContainer.value.scrollTop = chatContainer.value.scrollHeight
    }
  })
}

function renderMarkdown(text) {
  return marked.parse(text)
}

function openApiKeyModal() {
  apiKeyInput.value = apiKey.value
  showApiKeyModal.value = true
}

function saveApiKey() {
  apiKey.value = apiKeyInput.value.trim()
  localStorage.setItem('mimo_api_key', apiKey.value)
  showApiKeyModal.value = false
}

async function sendMessage() {
  const text = inputText.value.trim()
  if (!text || isLoading.value) return

  if (!apiKey.value) {
    openApiKeyModal()
    return
  }

  error.value = ''
  messages.value.push({ role: 'user', content: text })
  inputText.value = ''
  isLoading.value = true
  scrollToBottom()

  const assistantMsg = { role: 'assistant', content: '' }
  messages.value.push(assistantMsg)

  try {
    const response = await fetch('/api/chat/stream', {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ message: text, apiKey: apiKey.value })
    })

    if (!response.ok) {
      const errBody = await response.json().catch(() => ({}))
      throw new Error(errBody.error || `HTTP ${response.status}`)
    }

    const reader = response.body.getReader()
    const decoder = new TextDecoder()
    let buffer = ''

    while (true) {
      const { done, value } = await reader.read()
      if (done) break

      buffer += decoder.decode(value, { stream: true })
      const lines = buffer.split('\n')
      buffer = lines.pop() || ''

      for (const line of lines) {
        if (line.startsWith('data: ')) {
          const data = line.slice(6)
          if (data === '[DONE]') break
          assistantMsg.content += data
          scrollToBottom()
        }
      }
    }
  } catch (err) {
    error.value = err.message || 'Request failed'
    if (assistantMsg.content === '') {
      messages.value.pop()
    }
  } finally {
    isLoading.value = false
    scrollToBottom()
  }
}

function handleKeydown(e) {
  if (e.key === 'Enter' && !e.shiftKey) {
    e.preventDefault()
    sendMessage()
  }
}

function clearChat() {
  messages.value = []
  error.value = ''
}

onMounted(() => {
  if (!apiKey.value) {
    showApiKeyModal.value = true
  }
})
</script>

<template>
  <header class="app-header">
    <h1>
      <span class="logo">AI</span>
      Spring AI Chat Demo
    </h1>
    <div class="header-actions">
      <button class="btn" @click="openApiKeyModal">API Key</button>
      <button class="btn" @click="clearChat">Clear</button>
    </div>
  </header>

  <div class="chat-container" ref="chatContainer">
    <div class="chat-messages" v-if="messages.length > 0">
      <div v-for="(msg, idx) in messages" :key="idx" :class="['message', msg.role]">
        <div class="avatar">{{ msg.role === 'user' ? 'U' : 'AI' }}</div>
        <div class="bubble" v-if="msg.role === 'user'">{{ msg.content }}</div>
        <div class="bubble" v-else v-html="renderMarkdown(msg.content)"></div>
      </div>

      <div v-if="isLoading && !messages[messages.length - 1]?.content" class="message assistant">
        <div class="avatar">AI</div>
        <div class="bubble">
          <div class="typing-indicator">
            <span></span><span></span><span></span>
          </div>
        </div>
      </div>
    </div>

    <div class="welcome-screen" v-else>
      <div class="logo-big">AI</div>
      <h2>Spring AI Chat Demo</h2>
      <p>Powered by mimo-v2.5-pro. Start a conversation to explore AI text generation.</p>
    </div>
  </div>

  <div class="input-area">
    <div v-if="error" class="error-toast">{{ error }}</div>
    <div class="input-wrapper">
      <textarea
        v-model="inputText"
        @keydown="handleKeydown"
        placeholder="Type your message... (Enter to send, Shift+Enter for new line)"
        rows="1"
        :disabled="isLoading"
      ></textarea>
      <button class="send-btn" @click="sendMessage" :disabled="isLoading || !inputText.trim()">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round">
          <line x1="22" y1="2" x2="11" y2="13"></line>
          <polygon points="22 2 15 22 11 13 2 9 22 2"></polygon>
        </svg>
      </button>
    </div>
    <div class="input-hint">Powered by Spring AI + mimo-v2.5-pro</div>
  </div>

  <div v-if="showApiKeyModal" class="modal-overlay" @click.self="showApiKeyModal = false">
    <div class="modal">
      <h3>Set API Key</h3>
      <p>Enter your mimo API key to start chatting. The key is stored locally in your browser.</p>
      <label>MiMo API Key</label>
      <input
        v-model="apiKeyInput"
        type="password"
        placeholder="sk-..."
        @keydown.enter="saveApiKey"
      />
      <div class="modal-actions">
        <button class="btn" @click="showApiKeyModal = false">Cancel</button>
        <button class="btn primary" @click="saveApiKey">Save</button>
      </div>
    </div>
  </div>
</template>
