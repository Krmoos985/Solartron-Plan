const BASE_URL = '/api/v1/scheduling';
const HOUR_MS = 1000 * 60 * 60;
const DAY_MS = 1000 * 60 * 60 * 24;
const GANTT_PX_PER_HOUR = 3.2;
const GANTT_MIN_TASK_WIDTH = 42;
const GANTT_LABEL_WIDTH = 132;

const constraintBlueprints = [
  { key: 'hc1', id: 'HC1-产品产线兼容', code: 'HC1', label: '产线兼容', level: 'Hard', description: '禁止把订单分配到不兼容产线。', defaultEnabled: true },
  { key: 'hc2', id: 'HC2-紧急库存优先', code: 'HC2', label: '紧急库存优先', level: 'Hard', description: '库存覆盖天数低于 10 天的订单要靠前。', defaultEnabled: false },
  { key: 'hc3', id: 'HC3-停机冲突', code: 'HC3', label: '停机冲突', level: 'Hard', description: '排程时间不能和停机窗口重叠。', defaultEnabled: false },
  { key: 'hc4', id: 'HC4-厚度单峰波浪', code: 'HC4', label: '厚度单峰', level: 'Hard', description: '同线厚度变化保持单峰轮转。', defaultEnabled: false },
  { key: 'mc1', id: 'MC1-过滤器后20天优先顺序', code: 'MC1', label: '过滤器优先顺序', level: 'Medium', description: '过滤器更换后 20 天内执行预设型号顺序。', defaultEnabled: false },
  { key: 'mc2', id: 'MC2-高库存不前移', code: 'MC2', label: '高库存不前移', level: 'Medium', description: '高库存订单不应过早排产。', defaultEnabled: false },
  { key: 'sc1', id: 'SC1-换型最小化', code: 'SC1', label: '换型最小化', level: 'Soft', description: '当前主链路，最小化配方/型号/厚度换型时间。', defaultEnabled: true },
  { key: 'sc2', id: 'SC2-库存天数优先级', code: 'SC2', label: '库存天数优先级', level: 'Soft', description: '同型号内优先低库存覆盖天数订单。', defaultEnabled: false },
  { key: 'sc3', id: 'SC3-期望时间偏差', code: 'SC3', label: '期望时间偏差', level: 'Soft', description: '延迟越多，惩罚越高。', defaultEnabled: false },
  { key: 'sc4', id: 'SC4-特定产线偏好', code: 'SC4', label: '偏好产线', level: 'Soft', description: '尽量把订单放在偏好产线上。', defaultEnabled: false },
  { key: null, id: 'HC5-子任务保序', code: 'HC5', label: '子任务保序', level: 'Locked', description: '当前 Excel 模式未启用按天拆分。', defaultEnabled: false },
  { key: null, id: 'SC5-拆分子任务连续生产', code: 'SC5', label: '拆分连续生产', level: 'Locked', description: '当前 Excel 模式未启用按天拆分。', defaultEnabled: false }
];

const state = {
  file: null,
  validation: null,
  activeJobId: null,
  pollTimer: null,
  selectedTaskKey: null,
  ganttTasks: new Map()
};

const elements = {
  fileInput: document.getElementById('excel-file'),
  fileName: document.getElementById('file-name'),
  startTime: document.getElementById('start-time'),
  validationState: document.getElementById('validation-state'),
  systemPill: document.getElementById('system-pill'),
  systemStatus: document.getElementById('system-status'),
  executionMessage: document.getElementById('execution-message'),
  summary: {
    orders: document.getElementById('metric-orders'),
    dualLines: document.getElementById('metric-dual-lines'),
    urgent: document.getElementById('metric-urgent'),
    highStock: document.getElementById('metric-high-stock'),
    exceptions: document.getElementById('metric-exceptions'),
    filterPlans: document.getElementById('metric-filter-plans')
  },
  warningList: document.getElementById('warning-list'),
  constraintGrid: document.getElementById('constraint-grid'),
  resultScore: document.getElementById('result-score'),
  resultFeasible: document.getElementById('result-feasible'),
  resultUnassigned: document.getElementById('result-unassigned'),
  resultJobId: document.getElementById('result-jobid'),
  lineContainer: document.getElementById('line-container'),
  lineStats: document.getElementById('line-stats'),
  terminationTime: document.getElementById('termination-time'),
  terminationUnimproved: document.getElementById('termination-unimproved'),
  terminationSteps: document.getElementById('termination-steps'),
  terminationFeasible: document.getElementById('termination-feasible'),
  buttons: {
    useValidationSample: document.getElementById('btn-use-validation-sample'),
    validate: document.getElementById('btn-validate'),
    clear: document.getElementById('btn-clear'),
    sync: document.getElementById('btn-sync'),
    async: document.getElementById('btn-async'),
    stop: document.getElementById('btn-stop')
  }
};

