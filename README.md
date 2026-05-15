# APE-05/06 — Mensajería Distribuida: Flota Logística (Versión Python) 🚛

## Arquitectura del Sistema (Migrada a Python)

El sistema ha sido migrado de Spring Boot a una arquitectura **Python de alto rendimiento** utilizando FastAPI, garantizando una interfaz interactiva y ligera.

```
Simulador (Python) → MQTT (Mosquitto:1883) → Bridge (Python) → RabbitMQ (5672) → FastAPI (Python) → Dashboard Moderno
```

### Componentes Clave

1.  **Backend (FastAPI)**: Servidor REST que centraliza la simulación, el puenteo de mensajes y la persistencia en SQLite.
2.  **Dashboard Moderno**: Interfaz web rediseñada con un tema claro, tipografía moderna (Outfit) y animaciones fluidas.
3.  **Persistence**: Los mensajes se filtran por RabbitMQ y se guardan en una base de datos local `fleet.db`.

---

## Estructura del Proyecto

```
Practica06_Distribuidos/
├── backend/
│   ├── py/
│   │   ├── main.py         ← FastAPI + Orquestación de threads
│   │   ├── models.py       ← Esquema de Base de Datos (SQLAlchemy)
│   │   ├── simulator.py    ← Generador de telemetría (MQTT)
│   │   ├── bridge.py       ← Puente MQTT → RabbitMQ
│   │   ├── consumers.py    ← Consumidores RabbitMQ → SQLite
│   │   └── requirements.txt
├── frontend/
│   ├── index.html
│   ├── style.css       ← Nuevo diseño Light Mode
│   └── app.js
├── backend/docker/      ← Infraestructura Mosquitto/RabbitMQ
├── start.sh            ← Arranque automatizado
├── stop.sh             ← Detención limpia
└── status.sh           ← Monitor interactivo (no negro)
```

---

## Cómo Ejecutar

```bash
# 1. Dar permisos de ejecución
chmod +x start.sh stop.sh status.sh

# 2. Iniciar todo el sistema
./start.sh

# 3. Monitorear el estado (Modo Interactivo)
./status.sh
```

### Accesos Rápidos

- **Dashboard**: [http://localhost:8081](http://localhost:8081)
- **Documentación API (Swagger)**: [http://localhost:8082/docs](http://localhost:8082/docs)
- **Panel RabbitMQ**: [http://localhost:15672](http://localhost:15672) (admin / admin123)

---

## Mejoras Implementadas

- **Interfaz No Negra**: Cambio total de estética a un "Light Mode" profesional con acentos en índigo.
- **Backend Interactivo**: Implementación de Swagger UI para probar los endpoints en tiempo real.
- **Unificación**: Eliminación de la dependencia de Java/Spring Boot para simplificar el despliegue en entornos Python.
- **Monitor en Tiempo Real**: El script `status.sh` ahora permite interactuar y ver el estado de los servicios con colores vibrantes.

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

3. El patrón **Bridge** desacopla el mundo IoT del mundo de microservicios: el simulador no sabe nada de RabbitMQ, y FastAPI no sabe nada de MQTT.

4. El uso de **colas durables + mensajes persistentes** garantiza que ninguna alerta crítica se pierda ante fallos del broker.

5. **FastAPI** simplifica la integración y ofrece una documentación de API automática, facilitando el desarrollo y la depuración del sistema de mensajería.

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
