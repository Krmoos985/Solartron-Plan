<template>
  <div class="orders-view">
    <header class="orders-view__header fade-in">
      <div>
        <h1 class="orders-view__title">订单管理</h1>
        <p class="orders-view__subtitle">管理母卷生产订单 · 共 {{ store.totalOrders }} 条</p>
      </div>
      <div class="orders-view__actions">
        <button class="btn" @click="store.loadDemoData()">加载演示数据</button>
        <button class="btn btn--primary" @click="showForm = true">
          + 新增订单
        </button>
      </div>
    </header>

    <!-- 新增/编辑表单 -->
    <transition name="slide">
      <div v-if="showForm" class="order-form card card--glow fade-in">
        <h3 class="order-form__title">{{ editingOrder ? '编辑订单' : '新增订单' }}</h3>
        <div class="order-form__grid">
          <div class="form-group">
            <label>型号 <span class="required">*</span></label>
            <input v-model="form.productCode" class="input" placeholder="如 T10ESY" />
          </div>
          <div class="form-group">
            <label>配方编码 <span class="required">*</span></label>
            <input v-model="form.formulaCode" class="input" placeholder="如 F001" />
          </div>
          <div class="form-group">
            <label>厚度 (μm) <span class="required">*</span></label>
            <input v-model.number="form.thickness" type="number" class="input" placeholder="188" />
          </div>
          <div class="form-group">
            <label>数量</label>
            <input v-model.number="form.quantity" type="number" class="input" placeholder="50" />
          </div>
          <div class="form-group">
            <label>当前库存</label>
            <input v-model.number="form.currentInventory" type="number" class="input" placeholder="120" />
          </div>
          <div class="form-group">
            <label>月均出货量</label>
            <input v-model.number="form.monthlyShipment" type="number" class="input" placeholder="200" />
          </div>
          <div class="form-group">
            <label>期望开始时间</label>
            <input v-model="form.expectedStartTime" type="datetime-local" class="input" />
          </div>
          <div class="form-group">
            <label>生产时长 (h)</label>
            <input v-model.number="form.productionDurationHours" type="number" class="input" placeholder="24" />
          </div>
          <div class="form-group form-group--wide">
            <label>兼容产线</label>
            <div class="checkbox-group">
              <label class="checkbox-label" v-for="line in store.productionLines" :key="line.lineCode">
                <input
                  type="checkbox"
                  :value="line.lineCode"
                  v-model="form.compatibleLines"
                />
                {{ line.name }} ({{ line.lineCode }})
              </label>
            </div>
          </div>
        </div>
        <div class="order-form__footer">
          <button class="btn" @click="showForm = false; editingOrder = null">取消</button>
          <button class="btn btn--primary" @click="submitForm">确认</button>
        </div>
      </div>
    </transition>

    <!-- 订单列表 -->
    <div class="orders-table card fade-in stagger-1">
      <div class="table-wrap">
        <table v-if="store.orders.length > 0">
          <thead>
            <tr>
              <th>ID</th>
              <th>型号</th>
              <th>配方</th>
              <th>厚度</th>
              <th>数量</th>
              <th>库存天数</th>
              <th>生产时长</th>
              <th>兼容产线</th>
              <th>操作</th>
            </tr>
          </thead>
          <tbody>
            <tr v-for="order in store.orders" :key="order.id">
              <td>
                <span class="tag">{{ order.id }}</span>
              </td>
              <td>
                <span class="tag tag--cyan">{{ order.productCode }}</span>
              </td>
              <td>{{ order.formulaCode }}</td>
              <td>{{ order.thickness }}μm</td>
              <td>{{ order.quantity }}</td>
              <td>
                <span :class="supplyDaysClass(order)">
                  {{ supplyDays(order) }}天
                </span>
              </td>
              <td>{{ order.productionDurationHours }}h</td>
              <td>
                <span
                  class="tag"
                  v-for="line in order.compatibleLines"
                  :key="line"
                  style="margin-right: 4px"
                >{{ line }}</span>
              </td>
              <td>
                <button
                  class="btn btn--sm btn--danger"
                  @click="store.removeOrder(order.id)"
                >删除</button>
              </td>
            </tr>
          </tbody>
        </table>
        <div v-else class="orders-empty">
          <p>暂无订单</p>
          <p class="orders-empty__hint">点击"加载演示数据"快速体验，或手动新增订单</p>
        </div>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue'
import { useSchedulingStore } from '../stores/scheduling'

const store = useSchedulingStore()
const showForm = ref(false)
const editingOrder = ref(null)

const defaultForm = () => ({
  productCode: '',
  formulaCode: '',
  thickness: null,
  quantity: null,
  currentInventory: null,
  monthlyShipment: null,
  expectedStartTime: '',
  productionDurationHours: null,
  compatibleLines: ['LINE_1', 'LINE_2']
})

const form = reactive(defaultForm())

function submitForm() {
  if (!form.productCode || !form.formulaCode || !form.thickness) return
  store.addOrder({ ...form })
  Object.assign(form, defaultForm())
  showForm.value = false
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
</script>

<style scoped>
.orders-view__header {
  display: flex;
  justify-content: space-between;
  align-items: flex-start;
  flex-wrap: wrap;
  gap: 16px;
  margin-bottom: 24px;
}

.orders-view__title {
  font-size: 1.6rem;
  font-weight: 700;
  color: var(--text-primary);
}

.orders-view__subtitle {
  font-size: 0.85rem;
  color: var(--text-muted);
  margin-top: 4px;
}

.orders-view__actions {
  display: flex;
  gap: 10px;
}

/* 表单 */
.order-form {
  margin-bottom: 24px;
}

.order-form__title {
  font-size: 1.1rem;
  margin-bottom: 20px;
  color: var(--text-primary);
}

.order-form__grid {
  display: grid;
  grid-template-columns: repeat(auto-fill, minmax(220px, 1fr));
  gap: 16px;
}

.form-group {
  display: flex;
  flex-direction: column;
  gap: 6px;
}

.form-group label {
  font-family: var(--font-heading);
  font-size: 0.78rem;
  color: var(--text-secondary);
  font-weight: 500;
}

.form-group--wide {
  grid-column: 1 / -1;
}

.required {
  color: var(--red);
}

.checkbox-group {
  display: flex;
  gap: 20px;
  flex-wrap: wrap;
}

.checkbox-label {
  display: flex;
  align-items: center;
  gap: 6px;
  font-family: var(--font-mono);
  font-size: 0.8rem;
  color: var(--text-secondary);
  cursor: pointer;
}

.checkbox-label input[type="checkbox"] {
  accent-color: var(--cyan);
}

.order-form__footer {
  display: flex;
  justify-content: flex-end;
  gap: 10px;
  margin-top: 20px;
  padding-top: 16px;
  border-top: 1px solid var(--border);
}

/* 空状态 */
.orders-empty {
  text-align: center;
  padding: 48px 20px;
  color: var(--text-muted);
}

.orders-empty p {
  font-size: 1rem;
  margin-bottom: 8px;
}

.orders-empty__hint {
  font-size: 0.8rem;
  color: var(--text-muted);
}

/* 颜色辅助 */
.text-red { color: var(--red); }
.text-amber { color: var(--amber); }
.text-emerald { color: var(--emerald); }

/* 过渡 */
.slide-enter-active,
.slide-leave-active {
  transition: all 0.3s cubic-bezier(0.16, 1, 0.3, 1);
}

.slide-enter-from,
.slide-leave-to {
  opacity: 0;
  transform: translateY(-8px);
}
</style>
