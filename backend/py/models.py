from sqlalchemy import create_engine, Column, Integer, String, Float, DateTime, Boolean
from sqlalchemy.orm import declarative_base, sessionmaker
from datetime import datetime

Base = declarative_base()
engine = create_engine('sqlite:///fleet.db', connect_args={'check_same_thread': False})
SessionLocal = sessionmaker(bind=engine)

class GpsMessage(Base):
    __tablename__ = 'gps_messages'
    id = Column(Integer, primary_key=True, index=True)
    vehiculo_id = Column(String, index=True)
    latitud = Column(Float)
    longitud = Column(Float)
    velocidad_kmh = Column(Float)
    received_at = Column(DateTime, default=datetime.utcnow)

class TempAlert(Base):
    __tablename__ = 'temp_alerts'
    id = Column(Integer, primary_key=True, index=True)
    vehiculo_id = Column(String, index=True)
    temperatura_c = Column(Float)
    alerta = Column(Boolean)
    received_at = Column(DateTime, default=datetime.utcnow)

class FuelAlert(Base):
    __tablename__ = 'fuel_alerts'
    id = Column(Integer, primary_key=True, index=True)
    vehiculo_id = Column(String, index=True)
    nivel_pct = Column(Float)
    alerta = Column(Boolean)
    received_at = Column(DateTime, default=datetime.utcnow)

Base.metadata.create_all(bind=engine)
