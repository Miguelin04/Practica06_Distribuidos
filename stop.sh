#!/bin/bash
# stop.sh — APE-006 Flota Logística
# Detiene TODOS los servicios levantados por start.sh

ROOT="$(cd "$(dirname "$0")" && pwd)"
COMPOSE_FILE="$ROOT/backend/docker/docker-compose.yml"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

echo -e "${YELLOW}Deteniendo servicios APE-05/06...${NC}"

# ── Matar procesos por puerto ───────────────────────
for port in 8081 8082; do
    pids=$(lsof -ti :"$port" 2>/dev/null || true)
    if [ -n "$pids" ]; then
        kill -9 $pids 2>/dev/null && echo -e "  ${GREEN}✅ Puerto $port liberado (PIDs: $pids)${NC}"
    fi
done

# ── Procesos Python por nombre ────────────────────────────────────────────────
for proc in main.py bridge.py simulator.py consumers.py; do
    pids=$(pgrep -f "$proc" 2>/dev/null || true)
    if [ -n "$pids" ]; then
        kill -9 $pids 2>/dev/null && echo -e "  ${GREEN}✅ $proc detenido (PIDs: $pids)${NC}"
    fi
done

# ── Contenedores Docker (Mosquitto + RabbitMQ) ────────────────────────────────
echo "Bajando infraestructura Docker..."
docker compose -f "$COMPOSE_FILE" down

echo -e "${GREEN}✅ Todos los servicios detenidos.${NC}"
