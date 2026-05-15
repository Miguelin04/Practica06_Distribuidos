import paho.mqtt.client as mqtt
import sqlite3
import json
import os

DB_PATH = "telemetria.db"
MQTT_BROKER = "localhost"
MQTT_PORT = 1883

def init_db():
    conn = sqlite3.connect(DB_PATH)
    cursor = conn.cursor()
    # Create tables as per Architecture Image Phase 3
    cursor.execute('''CREATE TABLE IF NOT EXISTS gps_data (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        vehiculo_id TEXT,
        latitud REAL,
        longitud REAL,
        velocidad REAL,
        timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
    )''')
    cursor.execute('''CREATE TABLE IF NOT EXISTS temp_data (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        vehiculo_id TEXT,
        temperatura REAL,
        alerta INTEGER,
        timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
    )''')
    cursor.execute('''CREATE TABLE IF NOT EXISTS fuel_data (
        id INTEGER PRIMARY KEY AUTOINCREMENT,
        vehiculo_id TEXT,
        nivel REAL,
        alerta INTEGER,
        timestamp DATETIME DEFAULT CURRENT_TIMESTAMP
    )''')
    conn.commit()
    conn.close()

def on_connect(client, userdata, flags, rc):
    if rc == 0:
        print("Suscriptor: Connected to MQTT")
        client.subscribe("flota/#")
    else:
        print(f"Suscriptor: Connection failed with code {rc}")

def on_message(client, userdata, msg):
    try:
        topic = msg.topic
        payload = json.loads(msg.payload.decode())
        conn = sqlite3.connect(DB_PATH)
        cursor = conn.cursor()
        
        if "/gps" in topic:
            cursor.execute("INSERT INTO gps_data (vehiculo_id, latitud, longitud, velocidad) VALUES (?, ?, ?, ?)",
                         (payload['vehiculoId'], payload['latitud'], payload['longitud'], payload['velocidadKmh']))
        elif "/temp" in topic:
            cursor.execute("INSERT INTO temp_data (vehiculo_id, temperatura, alerta) VALUES (?, ?, ?)",
                         (payload['vehiculoId'], payload['temperaturaC'], 1 if payload['alerta'] else 0))
        elif "/fuel" in topic:
            cursor.execute("INSERT INTO fuel_data (vehiculo_id, nivel, alerta) VALUES (?, ?, ?)",
                         (payload['vehiculoId'], payload['nivelPct'], 1 if payload['alerta'] else 0))
        
        conn.commit()
        conn.close()
    except Exception as e:
        print(f"Suscriptor Error: {e}")

if __name__ == "__main__":
    init_db()
    client = mqtt.Client()
    client.on_connect = on_connect
    client.on_message = on_message
    
    try:
        client.connect(MQTT_BROKER, MQTT_PORT, 60)
        client.loop_forever()
    except Exception as e:
        print(f"Suscriptor: Could not connect to MQTT: {e}")
