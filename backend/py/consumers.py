import pika
import json
import time
from models import SessionLocal, GpsMessage, TempAlert, FuelAlert

RABBIT_BROKER = "localhost"

def start_consumers():
    channel = None
    while channel is None:
        try:
            connection = pika.BlockingConnection(pika.ConnectionParameters(host=RABBIT_BROKER))
            channel = connection.channel()
            print("Consumers: Connected to RabbitMQ")
        except Exception as e:
            print(f"Consumers: Waiting for RabbitMQ... {e}")
            time.sleep(5)
        
    channel.exchange_declare(exchange='fleet.direct', exchange_type='direct')
    
    channel.queue_declare(queue='cola.gps', durable=True)
    channel.queue_declare(queue='cola.temperatura', durable=True)
    channel.queue_declare(queue='cola.combustible', durable=True)
    
    channel.queue_bind(exchange='fleet.direct', queue='cola.gps', routing_key='gps.routing')
    channel.queue_bind(exchange='fleet.direct', queue='cola.temperatura', routing_key='temp.alert')
    channel.queue_bind(exchange='fleet.direct', queue='cola.combustible', routing_key='fuel.routing')
    
    def process_gps(ch, method, properties, body):
        try:
            db = SessionLocal()
            data = json.loads(body)
            msg = GpsMessage(
                vehiculo_id=data['vehiculoId'], 
                latitud=data['latitud'], 
                longitud=data['longitud'], 
                velocidad_kmh=data['velocidadKmh']
            )
            db.add(msg)
            db.commit()
            db.close()
        except Exception as e:
            print(f"Consumer GPS Error: {e}")
        
    def process_temp(ch, method, properties, body):
        try:
            db = SessionLocal()
            data = json.loads(body)
            msg = TempAlert(
                vehiculo_id=data['vehiculoId'], 
                temperatura_c=data['temperaturaC'], 
                alerta=data['alerta']
            )
            db.add(msg)
            db.commit()
            db.close()
        except Exception as e:
            print(f"Consumer Temp Error: {e}")
        
    def process_fuel(ch, method, properties, body):
        try:
            db = SessionLocal()
            data = json.loads(body)
            msg = FuelAlert(
                vehiculo_id=data['vehiculoId'], 
                nivel_pct=data['nivelPct'], 
                alerta=data['alerta']
            )
            db.add(msg)
            db.commit()
            db.close()
        except Exception as e:
            print(f"Consumer Fuel Error: {e}")
        
    channel.basic_consume(queue='cola.gps', on_message_callback=process_gps, auto_ack=True)
    channel.basic_consume(queue='cola.temperatura', on_message_callback=process_temp, auto_ack=True)
    channel.basic_consume(queue='cola.combustible', on_message_callback=process_fuel, auto_ack=True)
    
    print("Consumers: Starting to consume messages...")
    channel.start_consuming()

if __name__ == "__main__":
    start_consumers()