const setStatus = (stateName, text) => {
  elements.systemPill.dataset.state = stateName;
  elements.systemStatus.textContent = text;
  elements.executionMessage.textContent = text;
};

const resetResult = () => {
  elements.resultScore.textContent = '--';
  elements.resultFeasible.textContent = '--';
  elements.resultUnassigned.textContent = '--';
  elements.resultJobId.textContent = '--';
  state.selectedTaskKey = null;
  state.ganttTasks = new Map();
  elements.lineContainer.className = 'line-container empty-block';
  elements.lineContainer.textContent = '等待执行排程。';
  elements.lineStats.className = 'line-stats empty-block';
  elements.lineStats.textContent = '暂无产线结果。';
};

const formatDateTime = (value) => {
  if (!value) {
    return '--';
  }
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) {
    return value;
  }
  return `${String(date.getMonth() + 1).padStart(2, '0')}/${String(date.getDate()).padStart(2, '0')} ${String(date.getHours()).padStart(2, '0')}:${String(date.getMinutes()).padStart(2, '0')}`;
};

const formatDurationHours = (start, end) => {
  if (!start || !end) {
    return '--';
  }
  const startDate = new Date(start);
  const endDate = new Date(end);
  if (Number.isNaN(startDate.getTime()) || Number.isNaN(endDate.getTime())) {
    return '--';
  }
  const hours = Math.max(0, (endDate - startDate) / (1000 * 60 * 60));
  return `${hours.toFixed(1)}h`;
};

const parseDateValue = (value) => {
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
};

const startOfDay = (date) => {
  const copy = new Date(date);
  copy.setHours(0, 0, 0, 0);
  return copy;
};

const endOfNextDay = (date) => {
  const copy = startOfDay(date);
  copy.setDate(copy.getDate() + 1);
  return copy;
};

const formatAxisLabel = (date) => {
  return `${String(date.getMonth() + 1).padStart(2, '0')}/${String(date.getDate()).padStart(2, '0')}`;
};

const escapeHtml = (value) => {
  return String(value)
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll('"', '&quot;')
    .replaceAll("'", '&#39;');
};

const formatCoverage = (task) => {
  return `${task.daysCovered ?? '--'}/${task.totalDays ?? '--'} 天`;
};

const describeTaskMode = (task) => {
  const totalDays = Number(task.totalDays ?? 0);
  const daysCovered = Number(task.daysCovered ?? 0);

  if (totalDays > 1 && daysCovered < totalDays) {
    return '拆分片段';
  }
  if (totalDays > 1) {
    return '完整母任务';
  }
  return '单日任务';
};

const createTaskKey = (task) => {
  return [task.lineId ?? '', task.originalTaskId ?? '', task.plannedStart ?? '', task.plannedEnd ?? ''].join('::');
};

