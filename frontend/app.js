/* ── app.js — Fleet Monitor Dashboard ──────────────────────────────────────
   Conecta el frontend con la API REST de Spring Boot (localhost:8080)
   y actualiza el dashboard cada 5 segundos automáticamente.

   Endpoints consumidos:
     GET /api/fleet/status              → KPIs + última posición
     GET /api/fleet/alertas             → alertas activas (temp + comb)
     GET /api/fleet/vehicle/{id}/telemetry → tarjeta de vehículo
     GET /api/fleet/health              → estado del sistema
*/

const API = 'http://localhost:8082';  // Backend separado en puerto 8082
const VEHICULOS = ['VH-001', 'VH-002', 'VH-003'];
const COLORS = { 'VH-001': '#388bfd', 'VH-002': '#3fb950', 'VH-003': '#bc8cff' };

// ── Navegación ───────────────────────────────────────────────────────────────
function showView(name) {
  document.querySelectorAll('.view').forEach(v => v.classList.remove('active'));
  document.querySelectorAll('.nav-item').forEach(n => n.classList.remove('active'));
  document.getElementById('view-' + name).classList.add('active');
  document.querySelector(`[data-view="${name}"]`).classList.add('active');
  document.getElementById('view-title').textContent =
    { dashboard: 'Dashboard', vehiculos: 'Vehículos', alertas: 'Alertas', mapa: 'Mapa GPS' }[name];
}

// ── Fetch helpers ────────────────────────────────────────────────────────────
async function get(path) {
  const res = await fetch(API + path);
  if (!res.ok) throw new Error(res.status);
  return res.json();
}

function fmtTime(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleTimeString('es-EC', { hour: '2-digit', minute: '2-digit', second: '2-digit' });
}

function fmtDate(iso) {
  if (!iso) return '—';
  const d = new Date(iso);
  return d.toLocaleString('es-EC', { dateStyle: 'short', timeStyle: 'medium' });
}

// ── Log en vivo ──────────────────────────────────────────────────────────────
const logBox = document.getElementById('live-log');
function addLog(msg, cls = 'log-info') {
  const el = document.createElement('p');
  el.className = `log-entry ${cls}`;
  el.textContent = `[${new Date().toLocaleTimeString('es-EC')}] ${msg}`;
  logBox.appendChild(el);
  if (logBox.children.length > 120) logBox.removeChild(logBox.firstChild);
  logBox.scrollTop = logBox.scrollHeight;
}
function clearLog() { logBox.innerHTML = ''; addLog('Log limpiado.'); }

// ── Estado del sistema ───────────────────────────────────────────────────────
const dot = document.getElementById('system-dot');
const sysSpan = document.getElementById('system-status');
function setStatus(ok) {
  dot.className = 'status-dot ' + (ok ? 'ok' : 'err');
  sysSpan.textContent = ok ? 'Sistema activo' : 'Sin conexión';
}

// ── Fetch: status general ────────────────────────────────────────────────────
async function fetchStatus() {
  try {
    const d = await get('/api/fleet/status');
    document.getElementById('kpi-vehiculos').textContent = d.vehiculos_activos ?? '—';
    document.getElementById('kpi-gps').textContent = d.total_gps ?? '—';
    document.getElementById('kpi-temp-alert').textContent = d.alertas_temp_activas ?? '—';
    document.getElementById('kpi-fuel-alert').textContent = d.alertas_fuel_activas ?? '—';

    // Tabla última posición
    const tbody = document.getElementById('tbody-posicion');
    const pos = d.ultima_posicion ?? [];
    if (pos.length === 0) {
      tbody.innerHTML = '<tr><td colspan="5" class="empty">Sin datos aún — el simulador está publicando...</td></tr>';
    } else {
      tbody.innerHTML = pos.map(g => `
        <tr>
          <td><b style="color:${COLORS[g.vehiculoId] || '#888'}">${g.vehiculoId}</b></td>
          <td>${(g.latitud || 0).toFixed(4)}</td>
          <td>${(g.longitud || 0).toFixed(4)}</td>
          <td>${(g.velocidadKmh || 0).toFixed(1)}</td>
          <td>${fmtTime(g.receivedAt)}</td>
        </tr>`).join('');
      addLog(`Status: ${d.vehiculos_activos} vehículo(s), ${d.total_gps} GPS, ${d.alertas_temp_activas} alertas temp.`, 'log-gps');
    }

    document.getElementById('last-update').textContent = 'Actualizado: ' + new Date().toLocaleTimeString('es-EC');
    setStatus(true);

    // Dibujar mapa con posiciones
    dibujarMapa(pos);
  } catch (e) {
    addLog('Error conectando con la API: ' + e.message, 'log-alert');
    setStatus(false);
  }
}

