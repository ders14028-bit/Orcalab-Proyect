# OrcaLab Monorepo

Monorepo base de OrcaLab con microservicios Spring Boot 3.x, Java 17 y Maven independiente por servicio.

## Servicios

- `auth-service`: autenticación y RBAC.
- `api-gateway`: puerta de entrada con rutas y validación local de JWT.
- `room-service`: salas y presencia.
- `realtime-service`: WebSocket, STOMP y eventos en tiempo real.
- `reporting-service`: reportes, KPIs y vistas de solo lectura.
- `observability-service`: salud y métricas operativas.

## Infraestructura local

- PostgreSQL para `auth-service`, `room-service` y `reporting-service`.
- MongoDB para `realtime-service`.
- Redis para eventos y presencia.
- Prometheus, Grafana y Loki para observabilidad.

## Arranque local

1. Copia `.env.example` a `.env` y ajusta los secretos locales.
2. Levanta todo con `docker compose up --build`.
3. Abre `http://localhost:8080` para el gateway.

## Estructura

- Cada servicio tiene su propio `pom.xml`, `Dockerfile`, `README.md` y `src/`.
- El root actual conserva el `auth-service` existente y añade el resto del ecosistema como carpetas hermanas.
