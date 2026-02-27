<template>
  <div class="scoreboard" :class="{ 'scoreboard--has-score': score }">
    <div class="scoreboard__item" v-for="item in scoreItems" :key="item.label">
      <span class="scoreboard__label">{{ item.label }}</span>
      <span
        class="scoreboard__value"
        :class="item.class"
      >{{ item.value }}</span>
    </div>
  </div>
</template>

<script setup>
import { computed } from 'vue'

const props = defineProps({
  score: { type: Object, default: null }
})

const scoreItems = computed(() => {
  if (!props.score) {
    return [
      { label: 'Hard', value: '—', class: '' },
      { label: 'Medium', value: '—', class: '' },
      { label: 'Soft', value: '—', class: '' }
    ]
  }

  const s = props.score
  // Timefold score 格式可能是 "0hard/-3medium/-47soft" 或者对象
  let hard = 0, medium = 0, soft = 0
  if (typeof s === 'string') {
    const m = s.match(/(-?\d+)hard\/(-?\d+)medium\/(-?\d+)soft/)
    if (m) { hard = +m[1]; medium = +m[2]; soft = +m[3] }
  } else {
    hard = s.hardScore ?? s.hard ?? 0
    medium = s.mediumScore ?? s.medium ?? 0
    soft = s.softScore ?? s.soft ?? 0
  }

  return [
    {
      label: 'Hard',
      value: hard,
      class: hard < 0 ? 'scoreboard__value--bad' : 'scoreboard__value--good'
    },
    {
      label: 'Medium',
      value: medium,
      class: medium < 0 ? 'scoreboard__value--warn' : 'scoreboard__value--good'
    },
    {
      label: 'Soft',
      value: soft,
      class: 'scoreboard__value--soft'
    }
  ]
})
</script>

<style scoped>
.scoreboard {
  display: flex;
  gap: 16px;
}

.scoreboard__item {
  display: flex;
  flex-direction: column;
  align-items: center;
  padding: 12px 20px;
  background: var(--bg-secondary);
  border: 1px solid var(--border);
  border-radius: var(--radius-sm);
  min-width: 100px;
  transition: all var(--transition-normal);
}

.scoreboard--has-score .scoreboard__item {
  border-color: var(--border-hover);
}

.scoreboard__label {
  font-family: var(--font-heading);
  font-size: 0.7rem;
  font-weight: 500;
  text-transform: uppercase;
  letter-spacing: 0.08em;
  color: var(--text-muted);
  margin-bottom: 4px;
}

.scoreboard__value {
  font-family: var(--font-mono);
  font-size: 1.4rem;
  font-weight: 600;
  color: var(--text-secondary);
}

.scoreboard__value--good {
  color: var(--emerald);
  text-shadow: 0 0 8px rgba(16, 185, 129, 0.3);
}

.scoreboard__value--bad {
  color: var(--red);
  text-shadow: 0 0 8px rgba(239, 68, 68, 0.3);
}

.scoreboard__value--warn {
  color: var(--amber);
  text-shadow: 0 0 8px rgba(255, 176, 32, 0.3);
}

.scoreboard__value--soft {
  color: var(--cyan);
}
</style>
