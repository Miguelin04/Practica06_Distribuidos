#!/bin/bash
# stop.sh — APE-006 Flota Logística
# Detiene TODOS los servicios levantados por start.sh

ROOT="$(cd "$(dirname "$0")" && pwd)"
COMPOSE_FILE="$ROOT/docker/docker-compose.yml"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; RED='\033[0;31m'; NC='\033[0m'

echo -e "${YELLOW}Deteniendo servicios APE-006...${NC}"

# ── Matar procesos por puerto (más confiable que PIDs) ───────────────────────
for port in 8080; do
    pids=$(lsof -ti :"$port" 2>/dev/null || true)
    if [ -n "$pids" ]; then
        # shellcheck disable=SC2086
        kill -9 $pids 2>/dev/null && echo -e "  ${GREEN}✅ Puerto $port liberado (PIDs: $pids)${NC}"
    fi
done

# ── Procesos Python por nombre ────────────────────────────────────────────────
for proc in bridge.py suscriptor.py simulador.py; do
    pids=$(pgrep -f "$proc" 2>/dev/null || true)
    if [ -n "$pids" ]; then
        # shellcheck disable=SC2086
        kill -9 $pids 2>/dev/null && echo -e "  ${GREEN}✅ $proc detenido (PIDs: $pids)${NC}"
    fi
done

# ── Contenedores Docker (Mosquitto + RabbitMQ) ────────────────────────────────
echo "Bajando contenedores Docker..."
docker compose -f "$COMPOSE_FILE" down

echo -e "${GREEN}✅ Todos los servicios detenidos.${NC}"