const renderTaskInspector = (task) => {
  const inspector = elements.lineContainer.querySelector('.gantt-inspector');
  if (!inspector) {
    return;
  }

  if (!task) {
    inspector.className = 'gantt-inspector empty';
    inspector.innerHTML = '<p>点击甘特图上的任务块，查看这个任务的详细信息。</p>';
    return;
  }

  inspector.className = 'gantt-inspector';
  inspector.innerHTML = `
    <div class="gantt-inspector-head">
      <div>
        <p class="gantt-inspector-kicker">Task Detail</p>
        <h3>${escapeHtml(task.originalTaskId)}</h3>
      </div>
      <span class="gantt-inspector-badge">${escapeHtml(task.lineId)}</span>
    </div>
    <div class="gantt-inspector-grid">
      <div class="gantt-inspector-tile">
        <span>产线</span>
        <strong>${escapeHtml(task.lineId)}</strong>
      </div>
      <div class="gantt-inspector-tile">
        <span>线内顺位</span>
        <strong>#${task.sequence ?? '--'}</strong>
      </div>
      <div class="gantt-inspector-tile">
        <span>开始时间</span>
        <strong>${formatDateTime(task.plannedStart)}</strong>
      </div>
      <div class="gantt-inspector-tile">
        <span>结束时间</span>
        <strong>${formatDateTime(task.plannedEnd)}</strong>
      </div>
      <div class="gantt-inspector-tile">
        <span>持续时长</span>
        <strong>${formatDurationHours(task.plannedStart, task.plannedEnd)}</strong>
      </div>
      <div class="gantt-inspector-tile">
        <span>覆盖天数</span>
        <strong>${formatCoverage(task)}</strong>
      </div>
      <div class="gantt-inspector-tile">
        <span>块类型</span>
        <strong>${describeTaskMode(task)}</strong>
      </div>
      <div class="gantt-inspector-tile wide">
        <span>时间窗口</span>
        <strong>${formatDateTime(task.plannedStart)} → ${formatDateTime(task.plannedEnd)}</strong>
      </div>
    </div>
  `;
};

const syncTaskSelection = (taskKey) => {
  state.selectedTaskKey = taskKey ?? null;

  elements.lineContainer.querySelectorAll('.gantt-task').forEach((button) => {
    const isSelected = button.dataset.taskKey === taskKey;
    button.classList.toggle('is-selected', isSelected);
    button.setAttribute('aria-pressed', String(isSelected));
  });

  renderTaskInspector(taskKey ? state.ganttTasks.get(taskKey) ?? null : null);
};

const createConstraintCard = (blueprint) => {
  const card = document.createElement('article');
  card.className = 'constraint-card';
  card.dataset.constraintId = blueprint.id;
  card.dataset.active = String(Boolean(blueprint.defaultEnabled));

  const switchMarkup = blueprint.key
    ? `<input class="constraint-switch" type="checkbox" id="constraint-${blueprint.key}" ${blueprint.defaultEnabled ? 'checked' : ''}>`
    : `<span class="badge locked">Locked</span>`;

  card.innerHTML = `
    <div class="constraint-head">
      <div>
        <p class="constraint-code">${blueprint.code}</p>
        <h3 class="constraint-title">${blueprint.label}</h3>
      </div>
      ${switchMarkup}
    </div>
    <div class="constraint-badges">
      <span class="badge">${blueprint.level}</span>
      <span class="badge status-pill">等待校验</span>
    </div>
    <div class="constraint-meta">${blueprint.description}</div>
    <div class="constraint-note">请先执行数据验证。</div>
  `;

  if (blueprint.key) {
    const checkbox = card.querySelector('.constraint-switch');
    checkbox.addEventListener('change', () => {
      card.dataset.active = String(checkbox.checked);
    });
  } else {
    card.dataset.available = 'false';
  }
  return card;
};

const renderConstraintGrid = () => {
  elements.constraintGrid.innerHTML = '';
  constraintBlueprints.forEach((blueprint) => {
    elements.constraintGrid.appendChild(createConstraintCard(blueprint));
  });
};

const applyConstraintStatus = (summary) => {
  const statusMap = new Map((summary?.constraintStatus || []).map((status) => [status.id, status]));
  document.querySelectorAll('.constraint-card').forEach((card) => {
    const status = statusMap.get(card.dataset.constraintId);
    const statusPill = card.querySelector('.status-pill');
    const note = card.querySelector('.constraint-note');
    const checkbox = card.querySelector('.constraint-switch');

    if (!status) {
      statusPill.textContent = '未提供';
      statusPill.className = 'badge status-pill warn';
      note.textContent = '当前未收到该约束的校验状态。';
      return;
    }

    const canUse = status.available && status.dataReady;
    card.dataset.available = String(status.available);

    if (status.available && status.dataReady) {
      statusPill.textContent = '可验证';
      statusPill.className = 'badge status-pill good';
    } else if (status.available) {
      statusPill.textContent = '数据不足';
      statusPill.className = 'badge status-pill warn';
    } else {
      statusPill.textContent = '当前关闭';
      statusPill.className = 'badge status-pill locked';
    }

    note.textContent = status.note;

    if (checkbox) {
      checkbox.disabled = !canUse;
      if (!canUse) {
        checkbox.checked = false;
      }
      card.dataset.active = String(checkbox.checked);
    }
  });
};

