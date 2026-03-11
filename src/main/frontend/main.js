const BASE_URL = '/api/v1/scheduling';

const btnSync = document.getElementById('btn-sync');
const btnAsync = document.getElementById('btn-async');
const statusDot = document.querySelector('.dot');
const statusText = document.getElementById('system-status');
const valScore = document.getElementById('val-score');
const valJobId = document.getElementById('val-jobid');
const lineContainer = document.getElementById('line-container');

let activeJobId = null;
let pollInterval = null;

// Mock problem data for DEMO API call
const getMockProblem = async () => {
  const res = await fetch(`${BASE_URL}/demo`);
  if (!res.ok) throw new Error("未能获取演示数据");
  return await res.json();
};

const setStatus = (status, jobId = 'N/A') => {
  valJobId.innerText = jobId;
  if (status === 'SOLVING') {
    statusDot.classList.add('processing');
    statusText.innerText = '引流计算中 / COMPUTING...';
    btnSync.disabled = true;
    btnAsync.disabled = true;
  } else {
    statusDot.classList.remove('processing');
    statusText.innerText = '系统就绪 / SYSTEM READY';
    btnSync.disabled = false;
    btnAsync.disabled = false;
  }
};

const formatTime = (isoString) => {
  if (!isoString) return '??:??';
  const d = new Date(isoString);
  const MM = String(d.getMonth()+1).padStart(2,'0');
  const DD = String(d.getDate()).padStart(2,'0');
  const HH = String(d.getHours()).padStart(2,'0');
  const mm = String(d.getMinutes()).padStart(2,'0');
  return `${MM}/${DD} ${HH}:${mm}`;
};

const renderTasks = (lineTasks) => {
  lineContainer.innerHTML = '';
  
  if (!lineTasks || Object.keys(lineTasks).length === 0) {
    lineContainer.innerHTML = '<div class="empty-state">尚未接收排程结果。 / NO DATA</div>';
    return;
  }

  let delay = 0;
  // Sort lines so they render consistently
  const sortedLines = Object.entries(lineTasks).sort((a,b) => a[0].localeCompare(b[0]));

  for (const [lineId, tasks] of sortedLines) {
    const lineEl = document.createElement('div');
    lineEl.className = 'production-line';
    
    // Header
    const header = document.createElement('div');
    header.className = 'line-header';
    header.innerHTML = `<span class="line-name">${lineId} 拓扑</span><span class="task-id">包含 ${tasks.length} 组任务块</span>`;
    lineEl.appendChild(header);
    
    // Tracks
    const track = document.createElement('div');
    track.className = 'task-track';
    
    if(tasks.length === 0) {
      track.innerHTML = '<div class="empty-state" style="padding:1rem;">路线空闲</div>';
    } else {
      tasks.forEach((t) => {
        const block = document.createElement('div');
        block.className = 'task-block';
        block.style.animationDelay = `${delay}s`;
        delay += 0.05;
        
        const sTime = formatTime(t.plannedStart);
        const eTime = formatTime(t.plannedEnd);
        
        block.innerHTML = `
          <div class="task-id">${t.originalTaskId}</div>
          <div class="task-time">${sTime} — ${eTime}</div>
          <div class="task-days">占用时长: ${t.daysCovered}D / ${t.totalDays}D</div>
        `;
        track.appendChild(block);
      });
    }
    
    lineEl.appendChild(track);
    lineContainer.appendChild(lineEl);
  }
};

const pollStatus = async () => {
  if (!activeJobId) return;
  try {
    const res = await fetch(`${BASE_URL}/status/${activeJobId}`);
    const data = await res.json();
    
    if (data.score) {
      valScore.innerText = data.score;
    }
    
    if (data.status === 'NOT_SOLVING') {
      clearInterval(pollInterval);
      setStatus('DONE', activeJobId);
      if (data.result && data.result.lineTasks) {
        renderTasks(data.result.lineTasks);
      }
    }
  } catch (err) {
    console.error('Polling error:', err);
    clearInterval(pollInterval);
    setStatus('ERROR', activeJobId);
  }
};

btnAsync.addEventListener('click', async () => {
  try {
    const problem = await getMockProblem();
    const res = await fetch(`${BASE_URL}/solve-async`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(problem)
    });
    const data = await res.json();
    activeJobId = data.jobId;
    setStatus('SOLVING', activeJobId);
    valScore.innerText = '计算中...';
    lineContainer.innerHTML = '<div class="empty-state">正在深度演化... / EVOLVING...</div>';
    
    pollInterval = setInterval(pollStatus, 1000);
  } catch (err) {
    console.error('Async trigger error:', err);
    alert('无法连接至排程引擎后端');
  }
});

btnSync.addEventListener('click', async () => {
  setStatus('SOLVING');
  valScore.innerText = '同步阻塞中...';
  lineContainer.innerHTML = '<div class="empty-state">等待后端同步响应... / AWAITING...</div>';
  try {
    const problem = await getMockProblem();
    const res = await fetch(`${BASE_URL}/solve`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(problem)
    });
    const data = await res.json();
    
    setStatus('DONE');
    if (data.score) valScore.innerText = data.score;
    if (data.lineTasks) renderTasks(data.lineTasks);
  } catch (err) {
    console.error('Sync trigger error:', err);
    setStatus('ERROR');
    alert('同步排程请求失败');
  }
});

console.log("Solartron APS UI Initialized.");
