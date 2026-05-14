"""
bridge.py — APE-006 Flota Logística
Bridge MQTT → RabbitMQ.
Suscribe a todos los topics de la flota en Mosquitto y reenvía cada mensaje
al exchange 'exchange.fleet' en RabbitMQ con la routing key correcta:
  flota/{id}/gps         → gps.routing
  flota/{id}/temperatura → temp.alert
  flota/{id}/combustible → fuel.routing
"""

import paho.mqtt.client as mqtt
import pika
import json
import time
from datetime import datetime

# ─── Configuración MQTT ───────────────────────────────────────────────────────
MQTT_HOST     = "localhost"
MQTT_PORT     = 1883
TOPIC_WILDCARD = "flota/#"

# ─── Configuración RabbitMQ ───────────────────────────────────────────────────
RABBIT_HOST     = "localhost"
RABBIT_PORT     = 5672
RABBIT_USER     = "admin"
RABBIT_PASS     = "admin123"
EXCHANGE_NAME   = "exchange.fleet"
EXCHANGE_TYPE   = "direct"

# ─── Mapa de tipo de sensor → routing key ────────────────────────────────────
ROUTING_MAP = {
    "gps":         "gps.routing",
    "temperatura": "temp.alert",
    "combustible": "fuel.routing",
}

# ─── Estado global del canal RabbitMQ ────────────────────────────────────────
rabbit_channel = None

def conectar_rabbitmq() -> pika.BlockingConnection:
    """Crea y devuelve una conexión bloqueante a RabbitMQ."""
    credentials = pika.PlainCredentials(RABBIT_USER, RABBIT_PASS)
    params = pika.ConnectionParameters(
        host=RABBIT_HOST,
        port=RABBIT_PORT,
        credentials=credentials,
        heartbeat=600,
        blocked_connection_timeout=300
    )
    connection = pika.BlockingConnection(params)
    channel = connection.channel()

    # Declarar el exchange como durable para sobrevivir reinicios del broker
    channel.exchange_declare(
        exchange=EXCHANGE_NAME,
        exchange_type=EXCHANGE_TYPE,
        durable=True
    )

    # Pre-declarar las 3 colas y enlazarlas al exchange
    colas = {
        "cola.gps":          "gps.routing",
        "cola.temperatura":  "temp.alert",
        "cola.combustible":  "fuel.routing",
        "cola.notificaciones": "temp.alert",   # también recibe alertas de temp
    }
    for cola, rk in colas.items():
        channel.queue_declare(queue=cola, durable=True)
        channel.queue_bind(exchange=EXCHANGE_NAME, queue=cola, routing_key=rk)
        print(f"[BRIDGE] Cola '{cola}' ← exchange '{EXCHANGE_NAME}' (rk={rk})")

    print(f"[BRIDGE] RabbitMQ listo — exchange '{EXCHANGE_NAME}'")
    return connection, channel

# ─── Callbacks MQTT ──────────────────────────────────────────────────────────

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print(f"[BRIDGE] Conectado a Mosquitto ({MQTT_HOST}:{MQTT_PORT})")
        client.subscribe(TOPIC_WILDCARD)
        print(f"[BRIDGE] Suscrito a '{TOPIC_WILDCARD}'")
    else:
        print(f"[BRIDGE] Error MQTT, código: {rc}")

def on_message(client, userdata, msg):
    """Recibe mensaje MQTT y lo publica en RabbitMQ con la routing key correcta."""
    global rabbit_channel

    topic       = msg.topic
    payload_str = msg.payload.decode("utf-8")

    # Descomponer topic: flota / {vehiculo_id} / {sensor}
    partes = topic.split("/")
    if len(partes) < 3:
        print(f"[BRIDGE] Topic inesperado ignorado: {topic}")
        return

    sensor = partes[2]   # gps | temperatura | combustible

    routing_key = ROUTING_MAP.get(sensor)
    if routing_key is None:
        print(f"[BRIDGE] Sensor desconocido '{sensor}' — sin routing key")
        return

    # Enriquecer el payload con metadatos del bridge
    try:
        datos = json.loads(payload_str)
    except json.JSONDecodeError:
        datos = {"raw": payload_str}

    datos["_bridge_topic"]      = topic
    datos["_bridge_timestamp"]  = datetime.now().isoformat()
    payload_enriquecido = json.dumps(datos)

    # Publicar en RabbitMQ
    try:
        rabbit_channel.basic_publish(
            exchange=EXCHANGE_NAME,
            routing_key=routing_key,
            body=payload_enriquecido,
            properties=pika.BasicProperties(
                delivery_mode=2,       # mensaje persistente
                content_type="application/json"
            )
        )
        print(
            f"[BRIDGE] {topic:<40} → RabbitMQ [{routing_key}]"
        )
    except Exception as e:
        print(f"[BRIDGE] Error publicando en RabbitMQ: {e}")

def on_disconnect(client, userdata, rc):
    print(f"[BRIDGE] MQTT desconectado (rc={rc})")

# ─── Lógica principal ─────────────────────────────────────────────────────────

def main():
    global rabbit_channel

    # 1) Conectar a RabbitMQ primero
    print("[BRIDGE] Conectando a RabbitMQ…")
    rabbit_conn, rabbit_channel = conectar_rabbitmq()

    # 2) Conectar al broker MQTT
    client = mqtt.Client(client_id="bridge-flota", protocol=mqtt.MQTTv311)
    client.on_connect    = on_connect
    client.on_message    = on_message
    client.on_disconnect = on_disconnect

    print("[BRIDGE] Conectando a Mosquitto…")
    client.connect(MQTT_HOST, MQTT_PORT, 60)

    print("[BRIDGE] Bridge activo — esperando mensajes MQTT…")
    try:
        client.loop_forever()
    except KeyboardInterrupt:
        print("\n[BRIDGE] Deteniendo bridge…")
    finally:
        client.disconnect()
        rabbit_conn.close()
        print("[BRIDGE] Finalizado.")

if __name__ == "__main__":
    main()