const renderWarnings = (warnings) => {
  const items = warnings && warnings.length ? warnings : ['当前数据未发现额外风险。'];
  elements.warningList.innerHTML = items.map((warning) => `<li>${warning}</li>`).join('');
};

const renderValidationSummary = (summary) => {
  state.validation = summary;
  elements.validationState.textContent = '已完成';

  const metrics = summary?.metrics || {};
  elements.summary.orders.textContent = metrics.parsedOrders ?? metrics.rawOrderRows ?? '--';
  elements.summary.dualLines.textContent = metrics.dualCompatibleOrders ?? '--';
  elements.summary.urgent.textContent = metrics.urgentInventoryOrders ?? '--';
  elements.summary.highStock.textContent = metrics.highInventoryOrders ?? '--';
  elements.summary.exceptions.textContent = metrics.exceptionWindows ?? '--';
  elements.summary.filterPlans.textContent = metrics.filterChangePlans ?? '--';

  renderWarnings(summary?.warnings || []);
  applyConstraintStatus(summary);
};

const renderLineStats = (lineStats) => {
  if (!lineStats || Object.keys(lineStats).length === 0) {
    elements.lineStats.className = 'line-stats empty-block';
    elements.lineStats.textContent = '暂无产线结果。';
    return;
  }

  elements.lineStats.className = 'line-stats';
  elements.lineStats.innerHTML = Object.entries(lineStats).map(([lineName, stats]) => `
    <div class="line-stat-card">
      <strong>${lineName}</strong>
      <span>任务数: ${stats.orderCount ?? '--'}</span>
      <span>首任务: ${formatDateTime(stats.firstStart)}</span>
      <span>末任务: ${formatDateTime(stats.lastEnd)}</span>
    </div>
  `).join('');
};

