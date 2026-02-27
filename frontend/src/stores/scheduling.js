import { defineStore } from 'pinia'
import { ref, computed } from 'vue'
import * as api from '../api/scheduling'

export const useSchedulingStore = defineStore('scheduling', () => {
    // === 状态 ===
    const productionLines = ref([
        {
            id: 'LINE_1',
            name: '一线',
            lineCode: 'LINE_1',
            availableFrom: new Date().toISOString().slice(0, 16),
            orders: []
        },
        {
            id: 'LINE_2',
            name: '二线',
            lineCode: 'LINE_2',
            availableFrom: new Date().toISOString().slice(0, 16),
            orders: []
        }
    ])

    const orders = ref([])
    const solution = ref(null)
    const solverStatus = ref('NOT_SOLVING') // NOT_SOLVING, SOLVING, TERMINATED
    const jobId = ref(null)
    const error = ref(null)
    const isLoading = ref(false)

    // === 计算属性 ===
    const score = computed(() => {
        if (!solution.value?.score) return null
        return solution.value.score
    })

    const solvedLines = computed(() => {
        if (!solution.value?.productionLines) return []
        return solution.value.productionLines
    })

    const totalOrders = computed(() => orders.value.length)

    // === 操作 ===

    function addOrder(order) {
        orders.value.push({
            ...order,
            id: `ORDER_${Date.now()}_${Math.random().toString(36).slice(2, 6)}`
        })
    }

    function removeOrder(id) {
        orders.value = orders.value.filter(o => o.id !== id)
    }

    function clearOrders() {
        orders.value = []
    }

    function buildProblem() {
        return {
            productionLines: productionLines.value.map(line => ({
                id: line.id,
                name: line.name,
                lineCode: line.lineCode,
                availableFrom: line.availableFrom,
                orders: []
            })),
            orders: orders.value.map(o => ({
                ...o,
                assignedLine: null,
                sequenceIndex: null,
                previousOrder: null,
                startTime: null,
                endTime: null
            }))
        }
    }

    async function solve() {
        error.value = null
        isLoading.value = true
        solverStatus.value = 'SOLVING'
        try {
            const result = await api.solveProblem(buildProblem())
            solution.value = result
            solverStatus.value = 'NOT_SOLVING'
        } catch (e) {
            error.value = e.message
            solverStatus.value = 'NOT_SOLVING'
        } finally {
            isLoading.value = false
        }
    }

    async function solveAsync() {
        error.value = null
        isLoading.value = true
        solverStatus.value = 'SOLVING'
        try {
            const result = await api.solveAsync(buildProblem())
            jobId.value = result.jobId
        } catch (e) {
            error.value = e.message
            solverStatus.value = 'NOT_SOLVING'
            isLoading.value = false
        }
    }

    async function pollStatus() {
        if (!jobId.value) return
        try {
            const result = await api.getSolverStatus(jobId.value)
            solverStatus.value = result.status
            if (result.solution && typeof result.solution === 'object') {
                solution.value = result.solution
            }
            if (result.status === 'NOT_SOLVING') {
                isLoading.value = false
            }
        } catch (e) {
            error.value = e.message
        }
    }

    async function stopSolving() {
        if (!jobId.value) return
        try {
            await api.stopSolver(jobId.value)
            solverStatus.value = 'TERMINATED'
            isLoading.value = false
        } catch (e) {
            error.value = e.message
        }
    }

    function loadDemoData() {
        const demoOrders = [
            { productCode: 'T10ESY', formulaCode: 'F001', thickness: 188, quantity: 50, currentInventory: 120, monthlyShipment: 200, expectedStartTime: '2026-03-01T08:00', compatibleLines: ['LINE_1', 'LINE_2'], productionDurationHours: 24 },
            { productCode: 'T10ESY', formulaCode: 'F001', thickness: 150, quantity: 40, currentInventory: 80, monthlyShipment: 180, expectedStartTime: '2026-03-02T08:00', compatibleLines: ['LINE_1', 'LINE_2'], productionDurationHours: 20 },
            { productCode: 'T9EST', formulaCode: 'F002', thickness: 225, quantity: 60, currentInventory: 50, monthlyShipment: 150, expectedStartTime: '2026-03-01T08:00', compatibleLines: ['LINE_1', 'LINE_2'], productionDurationHours: 30 },
            { productCode: 'T9EST', formulaCode: 'F002', thickness: 188, quantity: 35, currentInventory: 100, monthlyShipment: 120, expectedStartTime: '2026-03-03T08:00', compatibleLines: ['LINE_1', 'LINE_2'], productionDurationHours: 18 },
            { productCode: 'T24DJX', formulaCode: 'F003', thickness: 100, quantity: 45, currentInventory: 30, monthlyShipment: 160, expectedStartTime: '2026-03-01T08:00', compatibleLines: ['LINE_1', 'LINE_2'], productionDurationHours: 22 },
            { productCode: 'T24DJY', formulaCode: 'F003', thickness: 120, quantity: 30, currentInventory: 90, monthlyShipment: 100, expectedStartTime: '2026-03-04T08:00', compatibleLines: ['LINE_1', 'LINE_2'], productionDurationHours: 15 },
            { productCode: 'T29DJX', formulaCode: 'F004', thickness: 150, quantity: 55, currentInventory: 40, monthlyShipment: 200, expectedStartTime: '2026-03-02T08:00', compatibleLines: ['LINE_1', 'LINE_2'], productionDurationHours: 28 },
            { productCode: 'T29DJY', formulaCode: 'F004', thickness: 180, quantity: 25, currentInventory: 200, monthlyShipment: 80, expectedStartTime: '2026-03-05T08:00', compatibleLines: ['LINE_1', 'LINE_2'], productionDurationHours: 12 },
            { productCode: 'T29QDJY', formulaCode: 'F005', thickness: 160, quantity: 38, currentInventory: 60, monthlyShipment: 140, expectedStartTime: '2026-03-01T08:00', compatibleLines: ['LINE_2'], productionDurationHours: 20 },
            { productCode: 'T61ESYH', formulaCode: 'F006', thickness: 200, quantity: 42, currentInventory: 70, monthlyShipment: 130, expectedStartTime: '2026-03-03T08:00', compatibleLines: ['LINE_1', 'LINE_2'], productionDurationHours: 22 },
        ]
        orders.value = demoOrders.map((o, i) => ({
            ...o,
            id: `DEMO_${String(i + 1).padStart(3, '0')}`
        }))
    }

    return {
        productionLines, orders, solution, solverStatus, jobId, error, isLoading,
        score, solvedLines, totalOrders,
        addOrder, removeOrder, clearOrders,
        solve, solveAsync, pollStatus, stopSolving, loadDemoData
    }
})
