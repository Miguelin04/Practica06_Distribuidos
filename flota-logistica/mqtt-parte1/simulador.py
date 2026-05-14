"""
simulador.py — APE-006 Flota Logística
Simula 3 vehículos publicando telemetría (GPS, temperatura, combustible)
al broker MQTT Mosquitto en los topics jerárquicos definidos por la guía.
"""

import paho.mqtt.client as mqtt
import json
import random
import time
from datetime import datetime

# ─── Configuración del broker MQTT ───────────────────────────────────────────
MQTT_HOST = "localhost"
MQTT_PORT = 1883
MQTT_KEEPALIVE = 60

# ─── Vehículos a simular ──────────────────────────────────────────────────────
VEHICULOS = ["VH-001", "VH-002", "VH-003"]

# ─── Callbacks del cliente MQTT ──────────────────────────────────────────────

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print(f"[SIMULADOR] Conectado a Mosquitto en {MQTT_HOST}:{MQTT_PORT}")
    else:
        print(f"[SIMULADOR] Error de conexión, código: {rc}")

def on_publish(client, userdata, mid):
    print(f"[SIMULADOR] Mensaje publicado (mid={mid})")

# ─── Generadores de datos realistas ──────────────────────────────────────────

def generar_gps(vehiculo_id: str) -> dict:
    """Coordenadas dentro del territorio ecuatoriano (lat -4 a 0, lng -79 a -75)."""
    return {
        "vehiculo_id": vehiculo_id,
        "latitud": round(random.uniform(-4.0, 0.0), 6),
        "longitud": round(random.uniform(-79.0, -75.0), 6),
        "velocidad_kmh": round(random.uniform(0, 120), 1),
        "timestamp": datetime.now().isoformat()
    }

def generar_temperatura(vehiculo_id: str) -> dict:
    """Temperatura de carga entre -5°C y 25°C (cadena de frío)."""
    temp = round(random.uniform(-5.0, 25.0), 1)
    return {
        "vehiculo_id": vehiculo_id,
        "temperatura_c": temp,
        "alerta": temp > 20.0 or temp < -3.0,   # umbral de alerta
        "timestamp": datetime.now().isoformat()
    }

def generar_combustible(vehiculo_id: str) -> dict:
    """Nivel de combustible entre 0% y 100%."""
    nivel = round(random.uniform(0.0, 100.0), 1)
    return {
        "vehiculo_id": vehiculo_id,
        "nivel_pct": nivel,
        "alerta_bajo": nivel < 15.0,              # alerta por nivel bajo
        "timestamp": datetime.now().isoformat()
    }

# ─── Lógica principal ─────────────────────────────────────────────────────────

def publicar_telemetria(client):
    """Publica los 3 tipos de telemetría para cada vehículo."""
    for vid in VEHICULOS:
        # GPS
        topic_gps = f"flota/{vid}/gps"
        payload_gps = json.dumps(generar_gps(vid))
        client.publish(topic_gps, payload_gps)
        print(f"[SIMULADOR] GPS      → {topic_gps}: {payload_gps}")

        # Temperatura
        topic_temp = f"flota/{vid}/temperatura"
        payload_temp = json.dumps(generar_temperatura(vid))
        client.publish(topic_temp, payload_temp)
        print(f"[SIMULADOR] 🌡️  Temp     → {topic_temp}: {payload_temp}")

        # Combustible
        topic_comb = f"flota/{vid}/combustible"
        payload_comb = json.dumps(generar_combustible(vid))
        client.publish(topic_comb, payload_comb)
        print(f"[SIMULADOR] Comb     → {topic_comb}: {payload_comb}")

        print("─" * 70)

def main():
    client = mqtt.Client(client_id="simulador-flota", protocol=mqtt.MQTTv311)
    client.on_connect = on_connect
    client.on_publish  = on_publish

    print("[SIMULADOR] Conectando a Mosquitto…")
    client.connect(MQTT_HOST, MQTT_PORT, MQTT_KEEPALIVE)
    client.loop_start()

    time.sleep(1)  # esperar conexión

    ciclo = 1
    try:
        while True:
            print(f"\n{'═'*70}")
            print(f"  CICLO #{ciclo} — {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
            print(f"{'═'*70}")
            publicar_telemetria(client)
            ciclo += 1
            time.sleep(5)   # intervalo entre ciclos de telemetría
    except KeyboardInterrupt:
        print("\n[SIMULADOR] Deteniendo simulador…")
    finally:
        client.loop_stop()
        client.disconnect()
        print("[SIMULADOR] Desconectado.")

if __name__ == "__main__":
    main()
