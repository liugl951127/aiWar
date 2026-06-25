// OpenClaw Wargame Live Dashboard (V1.3 - WebSocket)
// 通过 ws://...:18081/ws/snapshot 实时接收 snapshot 推送，替代 500ms 轮询。
// 如果 WebSocket 不可用，自动 fallback 到 REST 轮询。

const POLL_MS = 500;
const WS_PORT_OFFSET = 1; // WS 服务在 HTTP 端口 + 1

const canvas = document.getElementById('map');
const ctx = canvas.getContext('2d');
const statusEl = document.getElementById('status');
const tickEl = document.getElementById('tick');
const timeEl = document.getElementById('time');
const blueAliveEl = document.getElementById('blueAlive');
const redAliveEl = document.getElementById('redAlive');
const blueRatioEl = document.getElementById('blueRatio');
const redRatioEl = document.getElementById('redRatio');
const blueThreatsEl = document.getElementById('blueThreats');
const redThreatsEl = document.getElementById('redThreats');
const blueAdvantageEl = document.getElementById('blueAdvantage');
const redAdvantageEl = document.getElementById('redAdvantage');
const blueAdvicesEl = document.getElementById('blueAdvices');
const redAdvicesEl = document.getElementById('redAdvices');
const eventsEl = document.getElementById('events');
const trainEpisodesEl = document.getElementById('trainEpisodes');
const trainAvgRewardEl = document.getElementById('trainAvgReward');
const trainEpsilonEl = document.getElementById('trainEpsilon');
const trainCurveEl = document.getElementById('trainCurve');
const trainEmptyEl = document.getElementById('trainEmpty');
const wsIndicatorEl = document.getElementById('wsIndicator');

let lastState = null;
let mapWidth = 1000, mapHeight = 1000;
let usingWebSocket = false;
let ws = null;

// === WebSocket 客户端 ===

function connectWebSocket() {
  try {
    const proto = location.protocol === 'https:' ? 'wss:' : 'ws:';
    const wsHost = location.hostname || 'localhost';
    const wsPort = (location.port ? parseInt(location.port) + WS_PORT_OFFSET : 18081);
    const url = `${proto}//${wsHost}:${wsPort}/ws/snapshot`;
    ws = new WebSocket(url);

    ws.onopen = () => {
      usingWebSocket = true;
      if (wsIndicatorEl) {
        wsIndicatorEl.textContent = 'WS';
        wsIndicatorEl.className = 'badge badge-ws';
      }
      statusEl.textContent = '⚔️ connected (WebSocket)';
      statusEl.className = 'badge badge-run';
      // Send ping keepalive
      setInterval(() => {
        if (ws && ws.readyState === WebSocket.OPEN) ws.send('ping');
      }, 30000);
    };

    ws.onmessage = (event) => {
      const data = typeof event.data === 'string' ? event.data : '';
      if (data === 'pong') return;
      try {
        const snap = JSON.parse(data);
        if (snap.type === 'snapshot') handleSnapshot(snap);
      } catch (e) {
        console.error('parse error', e);
      }
    };

    ws.onerror = () => {
      usingWebSocket = false;
      if (wsIndicatorEl) {
        wsIndicatorEl.textContent = 'POLL';
        wsIndicatorEl.className = 'badge badge-poll';
      }
      statusEl.textContent = '⚠️ WS error, fallback to polling';
      statusEl.className = 'badge badge-connect';
    };

    ws.onclose = () => {
      usingWebSocket = false;
      if (wsIndicatorEl) {
        wsIndicatorEl.textContent = 'POLL';
        wsIndicatorEl.className = 'badge badge-poll';
      }
      statusEl.textContent = '🔄 WS closed, retrying...';
      statusEl.className = 'badge badge-connect';
      setTimeout(connectWebSocket, 3000);
    };
  } catch (e) {
    usingWebSocket = false;
  }
}

// === REST 轮询 fallback ===

async function pollOnce() {
  try {
    const [snapRes, blueRes, redRes, blueAdvRes, redAdvRes, evRes] = await Promise.all([
      fetch('/api/snapshot').then(r => r.json()),
      fetch('/api/analysis/blue').then(r => r.json()).catch(() => null),
      fetch('/api/analysis/red').then(r => r.json()).catch(() => null),
      fetch('/api/advisory/blue').then(r => r.json()).catch(() => null),
      fetch('/api/advisory/red').then(r => r.json()).catch(() => null),
      fetch('/api/events').then(r => r.json()).catch(() => ({ events: [] })),
      fetch('/api/training').then(r => r.json()).catch(() => ({ episodes: [] }))
    ]);
    render(snapRes, blueRes, redRes, blueAdvRes, redAdvRes, evRes);
    renderTraining(snapRes, evRes, null);
  } catch (e) {
    statusEl.textContent = 'disconnected';
    statusEl.className = 'badge badge-stop';
  }
}

