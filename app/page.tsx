'use client'

import { useState, useRef, useEffect, useCallback } from 'react'

// ====== Types ======

interface Evidence {
  type: 'QURAN' | 'HADITH'
  title: string
  text: string
  source: string
  narrator: string
  highlights: string[]
  aiRelevance: string
}

interface AiResponse {
  intro: string
  aiInsight: string
  evidence: Evidence[]
  error?: string
}

interface ChatMessage {
  role: 'user' | 'assistant'
  content?: string
  response?: AiResponse
  timestamp: number
}

// ====== Quick Suggestions ======

const SUGGESTIONS = [
  { emoji: '😔', text: "Ko'nglim siqilyapti" },
  { emoji: '😰', text: 'Kelajagimdan qo\'rqaman' },
  { emoji: '🤲', text: 'Tavba qilmoqchiman' },
  { emoji: '💼', text: 'Ishim yurishmayapti' },
  { emoji: '😡', text: 'Jahlim chiqyapti' },
  { emoji: '🌙', text: 'Yomon tush ko\'rdim' },
  { emoji: '📖', text: 'Namozga dangasaman' },
  { emoji: '🏥', text: 'Kasal bo\'lib qoldim' },
]

// ====== SVG Icons ======

function SendIcon() {
  return (
    <svg width="20" height="20" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <line x1="22" y1="2" x2="11" y2="13" />
      <polygon points="22 2 15 22 11 13 2 9 22 2" />
    </svg>
  )
}

function BookIcon() {
  return (
    <svg width="16" height="16" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
      <path d="M4 19.5A2.5 2.5 0 0 1 6.5 17H20" />
      <path d="M6.5 2H20v20H6.5A2.5 2.5 0 0 1 4 19.5v-15A2.5 2.5 0 0 1 6.5 2z" />
    </svg>
  )
}

function StarIcon() {
  return (
    <svg width="14" height="14" viewBox="0 0 24 24" fill="currentColor" stroke="none">
      <polygon points="12 2 15.09 8.26 22 9.27 17 14.14 18.18 21.02 12 17.77 5.82 21.02 7 14.14 2 9.27 8.91 8.26 12 2" />
    </svg>
  )
}

function MoonStarIcon() {
  return (
    <svg width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.5" strokeLinecap="round" strokeLinejoin="round">
      <path d="M12 3a6 6 0 0 0 9 9 9 9 0 1 1-9-9Z" />
      <path d="M19 3v4" />
      <path d="M21 5h-4" />
    </svg>
  )
}

// ====== Main Component ======

