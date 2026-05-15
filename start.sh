#!/bin/bash
# start.sh — APE-05/06 Flota Logística (Java — separado en frontend / backend)
#
# Estructura:
#   flota-logistica/
#   ├── backend/    ← Spring Boot (API REST :8080, MQTT Subscriber, Simulator, Bridge)
#   ├── frontend/   ← Dashboard HTML/CSS/JS (servido con Python en :8081)
#   └── backend/docker/  ← Mosquitto + RabbitMQ
#
# Orden de arranque:
#   1. Docker   → Mosquitto :1883  +  RabbitMQ :5672 / :15672
#   2. Backend  → Spring Boot :8082 (todo-en-uno: sim + sub + bridge + consumers + API)
#   3. Frontend → python3 -m http.server 8081  (sirve index.html)

ROOT="$(cd "$(dirname "$0")" && pwd)"
COMPOSE_FILE="$ROOT/backend/docker/docker-compose.yml"
BACKEND_DIR="$ROOT/backend"
FRONTEND_DIR="$ROOT/frontend"
LOGS_DIR="$ROOT/logs"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

echo -e "${CYAN}"
echo "═══════════════════════════════════════════════════════════════"
echo "  APE-05/06 — Flota Logística"
echo "  Backend: Spring Boot :8082 | Frontend: :8081"
echo "═══════════════════════════════════════════════════════════════"
echo -e "${NC}"

mkdir -p "$LOGS_DIR"

free_port() {
    local port="$1"
    local pids
    pids=$(lsof -ti :"$port" 2>/dev/null || true)
    if [ -n "$pids" ]; then
        echo -e "${YELLOW}  ⚠️  Puerto $port ocupado — liberando...${NC}"
        kill -9 $pids 2>/dev/null || true
        sleep 1
    fi
}

open_terminal() {
    gnome-terminal --title="$1" \
        -- bash -c "$2; echo ''; echo '══ Terminado. Cierra esta ventana. ══'; read -r" &
    sleep 0.5
}

# ── 1. Docker (Mosquitto + RabbitMQ) ─────────────────────────────────────────
echo -e "${YELLOW}[1/3] 🐳 Levantando Mosquitto + RabbitMQ...${NC}"
free_port 1883; free_port 5672; free_port 15672
docker compose -f "$COMPOSE_FILE" up -d

echo "  Esperando healthcheck de RabbitMQ..."
until docker compose -f "$COMPOSE_FILE" ps rabbitmq | grep -q "healthy"; do
    printf '.'; sleep 2
done
echo ""
echo -e "${GREEN}  ✅ Mosquitto  → localhost:1883${NC}"
echo -e "${GREEN}  ✅ RabbitMQ   → localhost:5672 | Panel: http://localhost:15672${NC}"

# ── 2. Backend — Spring Boot ──────────────────────────────────────────────────
echo ""
echo -e "${YELLOW}[2/3] 🌱 Iniciando Backend (Spring Boot)...${NC}"
free_port 8082
open_terminal "🌱 Backend — Spring Boot :8082" \
    "cd '$BACKEND_DIR' && mvn spring-boot:run 2>&1 | tee '$LOGS_DIR/backend.log'"

echo "  Esperando que Spring Boot arranque (~25s)..."
sleep 25
echo -e "${GREEN}  ✅ Backend     → http://localhost:8082/api/fleet/status${NC}"
echo -e "${GREEN}  ✅ H2 Console  → http://localhost:8082/h2-console${NC}"

# ── 3. Frontend — Python HTTP Server ─────────────────────────────────────────
echo ""
echo -e "${YELLOW}[3/3] 🌐 Iniciando Frontend (Dashboard)...${NC}"
free_port 8081
open_terminal "🌐 Frontend — Dashboard :8081" \
    "cd '$FRONTEND_DIR' && python3 -m http.server 8081 2>&1 | tee '$LOGS_DIR/frontend.log'"
sleep 2
echo -e "${GREEN}  ✅ Frontend    → http://localhost:8081${NC}"

# ── Resumen ───────────────────────────────────────────────────────────────────
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  ✅ Sistema completo iniciado${NC}"
echo ""
echo -e "  🌐 Dashboard        →  http://localhost:8081"
echo -e "  🔌 API REST         →  http://localhost:8082/api/fleet/status"
echo -e "  🗄️  H2 Console       →  http://localhost:8082/h2-console"
echo -e "  📊 RabbitMQ UI      →  http://localhost:15672  (admin / admin123)"
echo ""
echo -e "  📁 Logs: $LOGS_DIR/"
echo -e "  Para detener: ${YELLOW}./stop.sh${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