// ── Fetch: alertas ───────────────────────────────────────────────────────────
async function fetchAlertas() {
  try {
    const d = await get('/api/fleet/alertas');
    const totalAlertas = (d.total_alertas_temp || 0) + (d.total_alertas_fuel || 0);

    // Badge sidebar
    const badge = document.getElementById('badge-alertas');
    badge.textContent = totalAlertas;
    badge.className = 'badge' + (totalAlertas === 0 ? ' hidden' : '');

    // Tabla temperatura
    const tbodyT = document.getElementById('tbody-temp-alertas');
    const temps = d.alertas_temperatura ?? [];
    tbodyT.innerHTML = temps.length === 0
      ? '<tr><td colspan="4" class="empty">Sin alertas de temperatura activas ✅</td></tr>'
      : temps.slice(0, 20).map(t => `
          <tr>
            <td><b style="color:${COLORS[t.vehiculoId] || '#888'}">${t.vehiculoId}</b></td>
            <td style="color:#f85149"><b>${(t.temperaturaC || 0).toFixed(1)} °C</b></td>
            <td><span class="tag tag-alert">⚠️ ALERTA</span></td>
            <td>${fmtDate(t.receivedAt)}</td>
          </tr>`).join('');

    if (temps.length > 0)
      addLog(`⚠️ ${temps.length} alertas temperatura activas`, 'log-alert');

    // Tabla combustible
    const tbodyF = document.getElementById('tbody-fuel-alertas');
    const fuels = d.alertas_combustible ?? [];
    tbodyF.innerHTML = fuels.length === 0
      ? '<tr><td colspan="4" class="empty">Sin alertas de combustible activas ✅</td></tr>'
      : fuels.slice(0, 20).map(f => `
          <tr>
            <td><b style="color:${COLORS[f.vehiculoId] || '#888'}">${f.vehiculoId}</b></td>
            <td style="color:#d29922"><b>${(f.nivelPct || 0).toFixed(1)} %</b></td>
            <td><span class="tag tag-alert">⚠️ BAJO</span></td>
            <td>${fmtDate(f.receivedAt)}</td>
          </tr>`).join('');

    if (fuels.length > 0)
      addLog(`⚠️ ${fuels.length} alertas combustible activas`, 'log-temp');

  } catch (e) { /* silencioso */ }
}