export default function TadabburChat() {
  const [messages, setMessages] = useState<ChatMessage[]>([])
  const [input, setInput] = useState('')
  const [isLoading, setIsLoading] = useState(false)
  const messagesEndRef = useRef<HTMLDivElement>(null)
  const inputRef = useRef<HTMLTextAreaElement>(null)

  const scrollToBottom = useCallback(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [])

  useEffect(() => {
    scrollToBottom()
  }, [messages, isLoading, scrollToBottom])

  // Auto-resize textarea
  useEffect(() => {
    if (inputRef.current) {
      inputRef.current.style.height = 'auto'
      inputRef.current.style.height = Math.min(inputRef.current.scrollHeight, 120) + 'px'
    }
  }, [input])

  const sendMessage = async (text?: string) => {
    const query = (text || input).trim()
    if (!query || isLoading) return
    setInput('')
    if (inputRef.current) inputRef.current.style.height = 'auto'

    const userMsg: ChatMessage = {
      role: 'user',
      content: query,
      timestamp: Date.now(),
    }
    setMessages((prev) => [...prev, userMsg])
    setIsLoading(true)

    try {
      const res = await fetch('/api/tadabbur', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({ query }),
      })

      const data: AiResponse = await res.json()

      const assistantMsg: ChatMessage = {
        role: 'assistant',
        response: data,
        timestamp: Date.now(),
      }
      setMessages((prev) => [...prev, assistantMsg])
    } catch {
      const errorMsg: ChatMessage = {
        role: 'assistant',
        response: {
          intro: "Serverga ulanishda xatolik bo'ldi. Iltimos, qayta urinib ko'ring.",
          aiInsight: '',
          evidence: [],
        },
        timestamp: Date.now(),
      }
      setMessages((prev) => [...prev, errorMsg])
    } finally {
      setIsLoading(false)
    }
  }

  const handleKeyDown = (e: React.KeyboardEvent<HTMLTextAreaElement>) => {
    if (e.key === 'Enter' && !e.shiftKey) {
      e.preventDefault()
      sendMessage()
    }
  }

  return (
    <div className="flex flex-col h-dvh bg-islamic-pattern overflow-hidden">
      {/* Header */}
      <header className="header-glass sticky top-0 z-10 px-4 py-3 flex items-center gap-3">
        <div className="flex items-center gap-2.5">
          <div className="w-9 h-9 rounded-xl bg-gradient-to-br from-emerald-500/30 to-emerald-700/30 border border-emerald-500/20 flex items-center justify-center text-emerald-400">
            <MoonStarIcon />
          </div>
          <div>
            <h1 className="gradient-text text-lg font-semibold tracking-tight leading-tight">
              Tadabbur AI
            </h1>
            <p className="text-[0.65rem] text-emerald-400/60 tracking-wide">
              Qur&apos;on va Hadisdan yo&apos;l-yo&apos;riq
            </p>
          </div>
        </div>
      </header>

      {/* Messages Area */}
      <main className="flex-1 overflow-y-auto custom-scrollbar px-4 py-4">
        {messages.length === 0 ? (
          <EmptyState onSuggestionClick={(text) => sendMessage(text)} />
        ) : (
          <div className="max-w-2xl mx-auto space-y-4">
            {messages.map((msg, idx) => (
              <div
                key={`${msg.timestamp}-${idx}`}
                className="animate-fade-in-up"
                style={{ animationDelay: `${idx * 0.05}s` }}
              >
                {msg.role === 'user' ? (
                  <UserBubble text={msg.content || ''} />
                ) : (
                  <AssistantResponse response={msg.response!} />
                )}
              </div>
            ))}
            {isLoading && <LoadingIndicator />}
            <div ref={messagesEndRef} />
          </div>
        )}
      </main>

      {/* Input Area */}
      <footer className="glass sticky bottom-0 px-4 py-3">
        <div className="max-w-2xl mx-auto">
          <div className="flex items-end gap-2 input-glow rounded-2xl bg-[oklch(0.15_0.015_160_/_70%)] border border-emerald-500/15 px-4 py-2 transition-all duration-200">
            <textarea
              ref={inputRef}
              value={input}
              onChange={(e) => setInput(e.target.value)}
              onKeyDown={handleKeyDown}
              placeholder="Holatingizni yozing... (masalan: ko'nglim siqilyapti)"
              rows={1}
              className="flex-1 bg-transparent text-sm text-emerald-50 placeholder:text-emerald-400/35 resize-none outline-none py-1.5 max-h-[120px]"
              disabled={isLoading}
            />
            <button
              onClick={() => sendMessage()}
              disabled={!input.trim() || isLoading}
              className="send-btn flex-shrink-0"
              aria-label="Yuborish"
            >
              <SendIcon />
            </button>
          </div>
          <p className="text-center text-[0.6rem] text-emerald-500/30 mt-2">
            Javoblar faqat Qur&apos;on va sahih hadis manbalaridan tuziladi
          </p>
        </div>
      </footer>
    </div>
  )
}

// ====== Empty State ======

function EmptyState({ onSuggestionClick }: { onSuggestionClick: (text: string) => void }) {
  return (
    <div className="max-w-lg mx-auto flex flex-col items-center justify-center min-h-[60vh] px-4">
      {/* Decorative Icon */}
      <div className="mb-6 animate-float">
        <div className="w-20 h-20 rounded-2xl bg-gradient-to-br from-emerald-500/20 to-gold-500/20 border border-emerald-500/15 flex items-center justify-center">
          <span className="text-4xl">📖</span>
        </div>
      </div>

      <h2 className="gradient-text text-xl font-semibold text-center mb-2">
        Assalomu alaykum!
      </h2>
      <p className="text-emerald-300/60 text-sm text-center mb-8 max-w-sm leading-relaxed">
        Holatingizni yozing — AI sizga Qur&apos;on oyatlari va sahih hadislardan
        eng mos dalillarni topib beradi
      </p>

      {/* Suggestion Chips */}
      <div className="flex flex-wrap justify-center gap-2 max-w-md">
        {SUGGESTIONS.map((s) => (
          <button
            key={s.text}
            onClick={() => onSuggestionClick(s.text)}
            className="suggestion-chip"
          >
            <span className="mr-1.5">{s.emoji}</span>
            {s.text}
          </button>
        ))}
      </div>
    </div>
  )
}

