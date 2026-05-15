# APE-006 — Mensajería Distribuida: Flota Logística 🚛

## Arquitectura del Sistema

```
Simulador Python → MQTT (Mosquitto:1883) → Bridge Python → RabbitMQ (5672) → Spring Boot → API REST
```

### Exchange y colas RabbitMQ

| Routing Key   | Cola destino         | Consumidor          |
|---------------|----------------------|---------------------|
| `gps.routing` | `cola.gps`           | `GpsConsumer.java`  |
| `temp.alert`  | `cola.temperatura`   | `AlertConsumer.java`|
| `temp.alert`  | `cola.notificaciones`| (binding doble)     |
| `fuel.routing`| `cola.combustible`   | (monitoreo)         |

---

## Estructura del proyecto

```
flota-logistica/
├── docker/
│   ├── docker-compose-mqtt.yml
│   ├── docker-compose-rabbitmq.yml
│   └── mosquitto.conf
├── mqtt-parte1/
│   ├── simulador.py    ← Genera telemetría de 3 vehículos
│   ├── suscriptor.py   ← Escucha flota/# y guarda en SQLite
│   └── bridge.py       ← Reenvía MQTT→RabbitMQ
├── fleet-monitor/      ← Proyecto Spring Boot
│   ├── pom.xml
│   └── src/main/java/edu/ape006/fleetmonitor/
│       ├── config/RabbitMQConfig.java
│       ├── model/GpsMessage.java
│       ├── repository/GpsMessageRepository.java
│       ├── consumer/GpsConsumer.java
│       ├── consumer/AlertConsumer.java
│       └── controller/FleetController.java
├── start.sh
└── stop.sh
```

---

## Orden de arranque

```bash
# 1. Instalar dependencias Python (una sola vez)
pip install paho-mqtt pika

# 2. Arranque completo (respeta el orden de la guía)
chmod +x start.sh stop.sh
./start.sh

# 3. Para detener todo
./stop.sh
```

### Arranque manual (paso a paso)

```bash
# Terminal 1 — RabbitMQ
docker compose -f docker/docker-compose-rabbitmq.yml up

# Terminal 2 — Mosquitto
docker compose -f docker/docker-compose-mqtt.yml up

# Terminal 3 — Spring Boot (esperar a que RabbitMQ esté listo)
cd fleet-monitor && mvn spring-boot:run

# Terminal 4 — Bridge
python3 mqtt-parte1/bridge.py

# Terminal 5 — Suscriptor
python3 mqtt-parte1/suscriptor.py

# Terminal 6 — Simulador
python3 mqtt-parte1/simulador.py
```

---

## Endpoints de la API REST

| Método | URL                              | Descripción                          |
|--------|----------------------------------|--------------------------------------|
| GET    | `/api/fleet/status`              | Estado general de la flota ⭐        |
| GET    | `/api/fleet/gps`                 | Todos los mensajes GPS               |
| GET    | `/api/fleet/gps/{vehiculoId}`    | GPS de un vehículo (ej: VH-001)     |
| GET    | `/api/fleet/health`              | Health-check del servicio            |
| GET    | `/h2-console`                    | Consola H2 (JDBC: `jdbc:h2:mem:fleetdb`) |

```bash
# Evidencia para la rúbrica
curl http://localhost:8080/api/fleet/status
```

Paneles de administración:
- **RabbitMQ**: http://localhost:15672 (admin / admin123)
- **H2 Console**: http://localhost:8080/h2-console

---

## Preguntas de Control

### P1 — Tipos de Exchange en RabbitMQ

| Tipo     | Mecanismo de enrutamiento                    | Uso en esta práctica         |
|----------|----------------------------------------------|------------------------------|
| **Direct** | Routing key exacta                          | ✅ Usado — cada sensor tiene su routing key exacta |
| **Fanout** | Broadcast a TODAS las colas ligadas         | Útil si todos los consumidores necesitan cada mensaje |
| **Topic**  | Wildcard: `*` (1 nivel), `#` (varios)       | Útil para enrutar por jerarquía de topics |
| **Headers**| Ignora routing key, filtra por cabeceras    | Para enrutamiento por metadatos del mensaje |

**¿Por qué Direct es ideal aquí?** Porque cada tipo de sensor (`gps`, `temperatura`, `combustible`) tiene una routing key fija y exclusiva — no se necesita flexibilidad de patrones.

---

### P2 — Importancia de las Colas Durables

`durable=true` hace que la **definición** de la cola persista tras un reinicio del broker RabbitMQ.

Si una cola NO es durable:
- Al reiniciar RabbitMQ, la cola **desaparece** junto con todos sus mensajes no consumidos.
- En una flota real, esto significaría perder **alertas de temperatura crítica** que podrían indicar daño a la carga refrigerada.
- Con `delivery_mode=2` (mensajes persistentes) + colas durables, los mensajes sobreviven al broker.

---

### P3 — Pub/Sub vs Point-to-Point

| Patrón          | Descripción                                      | En esta práctica                    |
|-----------------|--------------------------------------------------|-------------------------------------|
| **Point-to-Point** | Un mensaje → un único consumidor             | `cola.gps` → solo `GpsConsumer`    |
| **Pub/Sub**     | Un mensaje → múltiples consumidores simultáneos  | `temp.alert` → `cola.temperatura` + `cola.notificaciones` |

**¿Por qué Pub/Sub es mejor para la flota?**
Los datos GPS pueden consumirlos simultáneamente el servicio de rutas, el dashboard web y el sistema de auditoría, sin que el publicador (bridge) tenga que duplicar la publicación.

---

### P4 — Seguridad en MQTT y RabbitMQ

**MQTT (Mosquitto):**
- TLS en puerto **8883** (en lugar del 1883 no cifrado)
- Autenticación usuario/contraseña en `mosquitto.conf` (`password_file`)
- ACLs por topic en `acl_file` (ej: solo el simulador puede publicar en `flota/#`)

**RabbitMQ:**
- TLS en puerto **5671** (en lugar del 5672 no cifrado)
- **Virtual Hosts** para aislar entornos (producción vs. testing)
- Políticas de permisos por usuario a nivel de exchange y cola específica
- Deshabilitar usuario `guest` predeterminado en producción

---

## Conclusiones

1. **MQTT** es ideal para IoT y dispositivos con recursos limitados: protocolo ligero, pub/sub nativo, soporte de wildcards jerárquicos (`flota/#`).

2. **RabbitMQ** aporta robustez empresarial: enrutamiento flexible con exchanges, colas durables, mensajes persistentes y gestión de prioridades.

3. El patrón **Bridge** desacopla el mundo IoT del mundo de microservicios: el simulador no sabe nada de RabbitMQ, y Spring Boot no sabe nada de MQTT.

4. El uso de **colas durables + mensajes persistentes** garantiza que ninguna alerta crítica se pierda ante fallos del broker.

5. **Spring AMQP** simplifica enormemente la integración con RabbitMQ gracias a `@RabbitListener` y la configuración declarativa de exchanges/colas con beans de Spring.

---

## Repositorio

> 📌 Añadir enlace al repositorio Git aquí.

```bash
git init
git add .
git commit -m "APE-006: Sistema de mensajería distribuida — Flota Logística"
git remote add origin <URL_DEL_REPOSITORIO>
git push -u origin main
```