// ── Fetch: vehículos individuales ────────────────────────────────────────────
async function fetchVehiculos() {
  const grid = document.getElementById('vehiculos-grid');
  grid.innerHTML = '';

  for (const id of VEHICULOS) {
    try {
      const d = await get(`/api/fleet/vehicle/${id}/telemetry`);
      const gps = d.ultimo_gps;
      const temp = d.ultima_temperatura;
      const fuel = d.ultimo_combustible;

      const nivelPct = fuel ? (fuel.nivelPct || 0) : 0;
      const tempC = temp ? (temp.temperaturaC || 0) : 0;
      const fuelClass = nivelPct < 20 ? 'gauge-red' : nivelPct < 40 ? 'gauge-orange' : 'gauge-green';
      const tempClass = tempC > 4 ? 'gauge-red' : tempC > 2 ? 'gauge-orange' : 'gauge-green';

      const card = document.createElement('div');
      card.className = 'vh-card';
      card.innerHTML = `
        <div class="vh-header">
          <span class="vh-id">${id}</span>
          ${temp && temp.alerta ? '<span class="tag tag-alert">⚠️ ALERTA</span>' : '<span class="tag tag-ok">✅ OK</span>'}
        </div>
        <div class="vh-row"><span class="vh-label">📡 GPS registros</span>
          <span class="vh-val">${d.historial_gps_count}</span></div>
        <div class="vh-row"><span class="vh-label">📍 Latitud</span>
          <span class="vh-val">${gps ? gps.latitud.toFixed(5) : '—'}</span></div>
        <div class="vh-row"><span class="vh-label">📍 Longitud</span>
          <span class="vh-val">${gps ? gps.longitud.toFixed(5) : '—'}</span></div>
        <div class="vh-row"><span class="vh-label">🏎️ Velocidad</span>
          <span class="vh-val">${gps ? gps.velocidadKmh.toFixed(1) + ' km/h' : '—'}</span></div>
        <div class="vh-row"><span class="vh-label">🌡️ Temperatura</span>
          <span class="vh-val" style="color:${tempC > 4 ? '#f85149' : '#3fb950'}">${temp ? tempC.toFixed(1) + ' °C' : '—'}</span></div>
        <div class="vh-row"><span class="vh-label">⛽ Combustible</span>
          <span class="vh-val" style="color:${nivelPct < 20 ? '#f85149' : '#3fb950'}">${fuel ? nivelPct.toFixed(1) + '%' : '—'}</span></div>

        <div class="gauge-wrap">
          <div class="gauge-label">⛽ Nivel combustible</div>
          <div class="gauge-bar"><div class="gauge-fill ${fuelClass}" style="width:${nivelPct}%"></div></div>
        </div>
        <div class="gauge-wrap" style="margin-top:8px">
          <div class="gauge-label">🌡️ Temperatura (escala -5..8°C → 0..100%)</div>
          <div class="gauge-bar"><div class="gauge-fill ${tempClass}" style="width:${Math.min(100, Math.max(0, ((tempC + 5) / 13) * 100))}%"></div></div>
        </div>`;
      grid.appendChild(card);

      addLog(`Telemetría ${id}: temp=${tempC.toFixed(1)}°C, fuel=${nivelPct.toFixed(1)}%`, 'log-fuel');
    } catch (e) {
      const card = document.createElement('div');
      card.className = 'vh-card';
      card.innerHTML = `<div class="vh-header"><span class="vh-id">${id}</span></div>
        <p class="empty">Sin datos aún</p>`;
      grid.appendChild(card);
    }
  }
}

