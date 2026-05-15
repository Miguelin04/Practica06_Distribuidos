import paho.mqtt.client as mqtt
import pika
import json
import time

MQTT_BROKER = "localhost"
MQTT_PORT = 1883
RABBIT_BROKER = "localhost"

def start_bridge():
    channel = None
    while channel is None:
        try:
            connection = pika.BlockingConnection(pika.ConnectionParameters(host=RABBIT_BROKER))
            channel = connection.channel()
            print("Bridge: Connected to RabbitMQ")
        except Exception as e:
            print(f"Bridge: Waiting for RabbitMQ... {e}")
            time.sleep(5)
        
    channel.exchange_declare(exchange='exchange.fleet', exchange_type='direct', durable=True)
    
    def on_connect(client, userdata, flags, rc):
        if rc == 0:
            print("Bridge: Connected to MQTT")
            client.subscribe("flota/#")
        else:
            print(f"Bridge: MQTT Connection failed with code {rc}")

    def on_message(client, userdata, msg):
        topic = msg.topic
        payload = msg.payload.decode()
        
        # Route depending on topic
        if "/gps" in topic:
            routing_key = "gps.routing"
        elif "/temp" in topic:
            routing_key = "temp.alert"
        elif "/fuel" in topic:
            routing_key = "fuel.routing"
        else:
            return
            
        try:
            channel.basic_publish(exchange='exchange.fleet', routing_key=routing_key, body=payload)
        except Exception as e:
            print(f"Bridge: Error publishing to RabbitMQ: {e}")
        
    client = mqtt.Client()
    client.on_connect = on_connect
    client.on_message = on_message
    
    try:
        client.connect(MQTT_BROKER, MQTT_PORT, 60)
        client.loop_forever() 
    except Exception as e:
        print(f"Bridge: Couldn't connect to MQTT: {e}")

if __name__ == "__main__":
    start_bridge()