// === 快照处理 ===

function handleSnapshot(snap) {
  // 来自 WebSocket 的紧凑快照
  if (snap.state === null) return;

  // WebSocket 格式转换到统一格式
  const unified = {
    tick: snap.tick,
    timeSeconds: snap.t / 1000,
    running: snap.running,
    winner: snap.winner,
    mapWidth: snap.w,
    mapHeight: snap.h,
    blueAlive: snap.ba,
    redAlive: snap.ra,
    units: (snap.units || []).map(u => ({
      id: u.id,
      team: u.t === 'B' ? 'BLUE' : u.t === 'R' ? 'RED' : 'NEUTRAL',
      type: u.k,
      x: u.x,
      y: u.y,
      hp: u.hp,
      maxHp: u.mh,
      status: u.s === 'I' ? 'IDLE' : u.s === 'M' ? 'MOVING' : u.s === 'E' ? 'ENGAGING'
                : u.s === 'R' ? 'RETREATING' : u.s === 'D' ? 'DESTROYED' : 'IDLE',
      alive: u.a === 1,
      buffCount: u.bf || 0
    })),
    _advBlue: snap.adv && snap.adv.b,
    _advRed: snap.adv && snap.adv.r
  };
  drawMap(unified);

  tickEl.textContent = unified.tick;
  timeEl.textContent = unified.timeSeconds.toFixed(1);
  mapWidth = unified.mapWidth;
  mapHeight = unified.mapHeight;
  blueAliveEl.textContent = unified.blueAlive;
  redAliveEl.textContent = unified.redAlive;

  // Advantage bar
  if (unified._advBlue) {
    renderAdvantageBar(blueAdvantageEl, unified._advBlue);
    // 在 WS 模式下我们没拿到 advices 全字段；保持上次（轮询模式下完整渲染）
  }
  if (unified._advRed) {
    renderAdvantageBar(redAdvantageEl, unified._advRed);
  }

  // Status
  if (unified.winner) {
    statusEl.textContent = `🏆 ${unified.winner} WINS @ tick ${unified.tick}`;
    statusEl.className = 'badge badge-stop';
  } else if (unified.running) {
    statusEl.textContent = '⚔️ IN PROGRESS';
    statusEl.className = 'badge badge-run';
  }

  lastState = unified;
}

// === 渲染 ===

function render(snap, blueAnalysis, redAnalysis, blueAdvisory, redAdvisory, events) {
  if (snap && snap.state !== null) {
    handleSnapshot({
      tick: snap.tick,
      t: (snap.timeSeconds || 0) * 1000,
      running: snap.running,
      winner: snap.winner,
      w: snap.mapWidth || 1000,
      h: snap.mapHeight || 1000,
      ba: snap.blueAlive,
      ra: snap.redAlive,
      units: (snap.units || []).map(u => ({
        id: u.id,
        team: u.team,
        type: u.type,
        x: u.x,
        y: u.y,
        hp: u.hp,
        mh: u.maxHp,
        s: u.status === 'IDLE' ? 'I' : u.status === 'MOVING' ? 'M' : u.status === 'ENGAGING' ? 'E'
            : u.status === 'RETREATING' ? 'R' : u.status === 'DESTROYED' ? 'D' : 'I',
        a: u.alive ? 1 : 0,
        bf: (u.buffs || []).length
      })),
      adv: { b: blueAdvisory && blueAdvisory.advantage, r: redAdvisory && redAdvisory.advantage }
    });
  }

  if (blueAnalysis && !blueAnalysis.error) {
    blueRatioEl.textContent = isFinite(blueAnalysis.firepowerRatio) ?
      blueAnalysis.firepowerRatio.toFixed(2) : '∞';
    blueThreatsEl.innerHTML = (blueAnalysis.topThreats || []).map(t =>
      `<li><span>${t.type} · ${t.target.substring(0,8)}</span><b>${t.score.toFixed(2)} / ${Math.round(t.distance)}m</b></li>`
    ).join('');
  }
  if (redAnalysis && !redAnalysis.error) {
    redRatioEl.textContent = isFinite(redAnalysis.firepowerRatio) ?
      redAnalysis.firepowerRatio.toFixed(2) : '∞';
    redThreatsEl.innerHTML = (redAnalysis.topThreats || []).map(t =>
      `<li><span>${t.type} · ${t.target.substring(0,8)}</span><b>${t.score.toFixed(2)} / ${Math.round(t.distance)}m</b></li>`
    ).join('');
  }

  if (blueAdvisory && !blueAdvisory.error) {
    renderAdvantageBar(blueAdvantageEl, blueAdvisory.advantage);
    renderAdvices(blueAdvicesEl, blueAdvisory.advices);
  }
  if (redAdvisory && !redAdvisory.error) {
    renderAdvantageBar(redAdvantageEl, redAdvisory.advantage);
    renderAdvices(redAdvicesEl, redAdvisory.advices);
  }

  eventsEl.innerHTML = (events.events || []).slice().reverse().slice(0, 12).map(e =>
    `<li><span class="kind ${e.kind}">${e.kind}</span><span class="tick">t=${e.tick}</span></li>`
  ).join('');
}