// ── Mapa GPS (Canvas) ─────────────────────────────────────────────────────────
function dibujarMapa(posiciones) {
  const canvas = document.getElementById('mapa-canvas');
  if (!canvas) return;
  const ctx = canvas.getContext('2d');
  const W = canvas.width, H = canvas.height;

  // Límites: área Guayaquil ±0.5°
  const LAT_MIN = -2.67, LAT_MAX = -1.67;
  const LNG_MIN = -80.42, LNG_MAX = -79.42;

  ctx.clearRect(0, 0, W, H);

  // Fondo (matching light theme)
  ctx.fillStyle = '#f1f5f9';
  ctx.fillRect(0, 0, W, H);

  // Grid sutil
  ctx.strokeStyle = 'rgba(148, 163, 184, 0.15)';
  ctx.lineWidth = 1;
  for (let i = 0; i <= 20; i++) {
    const x = (W / 20) * i;
    const y = (H / 12) * i;
    ctx.beginPath(); ctx.moveTo(x, 0); ctx.lineTo(x, H); ctx.stroke();
    if (i <= 12) { ctx.beginPath(); ctx.moveTo(0, y); ctx.lineTo(W, y); ctx.stroke(); }
  }

  // Título del mapa
  ctx.fillStyle = '#64748b';
  ctx.font = 'bold 14px "Plus Jakarta Sans"';
  ctx.fillText('Navegación en Tiempo Real — Guayaquil', 24, 30);

  // Marca base Guayaquil
  const baseX = latLngToX(-2.1709, -79.9224, W, LAT_MIN, LAT_MAX, LNG_MIN, LNG_MAX);
  const baseY = latLngToY(-2.1709, -79.9224, H, LAT_MIN, LAT_MAX);

  ctx.beginPath();
  ctx.arc(baseX, baseY, 25, 0, Math.PI * 2);
  ctx.fillStyle = 'rgba(79, 70, 229, 0.05)';
  ctx.fill();
  ctx.strokeStyle = 'rgba(79, 70, 229, 0.2)';
  ctx.setLineDash([5, 5]);
  ctx.stroke();
  ctx.setLineDash([]);

  ctx.fillStyle = '#4f46e5';
  ctx.font = '600 11px "Plus Jakarta Sans"';
  ctx.fillText('📍 Base Operativa', baseX + 15, baseY + 5);

  // Leyenda
  const leyenda = document.getElementById('mapa-leyenda');
  leyenda.innerHTML = '';

  // Dibujar vehículos
  if (!posiciones || posiciones.length === 0) {
    ctx.fillStyle = '#94a3b8';
    ctx.font = '500 18px "Plus Jakarta Sans"';
    ctx.textAlign = 'center';
    ctx.fillText('⌛ Esperando señales GPS...', W / 2, H / 2);
    ctx.textAlign = 'left';
    return;
  }

  posiciones.forEach(p => {
    const color = COLORS[p.vehiculoId] || '#4f46e5';
    const x = latLngToX(p.latitud, p.longitud, W, LAT_MIN, LAT_MAX, LNG_MIN, LNG_MAX);
    const y = latLngToY(p.latitud, p.longitud, H, LAT_MIN, LAT_MAX);

    // Halo animado (estático en canvas pero con gradiente)
    const grad = ctx.createRadialGradient(x, y, 0, x, y, 25);
    grad.addColorStop(0, color + '44');
    grad.addColorStop(1, 'transparent');
    ctx.fillStyle = grad;
    ctx.beginPath(); ctx.arc(x, y, 25, 0, Math.PI * 2); ctx.fill();

    // Punto principal
    ctx.beginPath(); ctx.arc(x, y, 9, 0, Math.PI * 2);
    ctx.fillStyle = color; ctx.fill();
    ctx.strokeStyle = '#ffffff'; ctx.lineWidth = 3; ctx.stroke();

    // Sombra del punto
    ctx.shadowColor = 'rgba(0,0,0,0.1)';
    ctx.shadowBlur = 10;
    ctx.stroke();
    ctx.shadowBlur = 0;

    // Etiqueta flotante
    ctx.fillStyle = '#1e293b';
    ctx.font = '800 12px "Plus Jakarta Sans"';
    ctx.fillText(p.vehiculoId, x + 15, y - 5);

    ctx.fillStyle = color;
    ctx.font = '600 10px "JetBrains Mono"';
    ctx.fillText(`${(p.velocidadKmh || 0).toFixed(1)} km/h`, x + 15, y + 8);

    // Leyenda interactiva
    const li = document.createElement('div');
    li.className = 'leyenda-item';
    li.innerHTML = `<div class="leyenda-dot" style="background:${color}"></div>
      <span>${p.vehiculoId} <small style="color:#64748b">(${p.latitud.toFixed(3)}, ${p.longitud.toFixed(3)})</small></span>`;
    leyenda.appendChild(li);
  });
}

function latLngToX(lat, lng, W, latMin, latMax, lngMin, lngMax) {
  return ((lng - lngMin) / (lngMax - lngMin)) * W;
}
function latLngToY(lat, lng, H, latMin, latMax) {
  return ((latMax - lat) / (latMax - latMin)) * H;
}

// ── Ciclo principal de actualización ────────────────────────────────────────
async function fetchAll() {
  const btn = document.querySelector('.btn-refresh');
  if (btn) btn.style.opacity = '0.5';

  await fetchStatus();
  await fetchAlertas();

  if (document.getElementById('view-vehiculos').classList.contains('active')) {
    await fetchVehiculos();
  }

  if (btn) btn.style.opacity = '1';
}

// ── Health-check inicial ──────────────────────────────────────────────────────
async function healthCheck() {
  try {
    const d = await get('/api/fleet/health');
    if (d.status === 'UP') {
      addLog('🚀 Sistema de Monitoreo inicializado', 'log-gps');
      addLog('📡 Pipeline MQTT → RabbitMQ → FastAPI activo', 'log-fuel');
      setStatus(true);
    }
  } catch (e) {
    addLog('❌ Error de conexión: Servidor no responde', 'log-alert');
    setStatus(false);
  }
}

// ── Inicializar ──────────────────────────────────────────────────────────────
window.addEventListener('DOMContentLoaded', async () => {
  await healthCheck();
  await fetchAll();

  // Auto-refresh inteligente
  setInterval(async () => {
    await fetchAll();
    // Si vehiculos view activa, refrescar también
    if (document.getElementById('view-vehiculos').classList.contains('active')) {
      await fetchVehiculos();
    }
  }, 5000);
});
