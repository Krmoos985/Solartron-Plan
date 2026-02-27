<template>
  <div class="dashboard">
    <header class="dashboard__header fade-in">
      <div>
        <h1 class="dashboard__title">排程控制台</h1>
        <p class="dashboard__subtitle">长阳科技 · 母卷生产排程系统</p>
      </div>
      <div class="dashboard__actions">
        <ScoreBoard :score="store.score" />
        <button
          v-if="store.solverStatus !== 'SOLVING'"
          class="btn btn--primary solve-btn"
          @click="handleSolve"
          :disabled="store.orders.length === 0"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
            <polygon points="5 3 19 12 5 21 5 3" fill="currentColor"/>
          </svg>
          开始求解
        </button>
        <button
          v-else
          class="btn btn--danger"
          @click="store.stopSolving()"
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" width="18" height="18">
            <rect x="6" y="6" width="12" height="12" fill="currentColor"/>
          </svg>
          终止求解
        </button>
      </div>
    </header>

    <!-- 统计卡片 -->
    <div class="dashboard__stats fade-in stagger-1">
      <div class="stat-card">
        <div class="stat-card__icon stat-card__icon--cyan">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="24" height="24">
            <path d="M9 5H7a2 2 0 00-2 2v12a2 2 0 002 2h10a2 2 0 002-2V7a2 2 0 00-2-2h-2"/>
            <rect x="9" y="3" width="6" height="4" rx="1"/>
          </svg>
        </div>
        <div class="stat-card__data">
          <span class="stat-card__value">{{ store.totalOrders }}</span>
          <span class="stat-card__label">订单数</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-card__icon stat-card__icon--emerald">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="24" height="24">
            <path d="M3.5 5.5L5 3h14l1.5 2.5M4 5.5v13a2 2 0 002 2h12a2 2 0 002-2v-13"/>
          </svg>
        </div>
        <div class="stat-card__data">
          <span class="stat-card__value">{{ store.productionLines.length }}</span>
          <span class="stat-card__label">产线数</span>
        </div>
      </div>

      <div class="stat-card">
        <div class="stat-card__icon stat-card__icon--amber">
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="24" height="24">
            <circle cx="12" cy="12" r="10"/>
            <polyline points="12 6 12 12 16 14"/>
          </svg>
        </div>
        <div class="stat-card__data">
          <span class="stat-card__value">{{ statusLabel }}</span>
          <span class="stat-card__label">求解状态</span>
        </div>
      </div>
    </div>

    <!-- 快捷操作 -->
    <div class="dashboard__quick fade-in stagger-2">
      <button class="btn" @click="store.loadDemoData()">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1.5" width="16" height="16">
          <path d="M4 16v2a2 2 0 002 2h12a2 2 0 002-2v-2M12 3v12M7 8l5-5 5 5"/>
        </svg>
        加载演示数据
      </button>
      <button class="btn" @click="store.clearOrders()">清空订单</button>
      <router-link to="/orders" class="btn">管理订单 →</router-link>
    </div>

    <!-- 甘特图 -->
    <div class="dashboard__gantt fade-in stagger-3">
      <h2 class="section-title">排程时间线</h2>
      <GanttChart
        :lines="ganttLines"
        :selected-order-id="selectedOrder?.id"
        @select-order="selectedOrder = $event"
      />
    </div>

    <!-- 选中订单详情 -->
    <transition name="slide">
      <div v-if="selectedOrder" class="dashboard__detail card card--glow fade-in">
        <div class="detail__header">
          <h3>订单详情</h3>
          <button class="btn btn--sm" @click="selectedOrder = null">关闭</button>
        </div>
        <div class="detail__grid">
          <div class="detail__item">
            <span class="detail__label">型号</span>
            <span class="detail__value tag tag--cyan">{{ selectedOrder.productCode }}</span>
          </div>
          <div class="detail__item">
            <span class="detail__label">配方</span>
            <span class="detail__value">{{ selectedOrder.formulaCode }}</span>
          </div>
          <div class="detail__item">
            <span class="detail__label">厚度</span>
            <span class="detail__value">{{ selectedOrder.thickness }}μm</span>
          </div>
          <div class="detail__item">
            <span class="detail__label">数量</span>
            <span class="detail__value">{{ selectedOrder.quantity }}</span>
          </div>
          <div class="detail__item">
            <span class="detail__label">库存天数</span>
            <span class="detail__value" :class="supplyDaysClass(selectedOrder)">
              {{ supplyDays(selectedOrder) }}天
            </span>
          </div>
          <div class="detail__item">
            <span class="detail__label">生产时长</span>
            <span class="detail__value">{{ selectedOrder.productionDurationHours }}h</span>
          </div>
          <div class="detail__item" v-if="selectedOrder.startTime">
            <span class="detail__label">开始时间</span>
            <span class="detail__value">{{ formatTime(selectedOrder.startTime) }}</span>
          </div>
          <div class="detail__item" v-if="selectedOrder.endTime">
            <span class="detail__label">结束时间</span>
            <span class="detail__value">{{ formatTime(selectedOrder.endTime) }}</span>
          </div>
        </div>
      </div>
    </transition>

    <!-- 错误提示 -->
    <div v-if="store.error" class="dashboard__error fade-in">
      <span>⚠ {{ store.error }}</span>
      <button class="btn btn--sm" @click="store.error = null">关闭</button>
    </div>
  </div>