function renderAdvantageBar(el, adv) {
  if (!adv) return;
  const fields = [
    { name: 'firepower', val: adv.firepower !== undefined ? adv.firepower : adv.f },
    { name: 'manpower', val: adv.manpower !== undefined ? adv.manpower : adv.m },
    { name: 'detection', val: adv.detection !== undefined ? adv.detection : adv.d },
    { name: 'mobility', val: adv.mobility !== undefined ? adv.mobility : adv.s },
    { name: 'cohesion', val: adv.cohesion !== undefined ? adv.cohesion : adv.c }
  ];
  let html = '';
  for (const f of fields) {
    const pct = Math.round((f.val || 0) * 100);
    const color = f.val > 0.6 ? '#4caf50' : f.val > 0.4 ? '#ff9800' : '#f44336';
    html += `<div class="row">
      <span class="label">${f.name}</span>
      <div class="bar"><div style="width:${pct}%; background:${color};"></div></div>
      <span class="val">${pct}%</span>
    </div>`;
  }
  const opct = Math.round((adv.overall !== undefined ? adv.overall : adv.o) * 100);
  const ocolor = (adv.overall || adv.o) > 0.6 ? '#4caf50' : (adv.overall || adv.o) > 0.4 ? '#ff9800' : '#f44336';
  html += `<div class="row overall">
    <span class="label">OVERALL</span>
    <div class="bar"><div style="width:${opct}%; background:${ocolor};"></div></div>
    <span class="val">${opct}%</span>
  </div>`;
  el.innerHTML = html;
}

function renderAdvices(el, advices) {
  if (!advices || advices.length === 0) {
    el.innerHTML = '<li style="color:#9fb1d6;">no advice</li>';
    return;
  }
  el.innerHTML = advices.map(a =>
    `<li>
      <span class="advice-kind ${a.kind}">${a.kind}</span>
      <span class="priority">pri=${a.priority}</span>
      <span class="reason">${a.reason}</span>
    </li>`
  ).join('');
}

function drawMap(snap) {
  if (!snap || !snap.units) return;
  const w = canvas.width, h = canvas.height;
  ctx.fillStyle = '#0a1426';
  ctx.fillRect(0, 0, w, h);
  ctx.strokeStyle = '#14213a';
  ctx.lineWidth = 1;
  const sx = w / mapWidth, sy = h / mapHeight;
  for (let x = 0; x <= mapWidth; x += 500) {
    ctx.beginPath();
    ctx.moveTo(x * sx, 0);
    ctx.lineTo(x * sx, h);
    ctx.stroke();
  }
  for (let y = 0; y <= mapHeight; y += 500) {
    ctx.beginPath();
    ctx.moveTo(0, y * sy);
    ctx.lineTo(w, y * sy);
    ctx.stroke();
  }
  for (const u of snap.units) {
    const cx = u.x * sx;
    const cy = h - u.y * sy;
    let color;
    if (!u.alive) color = '#555';
    else if (u.team === 'BLUE') color = '#2196f3';
    else if (u.team === 'RED') color = '#f44336';
    else color = '#888';
    const ratio = u.alive ? Math.max(0.2, u.hp / u.maxHp) : 0;
    const r = 6 + ratio * 6;
    if (u.status === 'ENGAGING') {
      ctx.beginPath();
      ctx.arc(cx, cy, r + 4, 0, 2 * Math.PI);
      ctx.fillStyle = color + '33';
      ctx.fill();
    }
    ctx.beginPath();
    ctx.arc(cx, cy, r, 0, 2 * Math.PI);
    ctx.fillStyle = color;
    ctx.fill();
    ctx.strokeStyle = '#fff';
    ctx.lineWidth = 1;
    ctx.stroke();
    ctx.fillStyle = '#fff';
    ctx.font = '10px monospace';
    ctx.textAlign = 'center';
    ctx.fillText((u.type || '').substring(0, 4), cx, cy - r - 4);
    if (u.buffCount && u.buffCount > 0) {
      ctx.fillStyle = '#ffeb3b';
      ctx.font = 'bold 10px sans-serif';
      ctx.fillText('✨', cx + r, cy - r);
    }
    const hpw = Math.max(8, r * 2);
    const hpx = cx - hpw / 2;
    const hpy = cy + r + 4;
    ctx.fillStyle = '#000';
    ctx.fillRect(hpx, hpy, hpw, 3);
    ctx.fillStyle = u.alive ? '#4caf50' : '#555';
    ctx.fillRect(hpx, hpy, hpw * (u.alive ? u.hp / u.maxHp : 0), 3);
  }
}

