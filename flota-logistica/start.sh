#!/bin/bash
# start.sh — APE-006 Flota Logística
# Cada servicio se abre en su propia terminal gnome-terminal.
# Los logs quedan en logs/ dentro del proyecto.
# Uso: chmod +x start.sh && ./start.sh

ROOT="$(cd "$(dirname "$0")" && pwd)"
COMPOSE_FILE="$ROOT/docker/docker-compose.yml"
MQTT_DIR="$ROOT/mqtt-parte1"
FLEET_DIR="$ROOT/fleet-monitor"
VENV_DIR="$ROOT/.venv"
LOGS_DIR="$ROOT/logs"
PIDS_DIR="$ROOT/logs"   # PIDs también en logs/

# ─── Colores ──────────────────────────────────────────────────────────────────
GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'; RED='\033[0;31m'; NC='\033[0m'

echo -e "${CYAN}"
echo "═══════════════════════════════════════════════════════════════"
echo "  APE-006 — Flota Logística — Arranque de servicios"
echo "  Arquitectura: Python → MQTT → Bridge → RabbitMQ → Spring Boot"
echo "═══════════════════════════════════════════════════════════════"
echo -e "${NC}"

# Crear carpeta de logs si no existe
mkdir -p "$LOGS_DIR"

# ── Helper: liberar un puerto si está ocupado (sin sudo — solo procesos propios)
free_port() {
    local port="$1"
    local pids
    pids=$(lsof -ti :"$port" 2>/dev/null || true)
    if [ -n "$pids" ]; then
        echo -e "${YELLOW}      ⚠️  Puerto $port ocupado (PIDs: $pids). Liberando...${NC}"
        # shellcheck disable=SC2086
        kill -9 $pids 2>/dev/null || true
        sleep 1
        echo -e "${GREEN}      ✅ Puerto $port liberado${NC}"
    fi
}

# ── Helper: abrir una gnome-terminal con título y comando ─────────────────────
open_terminal() {
    local title="$1"
    local cmd="$2"
    gnome-terminal \
        --title="$title" \
        -- bash -c "$cmd; echo ''; echo '══ Proceso terminado ══ Cierra esta ventana cuando quieras.'; read -r" &
    sleep 0.5
}

# ── 0. Entorno virtual Python ─────────────────────────────────────────────────
echo -e "${YELLOW}[0/5] 🐍 Preparando entorno virtual Python...${NC}"

if [ ! -d "$VENV_DIR" ]; then
    echo "      Creando entorno virtual en .venv/ ..."
    python3 -m venv "$VENV_DIR"
    echo -e "${GREEN}      ✅ Entorno virtual creado${NC}"
else
    echo -e "${GREEN}      ✅ Entorno virtual existente (.venv/)${NC}"
fi

source "$VENV_DIR/bin/activate"

if ! python -c "import paho, pika" 2>/dev/null; then
    echo "      Instalando dependencias: paho-mqtt pika ..."
    pip install --quiet paho-mqtt pika
    echo -e "${GREEN}      ✅ Dependencias instaladas${NC}"
else
    echo -e "${GREEN}      ✅ Dependencias ya disponibles${NC}"
fi

# ── 1. Infraestructura Docker (Mosquitto + RabbitMQ) ─────────────────────────
echo ""
echo -e "${YELLOW}[1/5] 🐳 Levantando infraestructura Docker...${NC}"
free_port 1883
free_port 5672
free_port 15672
docker compose -f "$COMPOSE_FILE" up -d

echo "      Esperando a que RabbitMQ pase el healthcheck..."
until docker compose -f "$COMPOSE_FILE" ps rabbitmq | grep -q "healthy"; do
    printf '.'
    sleep 2
done
echo ""
echo -e "${GREEN}      ✅ Mosquitto listo   → localhost:1883${NC}"
echo -e "${GREEN}      ✅ RabbitMQ listo    → localhost:5672${NC}"
echo -e "${GREEN}      ✅ Panel RabbitMQ    → http://localhost:15672 (admin/admin123)${NC}"

# ── 2. Spring Boot ────────────────────────────────────────────────────────────
echo ""
echo -e "${YELLOW}[2/5] 🌱 Iniciando Spring Boot (fleet-monitor)...${NC}"
free_port 8080
open_terminal "🌱 Spring Boot — fleet-monitor" \
    "cd '$FLEET_DIR' && mvn spring-boot:run 2>&1 | tee '$LOGS_DIR/spring-boot.log'"

echo "      Esperando que Spring Boot arranque (~30s)..."
sleep 30
echo -e "${GREEN}      ✅ Spring Boot listo → http://localhost:8080/api/fleet/status${NC}"

# ── 3. Bridge MQTT → RabbitMQ ─────────────────────────────────────────────────
echo ""
echo -e "${YELLOW}[3/5] 🔀 Iniciando bridge MQTT → RabbitMQ...${NC}"
open_terminal "🔀 Bridge MQTT→RabbitMQ" \
    "source '$VENV_DIR/bin/activate' && cd '$MQTT_DIR' && python bridge.py 2>&1 | tee '$LOGS_DIR/bridge.log'"
echo -e "${GREEN}      ✅ Bridge activo${NC}"

# ── 4. Suscriptor MQTT → SQLite ───────────────────────────────────────────────
echo ""
echo -e "${YELLOW}[4/5] 👂 Iniciando suscriptor MQTT → SQLite...${NC}"
open_terminal "👂 Suscriptor MQTT→SQLite" \
    "source '$VENV_DIR/bin/activate' && cd '$MQTT_DIR' && python suscriptor.py 2>&1 | tee '$LOGS_DIR/suscriptor.log'"
echo -e "${GREEN}      ✅ Suscriptor activo${NC}"

# ── 5. Simulador de vehículos ─────────────────────────────────────────────────
echo ""
echo -e "${YELLOW}[5/5] 🚛 Iniciando simulador de vehículos...${NC}"
open_terminal "🚛 Simulador de Vehículos" \
    "source '$VENV_DIR/bin/activate' && cd '$MQTT_DIR' && python simulador.py 2>&1 | tee '$LOGS_DIR/simulador.log'"
echo -e "${GREEN}      ✅ Simulador activo${NC}"

# ── Resumen final ─────────────────────────────────────────────────────────────
echo ""
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
echo -e "${GREEN}  ✅ Todos los servicios iniciados en terminales separadas${NC}"
echo ""
echo -e "  🌱 Spring Boot   →  http://localhost:8080/api/fleet/status"
echo -e "  📊 RabbitMQ UI   →  http://localhost:15672  (admin / admin123)"
echo ""
echo -e "  📁 Logs en:  $LOGS_DIR/"
echo -e "     📄 spring-boot.log  |  bridge.log  |  suscriptor.log  |  simulador.log"
echo ""
echo -e "  Para monitorear:   ${YELLOW}./status.sh${NC}"
echo -e "  Para detener todo: ${YELLOW}./stop.sh${NC}"
echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