const renderLineTasks = (lineTasks) => {
  if (!lineTasks || Object.keys(lineTasks).length === 0) {
    state.selectedTaskKey = null;
    state.ganttTasks = new Map();
    elements.lineContainer.className = 'line-container empty-block';
    elements.lineContainer.textContent = '求解完成，但当前没有可展示的任务块。';
    return;
  }

  const sortedEntries = Object.entries(lineTasks)
    .sort(([left], [right]) => left.localeCompare(right))
    .map(([lineId, tasks]) => [
      lineId,
      tasks
        .map((task, index) => ({
          ...task,
          lineId: task.lineId || lineId,
          sequence: index + 1,
          start: parseDateValue(task.plannedStart),
          end: parseDateValue(task.plannedEnd),
          taskKey: createTaskKey({
            ...task,
            lineId: task.lineId || lineId
          })
        }))
        .filter((task) => task.start && task.end)
    ]);

  const allTasks = sortedEntries.flatMap(([, tasks]) => tasks);
  if (!allTasks.length) {
    state.selectedTaskKey = null;
    state.ganttTasks = new Map();
    elements.lineContainer.className = 'line-container empty-block';
    elements.lineContainer.textContent = '求解完成，但当前任务缺少有效时间轴信息。';
    return;
  }

  const earliestStart = startOfDay(new Date(Math.min(...allTasks.map((task) => task.start.getTime()))));
  const latestEnd = endOfNextDay(new Date(Math.max(...allTasks.map((task) => task.end.getTime()))));
  const totalHours = Math.max(24, Math.ceil((latestEnd - earliestStart) / HOUR_MS));
  const timelineWidth = Math.max(960, totalHours * GANTT_PX_PER_HOUR);
  const gridStep = 24 * GANTT_PX_PER_HOUR;

  const markers = [];
  for (let current = new Date(earliestStart); current <= latestEnd; current = new Date(current.getTime() + DAY_MS)) {
    const offsetHours = (current - earliestStart) / HOUR_MS;
    markers.push({
      label: formatAxisLabel(current),
      left: offsetHours * GANTT_PX_PER_HOUR
    });
  }

  state.ganttTasks = new Map(allTasks.map((task) => [task.taskKey, task]));
  const selectedTaskKey = state.selectedTaskKey && state.ganttTasks.has(state.selectedTaskKey)
    ? state.selectedTaskKey
    : allTasks[0].taskKey;
  state.selectedTaskKey = selectedTaskKey;

  elements.lineContainer.className = 'line-container gantt-shell';
  elements.lineContainer.innerHTML = `
    <div class="gantt-toolbar">
      <div class="gantt-summary">
        <strong>${sortedEntries.length} 条产线</strong>
        <span>共 ${allTasks.length} 个任务块</span>
      </div>
      <div class="gantt-hint">点击任务块看详情，按住横向拖拽可查看后续时间</div>
    </div>
    <div class="gantt-scroll">
      <div class="gantt-board" style="--label-width:${GANTT_LABEL_WIDTH}px; --timeline-width:${timelineWidth}px; --grid-step:${gridStep}px;">
        <div class="gantt-header-row">
          <div class="gantt-sticky-cell gantt-corner">产线</div>
          <div class="gantt-axis">
            ${markers.map((marker) => `
              <div class="gantt-marker" style="left:${marker.left}px;">
                <span>${marker.label}</span>
              </div>
            `).join('')}
          </div>
        </div>
        ${sortedEntries.map(([lineId, tasks]) => `
          <div class="gantt-row">
            <div class="gantt-sticky-cell gantt-line-label">
              <strong>${lineId}</strong>
              <span>${tasks.length} blocks</span>
            </div>
            <div class="gantt-track">
              ${tasks.map((task) => {
                const left = ((task.start - earliestStart) / HOUR_MS) * GANTT_PX_PER_HOUR;
                const width = Math.max(GANTT_MIN_TASK_WIDTH, ((task.end - task.start) / HOUR_MS) * GANTT_PX_PER_HOUR);
                const title = `${task.originalTaskId} | ${formatDateTime(task.plannedStart)} → ${formatDateTime(task.plannedEnd)} | 持续 ${formatDurationHours(task.plannedStart, task.plannedEnd)} | 覆盖 ${task.daysCovered}/${task.totalDays} 天`;
                return `
                  <button
                    class="gantt-task ${task.taskKey === selectedTaskKey ? 'is-selected' : ''}"
                    type="button"
                    style="left:${left}px; width:${width}px;"
                    title="${escapeHtml(title)}"
                    data-task-key="${escapeHtml(task.taskKey)}"
                    aria-pressed="${task.taskKey === selectedTaskKey ? 'true' : 'false'}"
                  >
                    <span class="gantt-task-id">${escapeHtml(task.originalTaskId)}</span>
                    <span class="gantt-task-meta">${task.daysCovered}/${task.totalDays}D</span>
                  </button>
                `;
              }).join('')}
            </div>
          </div>
        `).join('')}
      </div>
    </div>
    <div class="gantt-inspector"></div>
  `;

  const ganttScroller = elements.lineContainer.querySelector('.gantt-scroll');
  if (ganttScroller) {
    enableGanttDragScroll(ganttScroller);
  }

  elements.lineContainer.querySelectorAll('.gantt-task').forEach((button) => {
    button.addEventListener('click', () => {
      syncTaskSelection(button.dataset.taskKey);
    });
  });

  syncTaskSelection(selectedTaskKey);
};

const enableGanttDragScroll = (scroller) => {
  const dragThreshold = 6;
  let isPointerDown = false;
  let isDragging = false;
  let pointerId = null;
  let startX = 0;
  let startScrollLeft = 0;

  scroller.onpointerdown = (event) => {
    if (event.button !== 0) {
      return;
    }
    isPointerDown = true;
    pointerId = event.pointerId;
    startX = event.clientX;
    startScrollLeft = scroller.scrollLeft;
  };

  scroller.onpointermove = (event) => {
    if (!isPointerDown) {
      return;
    }
    const deltaX = event.clientX - startX;
    if (!isDragging && Math.abs(deltaX) >= dragThreshold) {
      isDragging = true;
      scroller.classList.add('is-dragging');
      try {
        scroller.setPointerCapture(pointerId);
      } catch (error) {
        // Ignore capture failures for unsupported pointers.
      }
    }
    if (!isDragging) {
      return;
    }
    scroller.scrollLeft = startScrollLeft - deltaX;
  };

  const stopDragging = () => {
    isPointerDown = false;
    isDragging = false;
    if (pointerId !== null) {
      try {
        scroller.releasePointerCapture(pointerId);
      } catch (error) {
        // Ignore capture release failures from ended pointers.
      }
    }
    pointerId = null;
    scroller.classList.remove('is-dragging');
  };

  scroller.onpointerup = stopDragging;
  scroller.onpointerleave = stopDragging;
  scroller.onpointercancel = stopDragging;
};

