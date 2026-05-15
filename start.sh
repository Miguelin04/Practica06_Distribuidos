#!/bin/bash
# start.sh — APE-05/06 Flota Logística (Híbrido Python + Spring Boot)
#
# Arquitectura:
#   Simulador (Py) → MQTT → Bridge (Py) → RabbitMQ → Spring Boot → API REST
#
# Puertos:
#   - Spring Boot (Backend): 8082
#   - HTTP Server (Frontend): 8081
#   - MQTT: 1883
#   - RabbitMQ: 5672 / 15672

ROOT="$(cd "$(dirname "$0")" && pwd)"
PYTHON_DIR="$ROOT/backend/py"
FRONTEND_DIR="$ROOT/frontend"
LOGS_DIR="$ROOT/logs"
COMPOSE_FILE="$ROOT/docker/docker-compose.yml"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; NC='\033[0m'

echo -e "${CYAN}"
echo "═══════════════════════════════════════════════════════════════"
echo "  APE-05/06 — Flota Logística (Modo Silencioso)"
echo "  Backend: Spring Boot :8082 | Frontend: :8081"
echo "═══════════════════════════════════════════════════════════════"
echo -e "${NC}"

mkdir -p "$LOGS_DIR"

free_port() {
    local port="$1"
    local pids
    pids=$(lsof -ti :"$port" 2>/dev/null || true)
    if [ -n "$pids" ]; then
        echo -e "${YELLOW}  Puerto $port ocupado — liberando...${NC}"
        kill -9 $pids 2>/dev/null || true
        sleep 1
    fi
}

run_background() {
    # $1: Name, $2: Command, $3: Log file
    nohup bash -c "$2" > "$3" 2>&1 &
    echo -e "${GREEN}  $1 iniciado en segundo plano.${NC}"
}

# ── 0. Dependencias (Virtual Environment) ──────────────────────────────────────
echo -e "${YELLOW}[0/3] Configurando entorno virtual y dependencias...${NC}"

if [ ! -d "$PYTHON_DIR/venv" ]; then
    echo "  Creando entorno virtual en $PYTHON_DIR/venv..."
    python3 -m venv "$PYTHON_DIR/venv"
fi

source "$PYTHON_DIR/venv/bin/activate"
pip install -r "$PYTHON_DIR/requirements.txt" --quiet
echo -e "${GREEN} Entorno virtual listo${NC}"

# ── 1. Docker (Mosquitto + RabbitMQ) ─────────────────────────────────────────
echo -e "${YELLOW}[1/3] 🐳 Levantando Infraestructura (Docker)...${NC}"
free_port 1883; free_port 5672; free_port 15672
docker compose -f "$COMPOSE_FILE" up -d

echo "  Esperando healthcheck de RabbitMQ..."
until docker compose -f "$COMPOSE_FILE" ps rabbitmq | grep -q "healthy"; do
    printf '.'; sleep 2
done
echo ""
echo -e "${GREEN} Mosquitto (1883) y RabbitMQ (5672) activos${NC}"

# ── 2. Backend — Spring Boot (APE-06) ─────────────────────────────────────────
echo ""
echo -e "${YELLOW}[2/3] 🌱 Iniciando Backend (Spring Boot)...${NC}"
free_port 8082
run_background "Backend (Spring Boot)" \
    "cd '$ROOT/backend' && mvn spring-boot:run" \
    "$LOGS_DIR/backend.log"

# ── 2b. APE-05 — Python (Simulador + Bridge + Suscriptor) ─────────────────────
echo -e "${YELLOW}   🐍 Iniciando Componentes Python (APE-05)...${NC}"
run_background "Simulador (Python)" \
    "source '$PYTHON_DIR/venv/bin/activate' && cd '$PYTHON_DIR' && python3 simulator.py" \
    "$LOGS_DIR/simulator.log"

run_background "Bridge (Python)" \
    "source '$PYTHON_DIR/venv/bin/activate' && cd '$PYTHON_DIR' && python3 bridge.py" \
    "$LOGS_DIR/bridge.log"

run_background "Suscriptor (Python)" \
    "source '$PYTHON_DIR/venv/bin/activate' && cd '$PYTHON_DIR' && python3 suscriptor.py" \
    "$LOGS_DIR/suscriptor.log"

echo "  Esperando que el sistema arranque (compilación Maven)..."
sleep 15

# ── 3. Frontend — HTTP Server ───────────────────────────────────────────────
echo ""
echo -e "${YELLOW}[3/3] 🌐 Iniciando Frontend (Dashboard Moderno)...${NC}"
free_port 8081
run_background "Frontend (Dashboard)" \
    "cd '$FRONTEND_DIR' && python3 -m http.server 8081" \
    "$LOGS_DIR/frontend.log"

echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN} Sistema completo iniciado (SIN VENTANAS ADICIONALES)${NC}"
echo ""
echo -e "  🌐 Dashboard        →  http://localhost:8081"
echo -e "  🔌 API REST         →  http://localhost:8082/api/fleet/status"
echo -e "  📊 RabbitMQ UI      →  http://localhost:15672"
echo ""
echo -e "  📁 Logs disponibles en: $LOGS_DIR/"
echo -e "  Para detener: ${YELLOW}./stop.sh${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
