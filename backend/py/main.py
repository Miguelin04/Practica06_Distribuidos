from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
import threading
from models import SessionLocal, GpsMessage, TempAlert, FuelAlert
from sqlalchemy import func
import simulator, bridge, consumers
import uvicorn

app = FastAPI(title="Fleet Monitor API (Python Backend)")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*"],
)

@app.on_event("startup")
def startup_event():
    # Start background threads
    threading.Thread(target=simulator.start_simulator, daemon=True).start()
    threading.Thread(target=bridge.start_bridge, daemon=True).start()
    threading.Thread(target=consumers.start_consumers, daemon=True).start()

@app.get("/api/fleet/health")
def health():
    return {"status": "UP"}

@app.get("/api/fleet/status")
def get_status():
    db = SessionLocal()
    # Unique active vehicles
    vehiculos = [row[0] for row in db.query(GpsMessage.vehiculo_id).distinct().all()]
    total_gps = db.query(GpsMessage).count()
    alertas_temp = db.query(TempAlert).filter(TempAlert.alerta == True).count()
    alertas_fuel = db.query(FuelAlert).filter(FuelAlert.alerta == True).count()
    
    ultima_pos = []
    for vid in vehiculos:
        pos = db.query(GpsMessage).filter(GpsMessage.vehiculo_id == vid).order_by(GpsMessage.id.desc()).first()
        if pos:
            ultima_pos.append({
                "vehiculoId": pos.vehiculo_id,
                "latitud": pos.latitud,
                "longitud": pos.longitud,
                "velocidadKmh": pos.velocidad_kmh,
                "receivedAt": pos.received_at.isoformat() + "Z"
            })
            
    db.close()
    return {
        "vehiculos_activos": len(vehiculos),
        "total_gps": total_gps,
        "alertas_temp_activas": alertas_temp,
        "alertas_fuel_activas": alertas_fuel,
        "ultima_posicion": ultima_pos
    }

@app.get("/api/fleet/alertas")
def get_alertas():
    db = SessionLocal()
    temp_alerts = db.query(TempAlert).filter(TempAlert.alerta == True).order_by(TempAlert.id.desc()).limit(50).all()
    fuel_alerts = db.query(FuelAlert).filter(FuelAlert.alerta == True).order_by(FuelAlert.id.desc()).limit(50).all()
    
    data = {
        "total_alertas_temp": db.query(TempAlert).filter(TempAlert.alerta == True).count(),
        "total_alertas_fuel": db.query(FuelAlert).filter(FuelAlert.alerta == True).count(),
        "alertas_temperatura": [
            {
                "vehiculoId": a.vehiculo_id,
                "temperaturaC": a.temperatura_c,
                "receivedAt": a.received_at.isoformat() + "Z"
            } for a in temp_alerts
        ],
        "alertas_combustible": [
            {
                "vehiculoId": a.vehiculo_id,
                "nivelPct": a.nivel_pct,
                "receivedAt": a.received_at.isoformat() + "Z"
            } for a in fuel_alerts
        ]
    }
    db.close()
    return data

@app.get("/api/fleet/vehicle/{id}/telemetry")
def get_telemetry(id: str):
    db = SessionLocal()
    gps = db.query(GpsMessage).filter(GpsMessage.vehiculo_id == id).order_by(GpsMessage.id.desc()).first()
    temp = db.query(TempAlert).filter(TempAlert.vehiculo_id == id).order_by(TempAlert.id.desc()).first()
    fuel = db.query(FuelAlert).filter(FuelAlert.vehiculo_id == id).order_by(FuelAlert.id.desc()).first()
    count = db.query(GpsMessage).filter(GpsMessage.vehiculo_id == id).count()
    db.close()
    
    return {
        "ultimo_gps": {
            "latitud": gps.latitud,
            "longitud": gps.longitud,
            "velocidadKmh": gps.velocidad_kmh,
            "receivedAt": gps.received_at.isoformat() + "Z"
        } if gps else None,
        "ultima_temperatura": {
            "temperaturaC": temp.temperatura_c,
            "alerta": temp.alerta,
            "receivedAt": temp.received_at.isoformat() + "Z"
        } if temp else None,
        "ultimo_combustible": {
            "nivelPct": fuel.nivel_pct,
            "alerta": fuel.alerta,
            "receivedAt": fuel.received_at.isoformat() + "Z"
        } if fuel else None,
        "historial_gps_count": count
    }

if __name__ == "__main__":
    uvicorn.run(app, host="0.0.0.0", port=8082)