</template>

<script setup>
import { ref, computed } from 'vue'
import { useSchedulingStore } from '../stores/scheduling'
import ScoreBoard from '../components/ScoreBoard.vue'
import GanttChart from '../components/GanttChart.vue'

const store = useSchedulingStore()
const selectedOrder = ref(null)

const statusLabel = computed(() => {
  const map = { 'NOT_SOLVING': '待机', 'SOLVING': '求解中', 'TERMINATED': '已终止' }
  return map[store.solverStatus] || store.solverStatus
})

const ganttLines = computed(() => {
  if (store.solvedLines.length > 0) return store.solvedLines
  return store.productionLines
})

function handleSolve() {
  selectedOrder.value = null
  store.solve()
}

function supplyDays(order) {
  if (!order.monthlyShipment || order.monthlyShipment <= 0) return '∞'
  return ((order.currentInventory / order.monthlyShipment) * 30).toFixed(1)
}

function supplyDaysClass(order) {
  const days = parseFloat(supplyDays(order))
  if (isNaN(days)) return ''
  if (days < 10) return 'text-red'
  if (days < 20) return 'text-amber'
  return 'text-emerald'
}

function formatTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  return `${(d.getMonth() + 1).toString().padStart(2, '0')}/${d.getDate().toString().padStart(2, '0')} ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}
</script>

<style scoped>
.dashboard__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 28px;
}

.dashboard__title {
  font-size: 1.8rem;
  font-weight: 700;
  letter-spacing: -0.02em;
  background: linear-gradient(135deg, var(--text-primary), var(--cyan));
  -webkit-background-clip: text;
  -webkit-text-fill-color: transparent;
  background-clip: text;
}

.dashboard__subtitle {
  font-size: 0.85rem;
  color: var(--text-muted);
  margin-top: 4px;
}

.dashboard__actions {
  display: flex;
  align-items: center;
  gap: 16px;
  flex-wrap: wrap;
}

.solve-btn {
  animation: pulse-glow 2s ease-in-out infinite;
}

.solve-btn:disabled {
  animation: none;
  opacity: 0.4;
  cursor: not-allowed;
}

.dashboard__stats {
  display: grid;
  grid-template-columns: repeat(auto-fit, minmax(200px, 1fr));
  gap: 16px;
  margin-bottom: 24px;
}

.stat-card {
  display: flex;
  align-items: center;
  gap: 16px;
  padding: 18px 20px;
  background: var(--bg-surface);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  transition: all var(--transition-normal);
}

.stat-card:hover {
  border-color: var(--border-hover);
  transform: translateY(-2px);
}

.stat-card__icon {
  width: 44px;
  height: 44px;
  border-radius: var(--radius-sm);
  display: flex;
  align-items: center;
  justify-content: center;
  flex-shrink: 0;
}

.stat-card__icon--cyan {
  background: var(--cyan-glow);
  color: var(--cyan);
}

.stat-card__icon--emerald {
  background: rgba(16, 185, 129, 0.12);
  color: var(--emerald);
}

.stat-card__icon--amber {
  background: rgba(255, 176, 32, 0.12);
  color: var(--amber);
}

.stat-card__value {
  font-family: var(--font-mono);
  font-size: 1.5rem;
  font-weight: 600;
  color: var(--text-primary);
  display: block;
}

.stat-card__label {
  font-size: 0.75rem;
  color: var(--text-muted);
  display: block;
  margin-top: 2px;
}

.dashboard__quick {
  display: flex;
  gap: 10px;
  margin-bottom: 28px;
  flex-wrap: wrap;
}

.dashboard__gantt {
  margin-bottom: 24px;
}

.section-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

/* 详情面板 */
.dashboard__detail {
  margin-top: 16px;
}

.detail__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 16px;
}

.detail__header h3 {
  font-size: 1rem;
  color: var(--text-primary);
}

.detail__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(180px, 1fr));
  gap: 14px;
}

.detail__item {
  display: flex;
  flex-direction: column;
  gap: 4px;
}

.detail__label {
  font-size: 0.7rem;
  text-transform: uppercase;
  letter-spacing: 0.06em;
  color: var(--text-muted);
  font-family: var(--font-heading);
}

.detail__value {
  font-family: var(--font-mono);
  font-size: 0.9rem;
  color: var(--text-primary);
}

/* 颜色辅助 */
.text-red { color: var(--red); }
.text-amber { color: var(--amber); }
.text-emerald { color: var(--emerald); }

/* 错误提示 */
.dashboard__error {
  margin-top: 16px;
  padding: 14px 18px;
  background: rgba(239, 68, 68, 0.08);
  border: 1px solid rgba(239, 68, 68, 0.2);
  border-radius: var(--radius-sm);
  color: var(--red);
  display: flex;
  justify-content: space-between;
  align-items: center;
  font-size: 0.85rem;
}

/* 过渡 */
.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateY(12px);
}
</style>
