# orcalab-auth-service

Microservicio de autenticación y control de acceso basado en roles (RBAC) de OrcaLab.

## HU que cubre

| HU | Descripción |
|---|---|
| HU-01 | Registro de usuario |
| HU-02 | Login |
| HU-03 | Roles y RBAC |

## Atributo de calidad principal que soporta

**Seguridad** — KPI-04 (100% de peticiones filtradas correctamente por rol, cero tolerancia). Este servicio es quien **emite** el JWT que los demás microservicios (`room-service`, `realtime-service`, `reporting-service`) validan localmente sin necesidad de llamar de vuelta a este servicio en cada petición (decisión de arquitectura: reduce acoplamiento síncrono). Ver `orcalab-docs/atributos-calidad.md` para el escenario completo.

## Roles

`PUBLICO` (por defecto al registrarse) → `INVESTIGADOR` → `ORGANIZACION` → `ADMIN`. Solo `PUBLICO` ve ubicación aproximada de especies protegidas; los demás roles ven ubicación exacta.

## Endpoints

| Método | Ruta | Descripción | Auth requerida |
|---|---|---|---|
| POST | `/api/auth/register` | Registra un usuario nuevo (rol PUBLICO por defecto) | No |
| POST | `/api/auth/login` | Autentica y devuelve JWT | No |

### Ejemplo — registro

```bash
curl -X POST http://localhost:8081/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"investigador@orcalab.org","password":"contrasena123","fullName":"Ana Torres"}'
```

### Ejemplo — login

```bash
curl -X POST http://localhost:8081/api/auth/login \
  -H "Content-Type: application/json" \
  -d '{"email":"investigador@orcalab.org","password":"contrasena123"}'
```

Respuesta:
```json
{
  "token": "eyJhbGciOiJIUzI1NiJ9...",
  "email": "investigador@orcalab.org",
  "role": "PUBLICO",
  "expiresInMs": 3600000
}
```

## Cómo correr localmente

Requiere PostgreSQL corriendo (ver `orcalab-docs/docker-compose.yml` para levantarlo junto con todo el sistema, o standalone):

```bash
docker run --name postgres-auth -e POSTGRES_DB=auth_db \
  -e POSTGRES_USER=orcalab -e POSTGRES_PASSWORD=orcalab \
  -p 5432:5432 -d postgres:16

mvn spring-boot:run
```

El servicio queda en `http://localhost:8080` (o `8081` si lo corres vía el `docker-compose.yml` raíz de `orcalab-docs`, que remapea el puerto).

## Variables de entorno importantes

| Variable | Default (dev) | Nota |
|---|---|---|
| `JWT_SECRET` | valor dummy en `application.yml` | **Debe** compartirse con los demás microservicios que validan el token; en producción va por secret manager, nunca hardcoded |
| `SPRING_DATASOURCE_URL` | `jdbc:postgresql://localhost:5432/auth_db` | Sobrescrita por `docker-compose.yml` raíz cuando corre el sistema completo |

## Pendiente para dejar esto productivo (no bloqueante para la demo)

- Endpoint administrativo para promover un usuario de `PUBLICO` a `INVESTIGADOR`/`ORGANIZACION` (hoy no existe, todo usuario nuevo entra como PUBLICO).
- Publicar evento `UsuarioUnidoASala` u otros eventos relevantes a Redis (bus de eventos) cuando aplique — por ahora este servicio no publica eventos, solo emite JWT vía REST.
- Tests de integración con Testcontainers en vez de H2 (H2 sirve para arrancar rápido, pero no es 100% fiel a PostgreSQL en todos los casos).
