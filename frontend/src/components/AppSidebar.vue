<template>
  <aside class="sidebar">
    <div class="sidebar__logo">
      <div class="sidebar__logo-icon">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5">
          <path d="M3.5 5.5L5 3h14l1.5 2.5M4 5.5v13a2 2 0 002 2h12a2 2 0 002-2v-13" />
          <path d="M8 10h8M8 14h5" />
        </svg>
      </div>
      <div class="sidebar__logo-text">
        <span class="sidebar__brand">长阳排程</span>
        <span class="sidebar__version">v1.0</span>
      </div>
    </div>

    <nav class="sidebar__nav">
      <router-link
        v-for="item in navItems"
        :key="item.path"
        :to="item.path"
        class="sidebar__link"
        active-class="sidebar__link--active"
      >
        <span class="sidebar__link-icon" v-html="item.icon"></span>
        <span class="sidebar__link-label">{{ item.label }}</span>
      </router-link>
    </nav>

    <div class="sidebar__footer">
      <div class="sidebar__status">
        <span
          class="sidebar__dot"
          :class="statusClass"
        ></span>
        <span class="sidebar__status-text">{{ statusText }}</span>
      </div>
    </div>
  </aside>
</template>

<script setup>
import { computed } from 'vue'
import { useSchedulingStore } from '../stores/scheduling'

const store = useSchedulingStore()

const navItems = [
  {
    path: '/',
    label: '仪表盘',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><rect x="3" y="3" width="7" height="7" rx="1"/><rect x="14" y="3" width="7" height="4" rx="1"/><rect x="14" y="10" width="7" height="11" rx="1"/><rect x="3" y="13" width="7" height="8" rx="1"/></svg>'
  },
  {
    path: '/orders',
    label: '订单管理',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2"/><rect x="9" y="3" width="6" height="4" rx="1"/><path d="M9 12h6M9 16h4"/></svg>'
  },
  {
    path: '/result',
    label: '排程结果',
    icon: '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5"><path d="M3 3v18h18"/><path d="M7 16l4-4 3 3 5-6"/></svg>'
  }
]

const statusClass = computed(() => ({
  'sidebar__dot--solving': store.solverStatus === 'SOLVING',
  'sidebar__dot--idle': store.solverStatus === 'NOT_SOLVING',
  'sidebar__dot--terminated': store.solverStatus === 'TERMINATED'
}))

const statusText = computed(() => {
  const map = {
    'NOT_SOLVING': '待机中',
    'SOLVING': '求解中...',
    'TERMINATED': '已终止'
  }
  return map[store.solverStatus] || store.solverStatus
})
</script>

<style scoped>
.sidebar {
  position: fixed;
  left: 0;
  top: 0;
  bottom: 0;
  width: var(--sidebar-width);
  background: var(--bg-primary);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  z-index: 100;
  overflow-y: auto;
}

.sidebar__logo {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 24px 20px;
  border-bottom: 1px solid var(--border);
}

.sidebar__logo-icon {
  width: 36px;
  height: 36px;
  background: linear-gradient(135deg, var(--cyan-dim), var(--cyan));
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  color: var(--bg-deep);
  box-shadow: 0 0 12px var(--cyan-glow);
}

.sidebar__logo-icon svg {
  width: 20px;
  height: 20px;
}

.sidebar__brand {
  font-family: var(--font-heading);
  font-weight: 700;
  font-size: 1.1rem;
  color: var(--text-primary);
  letter-spacing: 0.02em;
}

.sidebar__version {
  display: block;
  font-family: var(--font-mono);
  font-size: 0.65rem;
  color: var(--text-muted);
  margin-top: 1px;
}

.sidebar__nav {
  flex: 1;
  padding: 16px 12px;
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.sidebar__link {
  display: flex;
  align-items: center;
  gap: 12px;
  padding: 11px 14px;
  border-radius: var(--radius-sm);
  color: var(--text-secondary);
  font-family: var(--font-heading);
  font-size: 0.9rem;
  font-weight: 400;
  transition: all var(--transition-normal);
  text-decoration: none;
}

.sidebar__link:hover {
  background: var(--bg-surface);
  color: var(--text-primary);
}

.sidebar__link--active {
  background: var(--bg-surface);
  color: var(--cyan);
  font-weight: 500;
  border-left: 2px solid var(--cyan);
  box-shadow: inset 0 0 16px var(--cyan-glow);
}

.sidebar__link-icon {
  width: 20px;
  height: 20px;
  flex-shrink: 0;
  display: flex;
  align-items: center;
}

.sidebar__link-icon :deep(svg) {
  width: 20px;
  height: 20px;
}

.sidebar__footer {
  padding: 16px 20px;
  border-top: 1px solid var(--border);
}

.sidebar__status {
  display: flex;
  align-items: center;
  gap: 8px;
}

.sidebar__dot {
  width: 8px;
  height: 8px;
  border-radius: 50%;
  background: var(--text-muted);
}

.sidebar__dot--idle {
  background: var(--text-muted);
}

.sidebar__dot--solving {
  background: var(--cyan);
  box-shadow: 0 0 8px var(--cyan-glow-strong);
  animation: pulse-glow-dot 1.5s ease-in-out infinite;
}

.sidebar__dot--terminated {
  background: var(--amber);
}

.sidebar__status-text {
  font-family: var(--font-mono);
  font-size: 0.75rem;
  color: var(--text-muted);
}

@keyframes pulse-glow-dot {
  0%, 100% { opacity: 1; }
  50% { opacity: 0.5; }
}
</style>
