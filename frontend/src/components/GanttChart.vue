<template>
  <div class="gantt">
    <div class="gantt__header">
      <div class="gantt__lane-label"></div>
      <div class="gantt__timeline" ref="timelineRef">
        <div
          class="gantt__tick"
          v-for="tick in timeTicks"
          :key="tick.offset"
          :style="{ left: tick.offset + '%' }"
        >
          <span class="gantt__tick-label">{{ tick.label }}</span>
        </div>
      </div>
    </div>

    <div
      class="gantt__lane"
      v-for="line in lines"
      :key="line.id"
    >
      <div class="gantt__lane-label">
        <span class="gantt__lane-name">{{ line.name }}</span>
        <span class="gantt__lane-code">{{ line.lineCode }}</span>
      </div>
      <div class="gantt__lane-track">
        <div
          class="gantt__block"
          v-for="order in line.orders"
          :key="order.id"
          :style="getBlockStyle(order)"
          :class="{ 'gantt__block--selected': selectedOrderId === order.id }"
          @click="$emit('select-order', order)"
          :title="`${order.productCode} t=${order.thickness}`"
        >
          <span class="gantt__block-label">{{ order.productCode }}</span>
          <span class="gantt__block-thickness">{{ order.thickness }}μm</span>
        </div>
        <div
          v-if="!line.orders || line.orders.length === 0"
          class="gantt__empty"
        >
          暂无排程
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  lines: { type: Array, default: () => [] },
  selectedOrderId: { type: String, default: null }
})

defineEmits(['select-order'])

// 计算时间范围
const timeRange = computed(() => {
  let minTime = Infinity
  let maxTime = -Infinity

  for (const line of props.lines) {
    for (const order of (line.orders || [])) {
      if (order.startTime) {
        const start = new Date(order.startTime).getTime()
        const end = new Date(order.endTime).getTime()
        if (start < minTime) minTime = start
        if (end > maxTime) maxTime = end
      }
    }
  }

  if (minTime === Infinity) {
    // 默认展示一周
    const now = new Date()
    now.setHours(0, 0, 0, 0)
    minTime = now.getTime()
    maxTime = minTime + 7 * 24 * 60 * 60 * 1000
  }

  // 添加两端 padding
  const range = maxTime - minTime
  return {
    start: minTime - range * 0.02,
    end: maxTime + range * 0.05,
    duration: (maxTime - minTime) * 1.07
  }
})

const timeTicks = computed(() => {
  const { start, end } = timeRange.value
  const duration = end - start
  const tickCount = 8
  const step = duration / tickCount
  const ticks = []

  for (let i = 0; i <= tickCount; i++) {
    const time = new Date(start + step * i)
    ticks.push({
      offset: (i / tickCount) * 100,
      label: `${(time.getMonth() + 1).toString().padStart(2, '0')}/${time.getDate().toString().padStart(2, '0')} ${time.getHours().toString().padStart(2, '0')}:00`
    })
  }
  return ticks
})

// 颜色映射
const colorMap = {
  'T10ESY': '#00d4ff',
  'T9EST': '#10b981',
  'T24DJX': '#f59e0b',
  'T24DJY': '#f97316',
  'T29DJX': '#8b5cf6',
  'T29DJY': '#a78bfa',
  'T29QDJY': '#ec4899',
  'T42DJX': '#06b6d4',
  'T61ESYH': '#14b8a6',
  'T19EST': '#eab308',
  'T4FDX': '#f43f5e',
  'T4FDY': '#fb7185',
  'T5FDX': '#d946ef',
  'T7FDX': '#c084fc',
}

function getColor(productCode) {
  return colorMap[productCode] || '#6366f1'
}

function getBlockStyle(order) {
  if (!order.startTime || !order.endTime) return { display: 'none' }

  const { start, duration } = timeRange.value
  const orderStart = new Date(order.startTime).getTime()
  const orderEnd = new Date(order.endTime).getTime()

  const left = ((orderStart - start) / duration) * 100
  const width = ((orderEnd - orderStart) / duration) * 100

  const color = getColor(order.productCode)

  return {
    left: `${Math.max(0, left)}%`,
    width: `${Math.max(1, width)}%`,
    '--block-color': color,
    background: `linear-gradient(135deg, ${color}22, ${color}44)`,
    borderLeft: `3px solid ${color}`,
    color: color
  }
}
</script>

<style scoped>
.gantt {
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius-md);
  overflow: hidden;
}

.gantt__header {
  display: flex;
  border-bottom: 1px solid var(--border);
}

.gantt__lane-label {
  width: 120px;
  flex-shrink: 0;
  padding: 10px 16px;
  font-family: var(--font-heading);
  font-size: 0.8rem;
  font-weight: 500;
  color: var(--text-secondary);
  border-right: 1px solid var(--border);
  display: flex;
  flex-direction: column;
  justify-content: center;
  gap: 2px;
}

.gantt__lane-name {
  color: var(--text-primary);
  font-weight: 600;
}

.gantt__lane-code {
  font-family: var(--font-mono);
  font-size: 0.65rem;
  color: var(--text-muted);
}

.gantt__timeline {
  flex: 1;
  position: relative;
  height: 36px;
  background: var(--bg-primary);
}

.gantt__tick {
  position: absolute;
  top: 0;
  bottom: 0;
  border-left: 1px solid var(--border);
}

.gantt__tick-label {
  position: absolute;
  top: 8px;
  left: 6px;
  font-family: var(--font-mono);
  font-size: 0.62rem;
  color: var(--text-muted);
  white-space: nowrap;
}

.gantt__lane {
  display: flex;
  border-bottom: 1px solid var(--border);
  min-height: 60px;
}

.gantt__lane:last-child {
  border-bottom: none;
}

.gantt__lane-track {
  flex: 1;
  position: relative;
  padding: 8px 0;
  background:
    repeating-linear-gradient(
      90deg,
      transparent 0%,
      transparent 12.499%,
      rgba(255, 255, 255, 0.015) 12.5%,
      rgba(255, 255, 255, 0.015) 12.51%,
      transparent 12.51%,
      transparent 25%
    );
}

.gantt__block {
  position: absolute;
  top: 8px;
  bottom: 8px;
  border-radius: 4px;
  display: flex;
  align-items: center;
  gap: 6px;
  padding: 0 10px;
  font-family: var(--font-mono);
  font-size: 0.72rem;
  font-weight: 500;
  cursor: pointer;
  transition: all var(--transition-fast);
  overflow: hidden;
  white-space: nowrap;
}

.gantt__block:hover {
  transform: scaleY(1.1);
  z-index: 10;
  box-shadow: 0 0 16px rgba(0, 0, 0, 0.4);
}

.gantt__block--selected {
  transform: scaleY(1.15);
  z-index: 11;
  box-shadow: 0 0 20px var(--cyan-glow-strong);
  outline: 1px solid var(--cyan);
}

.gantt__block-label {
  font-weight: 600;
}

.gantt__block-thickness {
  font-size: 0.62rem;
  opacity: 0.7;
}

.gantt__empty {
  display: flex;
  align-items: center;
  justify-content: center;
  height: 100%;
  min-height: 44px;
  font-family: var(--font-heading);
  font-size: 0.8rem;
  color: var(--text-muted);
  font-style: italic;
}
</style>