// === 启动 ===

// === 训练曲线渲染 ===
function renderTraining(snap, events, training) {
  // 训练数据可通过 /api/training 取得
  if (!training || !training.episodes || training.episodes.length === 0) {
    trainEmptyEl.classList.remove('hidden');
    trainCurveEl.classList.add('hidden');
    return;
  }
  trainEmptyEl.classList.add('hidden');
  trainCurveEl.classList.remove('hidden');

  const eps = training.episodes;
  const last = eps[eps.length - 1];
  // 统计
  trainEpisodesEl.textContent = eps.length;
  const totalReward = eps.reduce((a, e) => a + (e.r || 0), 0);
  trainAvgRewardEl.textContent = (totalReward / eps.length).toFixed(1);
  trainEpsilonEl.textContent = last.epsilon != null ? last.epsilon.toFixed(3) : '-';

  // 画 SVG 线图
  const W = 300, H = 100, padX = 8, padY = 8;
  const xs = eps.map((e, i) => i);
  const ys = eps.map(e => e.r || 0);
  const xMin = 0, xMax = Math.max(1, eps.length - 1);
  const yMin = Math.min(0, ...ys), yMax = Math.max(0, ...ys);
  const yRange = Math.max(1, yMax - yMin);
  const xScale = i => padX + (i - xMin) / (xMax - xMin) * (W - 2 * padX);
  const yScale = y => H - padY - (y - yMin) / yRange * (H - 2 * padY);

  // line path
  let path = '';
  let fill = `M ${padX} ${H - padY} L `;
  eps.forEach((e, i) => {
    const x = xScale(i);
    const y = yScale(e.r || 0);
    if (i === 0) path += `M ${x} ${y}`;
    else path += ` L ${x} ${y}`;
    fill += `${x} ${y} L `;
  });
  fill += `${xScale(eps.length - 1)} ${H - padY} Z`;

  // zero line
  let zeroY = null;
  if (yMin < 0 && yMax > 0) {
    zeroY = yScale(0);
  }

  trainCurveEl.innerHTML = `
    <line class="axis" x1="${padX}" y1="${H - padY}" x2="${W - padX}" y2="${H - padY}" />
    <line class="axis" x1="${padX}" y1="${padY}" x2="${padX}" y2="${H - padY}" />
    ${zeroY != null ? `<line class="axis" stroke-dasharray="2,2" x1="${padX}" y1="${zeroY}" x2="${W - padX}" y2="${zeroY}" />` : ''}
    <path class="reward-fill" d="${fill}" />
    <path class="reward-line" d="${path}" />
    ${eps.map((e, i) => `<circle class="dot" cx="${xScale(i)}" cy="${yScale(e.r || 0)}" r="1.5" />`).join('')}
  `;
}

let _lastTraining = { episodes: [] };
async function fetchTrainingAndRender() {
  try {
    const t = await fetch('/api/training').then(r => r.json());
    _lastTraining = t;
    renderTraining(null, null, t);
  } catch (e) { /* ignore */ }
}
setInterval(fetchTrainingAndRender, 2000);
fetchTrainingAndRender();

connectWebSocket();
// 启动 polling 作为 fallback（即使 WS 工作也保留，让 advices/events 完整刷新）
setInterval(pollOnce, POLL_MS);
pollOnce();
