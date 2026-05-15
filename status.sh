#!/bin/bash
# status.sh — APE-006 Flota Logística
# Muestra el estado de todos los servicios y las últimas líneas de cada log.
# Uso: ./status.sh
# Opcional: ./status.sh -f   (modo "follow" — refresca cada 3 segundos)

ROOT="$(cd "$(dirname "$0")" && pwd)"
LOGS_DIR="$ROOT/logs"
COMPOSE_FILE="$ROOT/docker/docker-compose.yml"

GREEN='\033[0;32m'; YELLOW='\033[1;33m'; CYAN='\033[0;36m'
RED='\033[0;31m'; BOLD='\033[1m'; DIM='\033[2m'; NC='\033[0m'

FOLLOW=false
[ "$1" = "-f" ] && FOLLOW=true

check_port() {
    ss -tlnp 2>/dev/null | grep -q ":$1 " && echo "UP" || echo "DOWN"
}

check_proc() {
    pgrep -f "$1" > /dev/null 2>&1 && echo "UP" || echo "DOWN"
}

status_icon() {
    [ "$1" = "UP" ] && echo -e "${GREEN}✅ ACTIVO ${NC}" || echo -e "${RED}❌ CAÍDO  ${NC}"
}

show_status() {
    clear
    echo -e "${CYAN}${BOLD}"
    echo "╔═══════════════════════════════════════════════════════════════╗"
    echo "║         APE-006 — Estado de Servicios                        ║"
    printf "║  %-61s║\n" "$(date '+%Y-%m-%d %H:%M:%S')"
    echo "╚═══════════════════════════════════════════════════════════════╝"
    echo -e "${NC}"

    # ── Docker containers ────────────────────────────────────────────────────
    echo -e "${BOLD}🐳  Contenedores Docker${NC}"
    echo -e "  ${DIM}────────────────────────────────────────────────────────────${NC}"

    MOSQ=$(docker inspect --format='{{.State.Status}}' mosquitto-flota 2>/dev/null || echo "stopped")
    RABB=$(docker inspect --format='{{.State.Status}}' rabbitmq-flota 2>/dev/null || echo "stopped")

    if [ "$MOSQ" = "running" ]; then
        echo -e "  ${GREEN}✅ ACTIVO ${NC}  mosquitto-flota     → localhost:1883"
    else
        echo -e "  ${RED}❌ CAÍDO  ${NC}  mosquitto-flota     → localhost:1883"
    fi

    if [ "$RABB" = "running" ]; then
        echo -e "  ${GREEN}✅ ACTIVO ${NC}  rabbitmq-flota      → localhost:5672"
        echo -e "  ${GREEN}   Panel  ${NC}                       → http://localhost:15672"
    else
        echo -e "  ${RED}❌ CAÍDO  ${NC}  rabbitmq-flota      → localhost:5672"
    fi

    # ── Spring Boot ──────────────────────────────────────────────────────────
    echo ""
    echo -e "${BOLD}🌱  Spring Boot (fleet-monitor)${NC}"
    echo -e "  ${DIM}────────────────────────────────────────────────────────────${NC}"
    SB=$(check_port 8080)
    echo -e "  $(status_icon $SB) Puerto 8080  →  http://localhost:8080/api/fleet/status"

    # quick API check
    if [ "$SB" = "UP" ]; then
        RESP=$(curl -s -o /dev/null -w "%{http_code}" --connect-timeout 2 http://localhost:8080/api/fleet/status 2>/dev/null || echo "ERR")
        if [ "$RESP" = "200" ]; then
            echo -e "  ${GREEN}   API OK${NC} (HTTP $RESP)"
        else
            echo -e "  ${YELLOW}   API responde HTTP $RESP${NC}"
        fi
    fi

    # ── Procesos Python ──────────────────────────────────────────────────────
    echo ""
    echo -e "${BOLD}🐍  Procesos Python${NC}"
    echo -e "  ${DIM}────────────────────────────────────────────────────────────${NC}"
    echo -e "  $(status_icon $(check_proc bridge.py))    bridge.py     (MQTT→RabbitMQ)"
    echo -e "  $(status_icon $(check_proc suscriptor.py)) suscriptor.py (MQTT→SQLite)"
    echo -e "  $(status_icon $(check_proc simulador.py))  simulador.py  (publica GPS/temp/comb)"

    # ── Últimas líneas de logs ───────────────────────────────────────────────
    echo ""
    echo -e "${BOLD}📄  Últimas líneas de logs  ${DIM}(logs/)${NC}"
    echo -e "  ${DIM}────────────────────────────────────────────────────────────${NC}"

    for logfile in spring-boot bridge suscriptor simulador; do
        LOGPATH="$LOGS_DIR/${logfile}.log"
        if [ -f "$LOGPATH" ] && [ -s "$LOGPATH" ]; then
            echo -e "${CYAN}  ▶ ${logfile}.log${NC}"
            tail -4 "$LOGPATH" | while IFS= read -r line; do
                echo -e "    ${DIM}$line${NC}"
            done
            echo ""
        else
            echo -e "${DIM}  ▶ ${logfile}.log  (vacío o sin crear)${NC}"
        fi
    done

    # ── Footer ───────────────────────────────────────────────────────────────
    echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
    echo -e "  ${DIM}Logs completos en:  $LOGS_DIR/${NC}"
    if $FOLLOW; then
        echo -e "  ${YELLOW}Modo follow activo — Ctrl+C para salir${NC}"
    else
        echo -e "  Monitoreo continuo: ${YELLOW}./status.sh -f${NC}   |   Detener: ${YELLOW}./stop.sh${NC}"
    fi
    echo -e "${CYAN}═══════════════════════════════════════════════════════════════${NC}"
}

if $FOLLOW; then
    while true; do
        show_status
        sleep 3
    done
else
    show_status
fi