// ====== User Bubble ======

function UserBubble({ text }: { text: string }) {
  return (
    <div className="flex justify-end">
      <div className="chat-user max-w-[85%] px-4 py-3">
        <p className="text-sm text-emerald-50 leading-relaxed">{text}</p>
      </div>
    </div>
  )
}

// ====== Assistant Response ======

function AssistantResponse({ response }: { response: AiResponse }) {
  return (
    <div className="space-y-3">
      {/* Intro */}
      {response.intro && (
        <div className="chat-ai max-w-[92%] px-4 py-3">
          <p className="text-sm text-emerald-100/85 leading-relaxed">
            {response.intro}
          </p>
        </div>
      )}

      {/* AI Insight */}
      {response.aiInsight && (
        <div className="ai-insight max-w-[92%] px-4 py-3.5">
          <div className="flex items-center gap-2 mb-2">
            <span className="text-sm">💡</span>
            <span className="text-xs font-semibold text-emerald-300/90 tracking-wide uppercase">
              AI Tavsiyasi
            </span>
          </div>
          <p className="text-sm text-emerald-100/80 leading-relaxed">
            {response.aiInsight}
          </p>
        </div>
      )}

      {/* Evidence Cards */}
      {response.evidence?.map((ev, idx) => (
        <EvidenceCard key={`${ev.title}-${idx}`} evidence={ev} />
      ))}

      {/* Error */}
      {response.error && (
        <div className="chat-ai max-w-[92%] px-4 py-3">
          <p className="text-sm text-red-300/80">{response.error}</p>
        </div>
      )}
    </div>
  )
}

// ====== Evidence Card ======

function EvidenceCard({ evidence }: { evidence: Evidence }) {
  const [expanded, setExpanded] = useState(false)
  const isLong = evidence.type === 'HADITH' && evidence.text.length > 400
  const cardClass = evidence.type === 'QURAN' ? 'evidence-quran' : 'evidence-hadith'
  const accentColor = evidence.type === 'QURAN' ? 'text-emerald-400' : 'text-amber-400/90'

  return (
    <div className={`${cardClass} rounded-xl max-w-[92%] px-4 py-3.5 space-y-2.5`}>
      {/* Header */}
      <div className="flex items-center gap-2">
        {evidence.type === 'QURAN' ? (
          <BookIcon />
        ) : (
          <StarIcon />
        )}
        <span className={`text-xs font-semibold ${accentColor} tracking-wide`}>
          {evidence.type === 'QURAN' ? "Qur'on" : 'Hadis'} • {evidence.title}
        </span>
      </div>

      {/* Tags */}
      {evidence.highlights.length > 0 && (
        <div className="flex flex-wrap gap-1.5">
          {evidence.highlights.filter(Boolean).slice(0, 5).map((h, i) => (
            <span
              key={`${h}-${i}`}
              className={evidence.type === 'QURAN' ? 'tag' : 'tag tag-gold'}
            >
              {h}
            </span>
          ))}
        </div>
      )}

      {/* Text */}
      <p className="text-sm text-emerald-50/80 leading-relaxed">
        {isLong && !expanded
          ? evidence.text.slice(0, 400) + '...'
          : evidence.text}
      </p>

      {isLong && (
        <button
          onClick={() => setExpanded(!expanded)}
          className="text-xs text-emerald-400/70 hover:text-emerald-300 transition-colors"
        >
          {expanded ? '⬆ Kamroq' : '⬇ Ko\'proq ko\'rsatish'}
        </button>
      )}

      {/* AI Relevance */}
      {evidence.aiRelevance && (
        <p className={`text-xs ${accentColor} opacity-75 italic`}>
          ✦ {evidence.aiRelevance}
        </p>
      )}

      {/* Narrator */}
      {evidence.narrator && (
        <p className="text-[0.7rem] text-emerald-300/40">
          Roviy: {evidence.narrator}
        </p>
      )}

      {/* Source */}
      <p className="text-[0.65rem] text-emerald-300/30">
        {evidence.source}
      </p>
    </div>
  )
}

// ====== Loading Indicator ======

function LoadingIndicator() {
  return (
    <div className="chat-ai max-w-[60%] px-4 py-3 animate-fade-in-up">
      <div className="flex items-center gap-2">
        <span className="text-sm text-emerald-400/70">AI tahlil qilmoqda</span>
        <div className="loading-dots">
          <span></span>
          <span></span>
          <span></span>
        </div>
      </div>
    </div>
  )
}
