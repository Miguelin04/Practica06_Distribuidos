"""
suscriptor.py — APE-006 Flota Logística
Suscriptor MQTT que escucha todos los topics de la flota (wildcard flota/#)
y persiste cada mensaje en una base de datos SQLite3 local.
"""

import paho.mqtt.client as mqtt
import sqlite3
import json
from datetime import datetime

# ─── Configuración del broker MQTT ───────────────────────────────────────────
MQTT_HOST = "localhost"
MQTT_PORT = 1883
TOPIC_WILDCARD = "flota/#"

# ─── Base de datos SQLite ─────────────────────────────────────────────────────
DB_PATH = "telemetria.db"

def inicializar_db() -> sqlite3.Connection:
    """Crea la tabla de telemetría si no existe y devuelve la conexión."""
    conn = sqlite3.connect(DB_PATH, check_same_thread=False)
    cursor = conn.cursor()
    cursor.execute("""
        CREATE TABLE IF NOT EXISTS telemetria (
            id          INTEGER PRIMARY KEY AUTOINCREMENT,
            vehiculo_id TEXT    NOT NULL,
            tipo        TEXT    NOT NULL,  -- 'gps', 'temp', 'combustible'
            payload     TEXT    NOT NULL,
            timestamp   DATETIME DEFAULT CURRENT_TIMESTAMP
        )
    """)
    conn.commit()
    print(f"[SUSCRIPTOR] 🗄️  Base de datos lista en '{DB_PATH}'")
    return conn

# ─── Callbacks del cliente MQTT ──────────────────────────────────────────────

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print(f"[SUSCRIPTOR] Conectado a Mosquitto ({MQTT_HOST}:{MQTT_PORT})")
        client.subscribe(TOPIC_WILDCARD)
        print(f"[SUSCRIPTOR] Suscrito a '{TOPIC_WILDCARD}'")
    else:
        print(f"[SUSCRIPTOR] Error de conexión, código: {rc}")

def on_message(client, userdata, msg):
    """Procesa cada mensaje entrante y lo persiste en SQLite."""
    topic  = msg.topic           # ej: flota/VH-001/gps
    payload_str = msg.payload.decode("utf-8")

    # Extraer vehiculo_id y tipo a partir del topic (flota/{id}/{tipo})
    partes = topic.split("/")
    if len(partes) < 3:
        print(f"[SUSCRIPTOR] Topic inesperado: {topic}")
        return

    vehiculo_id = partes[1]
    tipo_raw    = partes[2]   # gps | temperatura | combustible

    # Normalizar el tipo para coincidir con el esquema SQL
    tipo_map = {"gps": "gps", "temperatura": "temp", "combustible": "combustible"}
    tipo = tipo_map.get(tipo_raw, tipo_raw)

    # Mostrar en consola
    print(f"\n[SUSCRIPTOR] {topic}")
    try:
        datos = json.loads(payload_str)
        print(f"             Payload: {json.dumps(datos, indent=2, ensure_ascii=False)}")
    except json.JSONDecodeError:
        print(f"             Payload (raw): {payload_str}")

    # Persistir en SQLite
    conn: sqlite3.Connection = userdata["db"]
    cursor = conn.cursor()
    cursor.execute(
        "INSERT INTO telemetria (vehiculo_id, tipo, payload) VALUES (?, ?, ?)",
        (vehiculo_id, tipo, payload_str)
    )
    conn.commit()
    print(f"[SUSCRIPTOR] Guardado en BD — vehiculo={vehiculo_id}, tipo={tipo}")

def on_disconnect(client, userdata, rc):
    print(f"\n[SUSCRIPTOR] Desconectado (rc={rc})")

# ─── Utilidad: mostrar últimos registros ─────────────────────────────────────

def mostrar_estadisticas(conn: sqlite3.Connection):
    cursor = conn.cursor()
    cursor.execute("SELECT COUNT(*) FROM telemetria")
    total = cursor.fetchone()[0]
    cursor.execute("""
        SELECT vehiculo_id, tipo, COUNT(*) as cnt
        FROM telemetria
        GROUP BY vehiculo_id, tipo
        ORDER BY vehiculo_id, tipo
    """)
    print(f"\n[SUSCRIPTOR] Total registros: {total}")
    for row in cursor.fetchall():
        print(f"             {row[0]} | {row[1]:<12} | {row[2]} msgs")

# ─── Lógica principal ─────────────────────────────────────────────────────────

def main():
    conn = inicializar_db()

    client = mqtt.Client(
        client_id="suscriptor-flota",
        protocol=mqtt.MQTTv311,
        userdata={"db": conn}
    )
    client.on_connect    = on_connect
    client.on_message    = on_message
    client.on_disconnect = on_disconnect

    print("[SUSCRIPTOR] Conectando a Mosquitto…")
    client.connect(MQTT_HOST, MQTT_PORT, 60)

    try:
        client.loop_forever()
    except KeyboardInterrupt:
        print("\n[SUSCRIPTOR] Deteniendo suscriptor…")
        mostrar_estadisticas(conn)
    finally:
        conn.close()
        client.disconnect()
        print("[SUSCRIPTOR] Finalizado.")

if __name__ == "__main__":
    main()
