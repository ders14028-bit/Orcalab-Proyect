# OrcaLab

Monorepo base para OrcaLab, una plataforma colaborativa de investigación marina basada en microservicios Java + Spring Boot + Maven.

## Estructura

- `api-gateway/`: configuración declarativa de Kong.
- `auth-service/`: autenticación y autorización.
- `room-service/`: gestión de salas y presencia.
- `realtime-service/`: tiempo real con WebSocket y Redis Pub/Sub.
- `reporting-service/`: reportes y métricas históricas.
- `observability-service/`: exposición base de métricas agregadas.
- `docker-compose.yml`: orquestación local completa.

Cada servicio Spring Boot es independiente, sin pom padre compartido ni dependencias Maven cruzadas.

## Requisitos locales

- Java 17 o superior.
- Maven 3.9 o superior.
- Docker y Docker Compose.

## Levantar todo el entorno

```bash
docker compose up --build
```

## Servicios y puertos

- Kong proxy: `http://localhost:8000`
- Kong admin API: `http://localhost:8001`
- auth-service: `http://localhost:8081`
- room-service: `http://localhost:8082`
- realtime-service: `http://localhost:8083`
- reporting-service: `http://localhost:8084`
- observability-service: `http://localhost:8085`
- Prometheus: `http://localhost:9090`
- Grafana: `http://localhost:3000`
- Loki: `http://localhost:3100`

## Ejecutar un servicio por separado

Cada servicio se puede iniciar desde su carpeta con:

```bash
mvn spring-boot:run
```

## Nota sobre alta disponibilidad

El archivo `api-gateway/kong.yml` ya deja preparado el patrón de upstreams y targets para añadir varias réplicas por servicio más adelante. Para el escalado local, puedes ampliar los `targets` de cada upstream o introducir una capa de discovery externa.

