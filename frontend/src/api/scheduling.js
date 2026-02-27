const API_BASE = '/api/scheduling'

export async function solveProblem(problem) {
    const response = await fetch(`${API_BASE}/solve`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(problem)
    })
    if (!response.ok) throw new Error(`求解失败: ${response.statusText}`)
    return response.json()
}

export async function solveAsync(problem) {
    const response = await fetch(`${API_BASE}/solve-async`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify(problem)
    })
    if (!response.ok) throw new Error(`异步求解启动失败: ${response.statusText}`)
    return response.json()
}

export async function getSolverStatus(jobId) {
    const response = await fetch(`${API_BASE}/status/${jobId}`)
    if (!response.ok) throw new Error(`状态查询失败: ${response.statusText}`)
    return response.json()
}

export async function stopSolver(jobId) {
    const response = await fetch(`${API_BASE}/stop/${jobId}`, { method: 'DELETE' })
    if (!response.ok) throw new Error(`终止失败: ${response.statusText}`)
    return response.json()
}