const renderSolveResult = (result, jobId = state.activeJobId) => {
  elements.resultScore.textContent = result?.score ?? '--';
  elements.resultFeasible.textContent = result?.isFeasible === undefined ? '--' : (result.isFeasible ? 'YES' : 'NO');
  elements.resultUnassigned.textContent = result?.unassignedCount ?? '--';
  elements.resultJobId.textContent = jobId ?? '--';
  renderLineTasks(result?.lineTasks || {});
  renderLineStats(result?.lineStats || {});

  if (result?.validation) {
    renderValidationSummary(result.validation);
  }
};

const ensureFileSelected = () => {
  if (!state.file) {
    throw new Error('请先选择要验证或求解的 Excel 文件。');
  }
};

const applySelectedFile = (file, statusText = '文件已加载，请先执行数据验证。') => {
  state.file = file || null;
  state.validation = null;
  state.activeJobId = null;
  clearPolling();

  elements.fileInput.value = '';
  elements.fileName.textContent = file ? file.name : '未选择';
  elements.validationState.textContent = file ? '待校验' : '未执行';
  elements.warningList.innerHTML = `<li>${file ? '文件已变更，请重新执行数据验证。' : '尚未执行数据验证。'}</li>`;
  Object.values(elements.summary).forEach((element) => {
    element.textContent = '--';
  });
  renderConstraintGrid();
  resetResult();
  elements.buttons.stop.disabled = true;
  setStatus(file ? 'ready' : 'idle', file ? statusText : '等待上传 Excel');
};

const buildConfig = () => {
  const constraints = {};
  constraintBlueprints.forEach((blueprint) => {
    if (!blueprint.key) {
      return;
    }
    const checkbox = document.getElementById(`constraint-${blueprint.key}`);
    constraints[blueprint.key] = Boolean(checkbox?.checked);
  });

  return {
    constraints,
    termination: {
      timeLimitSeconds: Number.parseInt(elements.terminationTime.value, 10) || 60,
      unimprovedTimeLimitSeconds: elements.terminationUnimproved.value ? Number.parseInt(elements.terminationUnimproved.value, 10) : null,
      stepCountLimit: elements.terminationSteps.value ? Number.parseInt(elements.terminationSteps.value, 10) : null,
      stopOnFeasible: elements.terminationFeasible.checked
    }
  };
};

const buildFormData = (config = null) => {
  const formData = new FormData();
  formData.append('file', state.file);

  if (elements.startTime.value) {
    formData.append('startTime', `${elements.startTime.value}:00`);
  }

  if (config) {
    formData.append('config', JSON.stringify(config));
  }
  return formData;
};

const readJson = async (response) => {
  const data = await response.json();
  if (!response.ok) {
    const detail = Array.isArray(data.errors) ? data.errors.join('；') : data.error;
    const error = new Error(detail || '请求失败');
    error.payload = data;
    throw error;
  }
  return data;
};

const runValidation = async () => {
  ensureFileSelected();
  setStatus('running', '正在校验当前 Excel 数据...');
  elements.validationState.textContent = '校验中';

  const response = await fetch(`${BASE_URL}/analyze-excel`, {
    method: 'POST',
    body: buildFormData()
  });
  const summary = await readJson(response);
  renderValidationSummary(summary);
  setStatus('ready', '数据已校验，可选择约束和终止条件。');
  return summary;
};

