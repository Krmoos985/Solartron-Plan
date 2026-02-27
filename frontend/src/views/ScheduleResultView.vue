<template>
  <div class="result-view">
    <header class="result-view__header fade-in">
      <div>
        <h1 class="result-view__title">排程结果</h1>
        <p class="result-view__subtitle">
          <template v-if="store.solution">
            最新求解结果 · 得分：{{ scoreText }}
          </template>
          <template v-else>
            尚未执行求解，请前往 <router-link to="/">仪表盘</router-link> 启动求解
          </template>
        </p>
      </div>
      <ScoreBoard v-if="store.solution" :score="store.score" />
    </header>

    <template v-if="store.solution">
      <!-- 甘特图 -->
      <div class="result-view__gantt fade-in stagger-1">
        <h2 class="section-title">时间线视图</h2>
        <GanttChart
          :lines="store.solvedLines"
          :selected-order-id="selectedOrder?.id"
          @select-order="selectedOrder = $event"
        />
      </div>

      <!-- 产线详情 -->
      <div class="result-view__lines fade-in stagger-2">
        <h2 class="section-title">产线详情</h2>
        <div class="line-cards">
          <div class="line-card card" v-for="line in store.solvedLines" :key="line.id">
            <div class="line-card__header">
              <h3>{{ line.name }}</h3>
              <span class="tag tag--cyan">{{ line.orders?.length || 0 }} 个订单</span>
            </div>
            <div class="table-wrap">
              <table v-if="line.orders?.length > 0">
                <thead>
                  <tr>
                    <th>#</th>
                    <th>型号</th>
                    <th>厚度</th>
                    <th>配方</th>
                    <th>开始</th>
                    <th>结束</th>
                    <th>时长</th>
                  </tr>
                </thead>
                <tbody>
                  <tr
                    v-for="(order, idx) in line.orders"
                    :key="order.id"
                    :class="{ 'row--selected': selectedOrder?.id === order.id }"
                    @click="selectedOrder = order"
                    style="cursor: pointer"
                  >
                    <td>{{ idx + 1 }}</td>
                    <td><span class="tag tag--cyan">{{ order.productCode }}</span></td>
                    <td>{{ order.thickness }}μm</td>
                    <td>{{ order.formulaCode }}</td>
                    <td>{{ formatTime(order.startTime) }}</td>
                    <td>{{ formatTime(order.endTime) }}</td>
                    <td>{{ order.productionDurationHours }}h</td>
                  </tr>
                </tbody>
              </table>
              <p v-else class="line-card__empty">此产线暂无排程</p>
            </div>
          </div>
        </div>
      </div>

      <!-- 选中订单详情 -->
      <transition name="slide">
        <div v-if="selectedOrder" class="result-view__detail card card--glow fade-in">
          <div class="detail__header">
            <h3>{{ selectedOrder.productCode }} · 订单详情</h3>
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
              <span class="detail__label">当前库存</span>
              <span class="detail__value">{{ selectedOrder.currentInventory }}</span>
            </div>
            <div class="detail__item">
              <span class="detail__label">月出货量</span>
              <span class="detail__value">{{ selectedOrder.monthlyShipment }}</span>
            </div>
            <div class="detail__item">
              <span class="detail__label">库存天数</span>
              <span class="detail__value">{{ supplyDays(selectedOrder) }}天</span>
            </div>
            <div class="detail__item">
              <span class="detail__label">生产时长</span>
              <span class="detail__value">{{ selectedOrder.productionDurationHours }}h</span>
            </div>
            <div class="detail__item" v-if="selectedOrder.startTime">
              <span class="detail__label">计划开始</span>
              <span class="detail__value">{{ formatTime(selectedOrder.startTime) }}</span>
            </div>
            <div class="detail__item" v-if="selectedOrder.endTime">
              <span class="detail__label">计划结束</span>
              <span class="detail__value">{{ formatTime(selectedOrder.endTime) }}</span>
            </div>
          </div>
        </div>
      </transition>
    </template>

    <!-- 空状态 -->
    <div v-else class="result-view__empty fade-in stagger-1">
      <div class="empty-state">
        <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="1" width="64" height="64">
          <path d="M3 3v18h18"/>
          <path d="M7 16l4-4 3 3 5-6" stroke-width="1.5"/>
        </svg>
        <p>执行求解后，排程结果将在此展示</p>
      </div>
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

const scoreText = computed(() => {
  const s = store.score
  if (!s) return '—'
  if (typeof s === 'string') return s
  return `${s.hardScore ?? 0}hard / ${s.mediumScore ?? 0}medium / ${s.softScore ?? 0}soft`
})

function formatTime(t) {
  if (!t) return '—'
  const d = new Date(t)
  return `${(d.getMonth() + 1).toString().padStart(2, '0')}/${d.getDate().toString().padStart(2, '0')} ${d.getHours().toString().padStart(2, '0')}:${d.getMinutes().toString().padStart(2, '0')}`
}

function supplyDays(order) {
  if (!order.monthlyShipment || order.monthlyShipment <= 0) return '∞'
  return ((order.currentInventory / order.monthlyShipment) * 30).toFixed(1)
}
</script>

<style scoped>
.result-view__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 20px;
  margin-bottom: 28px;
}

.result-view__title {
  font-size: 1.6rem;
  font-weight: 700;
  color: var(--text-primary);
}

.result-view__subtitle {
  font-size: 0.85rem;
  color: var(--text-muted);
  margin-top: 4px;
}

.result-view__gantt {
  margin-bottom: 28px;
}

.section-title {
  font-size: 1rem;
  font-weight: 600;
  color: var(--text-secondary);
  margin-bottom: 12px;
}

.line-cards {
  display: grid;
  gap: 20px;
}

.line-card__header {
  display: flex;
  justify-content: space-between;
  align-items: center;
  margin-bottom: 14px;
}

.line-card__header h3 {
  font-size: 1.05rem;
  color: var(--text-primary);
}

.line-card__empty {
  text-align: center;
  padding: 20px;
  color: var(--text-muted);
  font-style: italic;
}

.row--selected {
  background: var(--cyan-glow) !important;
}

/* 详情面板 */
.result-view__detail {
  margin-top: 20px;
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

/* 空状态 */
.empty-state {
  text-align: center;
  padding: 80px 20px;
  color: var(--text-muted);
}

.empty-state svg {
  margin-bottom: 16px;
  opacity: 0.3;
}

.empty-state p {
  font-size: 0.95rem;
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
