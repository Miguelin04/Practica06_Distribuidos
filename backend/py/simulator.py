import paho.mqtt.client as mqtt
import time
import json
import random

VEHICULOS = ['VH-001', 'VH-002', 'VH-003']
MQTT_BROKER = "localhost"
MQTT_PORT = 1883

def start_simulator():
    client = mqtt.Client()
    try:
        client.connect(MQTT_BROKER, MQTT_PORT, 60)
        print("Simulator: Connected to MQTT")
    except Exception as e:
        print(f"Simulator: Couldn't connect to MQTT: {e}")
        return

    client.loop_start()

    while True:
        for vid in VEHICULOS:
            # GPS Telemetry
            gps = {
                "vehiculoId": vid,
                "latitud": -2.1709 + random.uniform(-0.05, 0.05),
                "longitud": -79.9224 + random.uniform(-0.05, 0.05),
                "velocidadKmh": random.uniform(20, 110)
            }
            client.publish(f"flota/{vid}/gps", json.dumps(gps))

            # Temperature Telemetry
            tempC = random.uniform(-2, 8)
            temp = {
                "vehiculoId": vid,
                "temperaturaC": tempC,
                "alerta": tempC > 4
            }
            client.publish(f"flota/{vid}/temp", json.dumps(temp))

            # Fuel Telemetry
            fuelPct = random.uniform(5, 100)
            fuel = {
                "vehiculoId": vid,
                "nivelPct": fuelPct,
                "alerta": fuelPct < 20
            }
            client.publish(f"flota/{vid}/fuel", json.dumps(fuel))
            
        time.sleep(5)

if __name__ == "__main__":
    start_simulator()
