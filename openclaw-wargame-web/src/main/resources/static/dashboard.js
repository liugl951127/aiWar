// OpenClaw Wargame Live Dashboard
const POLL_MS = 500;

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
const eventsEl = document.getElementById('events');

let lastState = null;
let mapWidth = 1000, mapHeight = 1000;

async function poll() {
  try {
    const [snapRes, blueRes, redRes, evRes] = await Promise.all([
      fetch('/api/snapshot').then(r => r.json()),
      fetch('/api/analysis/blue').then(r => r.json()).catch(() => null),
      fetch('/api/analysis/red').then(r => r.json()).catch(() => null),
      fetch('/api/events').then(r => r.json()).catch(() => ({ events: [] }))
    ]);
    render(snapRes, blueRes, redRes, evRes);
  } catch (e) {
    statusEl.textContent = 'disconnected';
    statusEl.className = 'badge badge-stop';
  }
}

function render(snap, blueAnalysis, redAnalysis, events) {
  // Status
  if (snap.winner) {
    statusEl.textContent = `🏆 ${snap.winner} WINS @ tick ${snap.winnerTick}`;
    statusEl.className = 'badge badge-stop';
  } else if (snap.running) {
    statusEl.textContent = '⚔️ IN PROGRESS';
    statusEl.className = 'badge badge-run';
  } else {
    statusEl.textContent = '⏸️ IDLE';
    statusEl.className = 'badge badge-connect';
  }

  if (snap.tick !== undefined) {
    tickEl.textContent = snap.tick;
    timeEl.textContent = snap.timeSeconds.toFixed(1);
    mapWidth = snap.mapWidth || 1000;
    mapHeight = snap.mapHeight || 1000;
  }
  blueAliveEl.textContent = snap.blueAlive ?? '-';
  redAliveEl.textContent = snap.redAlive ?? '-';

  // Render map
  drawMap(snap);

  // Analysis
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

  // Events
  eventsEl.innerHTML = (events.events || []).slice().reverse().slice(0, 12).map(e =>
    `<li><span class="kind ${e.kind}">${e.kind}</span><span class="tick">t=${e.tick}</span></li>`
  ).join('');
}

function drawMap(snap) {
  if (!snap.units) return;
  const w = canvas.width, h = canvas.height;
  ctx.fillStyle = '#0a1426';
  ctx.fillRect(0, 0, w, h);

  // Draw grid
  ctx.strokeStyle = '#14213a';
  ctx.lineWidth = 1;
  const gridStep = 50; // 50m
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

  // Draw units
  for (const u of snap.units) {
    const cx = u.x * sx;
    const cy = h - u.y * sy; // flip y
    let color;
    if (!u.alive) color = '#555';
    else if (u.team === 'BLUE') color = '#2196f3';
    else if (u.team === 'RED') color = '#f44336';
    else color = '#888';

    // hp ratio affects radius
    const ratio = u.alive ? Math.max(0.2, u.hp / u.maxHp) : 0;
    const r = 6 + ratio * 6;

    // Glow for ENGAGING
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

    // Type label
    ctx.fillStyle = '#fff';
    ctx.font = '10px monospace';
    ctx.textAlign = 'center';
    ctx.fillText(u.type.substring(0, 4), cx, cy - r - 4);
  }
}

setInterval(poll, POLL_MS);
poll();
