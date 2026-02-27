import { createRouter, createWebHistory } from 'vue-router'

const routes = [
  {
    path: '/',
    name: 'Dashboard',
    component: () => import('../views/DashboardView.vue')
  },
  {
    path: '/orders',
    name: 'Orders',
    component: () => import('../views/OrdersView.vue')
  },
  {
    path: '/result',
    name: 'Result',
    component: () => import('../views/ScheduleResultView.vue')
  }
]

const router = createRouter({
  history: createWebHistory(),
  routes
})

export default router