const loadValidationSample = async () => {
  setStatus('running', '正在加载内置验证数据...');
  const response = await fetch(`${BASE_URL}/validation-workbook`);
  if (!response.ok) {
    throw new Error('内置验证数据不存在，请先确认 docs/validation-data/validation-workbook.xlsx 已生成。');
  }

  const blob = await response.blob();
  const file = new File(
    [blob],
    'validation-workbook.xlsx',
    { type: blob.type || 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet' }
  );

  applySelectedFile(file, '内置验证数据已加载，正在执行数据验证。');
  await runValidation();
};

const runSyncSolve = async () => {
  ensureFileSelected();
  const config = buildConfig();

  setStatus('running', '同步求解中，等待后端返回最终结果...');
  const response = await fetch(`${BASE_URL}/solve-excel`, {
    method: 'POST',
    body: buildFormData(config)
  });
  const result = await readJson(response);
  renderSolveResult(result, null);
  setStatus(result.isFeasible ? 'done' : 'error', result.isFeasible ? '同步求解完成，已得到可行解。' : '同步求解完成，但结果仍有硬约束冲突。');
};

const clearPolling = () => {
  if (state.pollTimer) {
    clearInterval(state.pollTimer);
    state.pollTimer = null;
  }
};

const pollAsyncStatus = async () => {
  if (!state.activeJobId) {
    return;
  }

  try {
    const response = await fetch(`${BASE_URL}/status/${state.activeJobId}`);
    const statusData = await readJson(response);

    if (statusData.score) {
      elements.resultScore.textContent = statusData.score;
    }

    if (statusData.status === 'NOT_SOLVING') {
      clearPolling();
      elements.buttons.stop.disabled = true;
      if (statusData.result) {
        renderSolveResult(statusData.result, state.activeJobId);
        setStatus(statusData.result.isFeasible ? 'done' : 'error', statusData.result.isFeasible ? '异步求解完成，已得到可行解。' : '异步求解完成，但仍存在硬约束冲突。');
      } else {
        setStatus('ready', '异步任务已结束。');
      }
    }
  } catch (error) {
    clearPolling();
    elements.buttons.stop.disabled = true;
    setStatus('error', error.message || '异步轮询失败。');
  }
};

const runAsyncSolve = async () => {
  ensureFileSelected();
  const config = buildConfig();

  setStatus('running', '异步求解已提交，正在轮询状态...');
  const response = await fetch(`${BASE_URL}/solve-excel-async`, {
    method: 'POST',
    body: buildFormData(config)
  });
  const payload = await readJson(response);

  state.activeJobId = payload.jobId;
  elements.resultJobId.textContent = payload.jobId;
  elements.buttons.stop.disabled = false;

  clearPolling();
  state.pollTimer = window.setInterval(pollAsyncStatus, 1500);
};

const stopAsyncSolve = async () => {
  if (!state.activeJobId) {
    return;
  }
  await fetch(`${BASE_URL}/stop/${state.activeJobId}`, { method: 'DELETE' });
  clearPolling();
  elements.buttons.stop.disabled = true;
  setStatus('ready', `已向任务 ${state.activeJobId} 发送停止信号。`);
};

const clearAll = () => {
  clearPolling();
  state.file = null;
  state.validation = null;
  state.activeJobId = null;

  elements.fileInput.value = '';
  elements.fileName.textContent = '未选择';
  elements.validationState.textContent = '未执行';
  elements.warningList.innerHTML = '<li>尚未执行数据验证。</li>';
  Object.values(elements.summary).forEach((element) => {
    element.textContent = '--';
  });
  renderConstraintGrid();
  resetResult();
  elements.buttons.stop.disabled = true;
  setStatus('idle', '等待上传 Excel');
};

const withGuard = (fn) => async () => {
  try {
    await fn();
  } catch (error) {
    if (error?.payload?.validation) {
      renderValidationSummary(error.payload.validation);
    }
    setStatus('error', error.message || '执行失败。');
  }
};

const bindEvents = () => {
  elements.fileInput.addEventListener('change', (event) => {
    const [file] = event.target.files || [];
    applySelectedFile(file);
  });

  elements.buttons.useValidationSample.addEventListener('click', withGuard(loadValidationSample));
  elements.buttons.validate.addEventListener('click', withGuard(runValidation));
  elements.buttons.sync.addEventListener('click', withGuard(runSyncSolve));
  elements.buttons.async.addEventListener('click', withGuard(runAsyncSolve));
  elements.buttons.stop.addEventListener('click', withGuard(stopAsyncSolve));
  elements.buttons.clear.addEventListener('click', clearAll);
};

const init = () => {
  renderConstraintGrid();
  resetResult();
  bindEvents();
  setStatus('idle', '等待上传 Excel');
};

init();
