# Bloque E — Auditoría de cumplimiento y evidencias

Fecha: 27/10/2026. Estados: **CV** cumplido y verificado · **INV** implementado, no verificado ·
**P** parcial · **PEND** pendiente · **NV** no verificable con los recursos disponibles · **NA** no aplica.

## E.1 Qué se verificó y cómo (honestidad de la evidencia)

| Verificación | Resultado | Cómo |
|---|---|---|
| Compilación del reactor desde cero (`mvn clean install`) | **Éxito** | JDK 27 local con `--release 21`, Maven 3.9.16 |
| Pruebas automatizadas | **128 ejecutadas, 0 fallos, 0 errores** (tpi-common 13, ms-animales 32, ms-adopciones 62, ms-donaciones 14, gateway 7) | misma ejecución; las pruebas corren en JDK 27, no en 21 |
| Construcción “como el Dockerfile” (`-N install`, `tpi-common install`, `-f servicio package`) | **Éxito**; el JAR del servicio contiene `BOOT-INF/lib/tpi-common-1.0.0-SNAPSHOT.jar` | comandos del Dockerfile ejecutados en el host |
| Ejecución real de los 4 procesos y flujo E2E por HTTP | **Prueba de humo superada (19 verificaciones)** | Gateway, ms-animales, ms-adopciones, ms-donaciones como procesos locales, con **H2** en lugar de PostgreSQL y un **emisor JWT de prueba** que imita los endpoints JWKS y token de Keycloak |
| Georef real | **Respondió**: Av. Colón 1000, Córdoba → 2,7 km del refugio | llamada en vivo durante la prueba de humo y con `curl` |
| Token con emisor distinto / firma alterada | 401 en Gateway y en servicio directo | prueba manual con el emisor de prueba |
| Animales caído durante una aprobación | 503 y la solicitud **sigue PENDIENTE**; el Gateway responde 503 en `/api/animales` | se detuvo el proceso de ms-animales |
| `X-Correlation-ID` | el mismo id (`humo-28466`) aparece en ms-adopciones (“Solicitud 1 -> APROBADA”) y en ms-animales (“Animal 6: DISPONIBLE -> RESERVADO”) | `grep` de los logs |
| Docker Compose completo (PostgreSQL, Keycloak real, imágenes) | **NV** | la máquina no tiene *daemon* de Docker ni el plugin Compose |
| Keycloak real (import del realm, placeholders `${...}`, `KC_HOSTNAME`, password grant) | **NV** | ídem; configuración basada en la documentación oficial |
| Script `01-esquemas.sh` en PostgreSQL | **NV** | ídem |
| Mercado Pago real | **NV** | no hay `MP_ACCESS_TOKEN` en el entorno (sin él, `POST /api/donaciones` respondió 503 como está diseñado) |

**Comandos para completar las verificaciones pendientes** (en una máquina con Docker):
```bash
cp .env.example .env
```
```bash
docker compose up --build -d
```
```bash
docker compose ps
```
```bash
DEMO_USERS_PASSWORD=<valor del .env> ./scripts/prueba-humo.sh
```
```bash
docker compose exec postgres psql -U animales_user -d tpi -c "select count(*) from adopciones.solicitud"
```
El último comando debe fallar con *permission denied* (aislamiento entre esquemas).

## E.2 Matriz de requisitos obligatorios (enunciado)

| ID | Requisito | Criterio rúbrica | Evidencia en código | Prueba | Estado | Acción |
|---|---|---|---|---|---|---|
| RC-01 | C1 y C2 que reflejen lo implementado | ARQ-01 | `docs/diagramas/C1-Contexto.puml`, `C2-Contenedores.puml` | revisión contra `docker-compose.yml` | CV (estático) | renderizar a PNG para la defensa |
| RC-02 | Microservicios + API Gateway como entrada | ARQ-01 | `gateway/`, rutas en `application.yml` | `GatewaySecurityTest`, prueba de humo | CV | — |
| RC-03 | ≥ 2 servicios con responsabilidades coherentes | ARQ-01 | `Animal`/`EstadoAnimal`, `Solicitud`, `Donacion` | `AnimalTest`, `SolicitudTest` | CV | — |
| RC-04 | Proceso con colaboración real y manejo de fallas | INT-01 | `SolicitudService`, `AnimalesClient` | `FlujoAdopcionIntegrationTest`, `AnimalesClientTest`, prueba de humo, caída de Animales | CV | — |
| RC-05 | Gateway con Security Filter | SEG-01 | `gateway/.../SecurityConfig.java` | `GatewaySecurityTest` | CV | — |
| RC-06 | API pública externa necesaria | INT-01 | `GeorefClient` (R-06), `MercadoPagoClient` (D-02) | `GeorefClientTest`, `MercadoPagoClientTest`; Georef en vivo | CV (Georef) / INV (MP real) | probar MP con credenciales de prueba |
| RC-07 | Keycloak, OIDC, JWT, Spring Security, roles, trusted zone | SEG-01 | `realm-adopciones.json`, `KeycloakRoleConverter`, `SecurityConfig` x4, `ServiceTokenProvider` | pruebas de seguridad en los 4 módulos; emisor de prueba | P (Keycloak real NV) | levantar con Docker y correr la colección |
| RC-08 | JAR Maven común usado por ≥ 2 servicios vía `.m2` | INF-01 | `tpi-common`, POM de los 3 servicios, Dockerfiles | build y contenido del JAR | CV | — |
| RC-09 | DBMS relacional y estrategia | INF-01 | `01-esquemas.sh`, `default_schema`, compose | repositorios con H2 | INV (PostgreSQL NV) | verificar con Docker |
| RC-10 | REST, códigos, validación, errores, OpenAPI | TEC-01 | controladores, `GlobalExceptionHandler`, `OpenApiConfig` | pruebas MockMvc | CV | revisar Swagger UI en vivo |
| RC-11 | Docker Compose reproducible | INF-01 | Dockerfiles, `docker-compose*.yml` | — | NV | ejecutar `docker compose up --build` |

## E.3 Compromisos específicos

| ID | Compromiso | Evidencia | Prueba | Estado |
|---|---|---|---|---|
| CE-P1 | Gestión de animales, R-13 | `Animal`, `AnimalController` | `AnimalTest`, `AnimalControllerSecurityTest`, humo | CV |
| CE-P2 | R-01..R-08 | `SolicitudService.crear/aprobar/rechazar`, `EvaluadorSolicitud` | `FlujoAdopcionIntegrationTest`, `EvaluadorSolicitudTest`, humo | CV |
| CE-P3 | R-09, R-10 | `Solicitud.confirmarEntrega`, `TareaVencimientos` | `r09*`, `SolicitudTest` | CV |
| CE-P4 | R-11, R-12 | `Solicitud.registrarControl`, `devolver` | `r11*`, `r12*`, humo | CV |
| CE-P5 | D-01..D-04 | `DonacionService`, `MercadoPagoClient` | `FlujoDonacionIntegrationTest` | CV con proveedor simulado; NV con MP real |
| CE-T1 | Tarea programada | `TareaVencimientos` (cada 5 min) | `r09LaTareaProgramada...` | CV (R-09); R-11 no automatizado (documentado) |
| CE-S1 | Doble validación, API interna, token de servicio | ver RC-07 | pruebas y verificación directa | CV con emisor de prueba |
| CE-D1 | Esquema y usuario por servicio; Keycloak con base propia | compose, script | — | INV |
| CE-O1 | Correlación y logs | filtros e interceptor | `CorrelationIdFilterTest`, `GatewaySecurityTest`, logs | CV |
| Extra | Historial de la solicitud | `EventoSolicitud`, `GET .../historial` | flujo completo | CV |
| Extra | Datos de demostración | `DatosDemo` | `AnimalesApplicationTest` | CV |

## E.4 Diferencias entre lo comprometido y lo implementado

1. R-04 devuelve 201 RECHAZADA (el contrato decía 409). Fundamento: el documento de dominio.
2. La extensión única del plazo de entrega (R-09, “a validar”) no se implementó.
3. La tarea programada no actúa sobre controles vencidos (R-11): no hay un estado definido para eso.
4. Endpoints adicionales: historial y perfil del postulante para el Coordinador.

Estos cambios deben comunicarse al equipo docente (criterio “evolución del alcance”).

## E.5 Pendientes y acciones

| Pendiente | Responsable sugerido | Acción |
|---|---|---|
| Levantar el sistema con Docker y correr la prueba de humo y la colección | Integrante 5 + Integrante 4 | `docker compose up --build -d` y `./scripts/prueba-humo.sh` |
| Verificar import del realm (placeholders) y el `iss` | Integrante 4 | decodificar un token (test de Postman “Token …”) |
| Probar Mercado Pago con la cuenta vendedora de prueba | Integrante 3 | `MP_ACCESS_TOKEN` en `.env`, pagar con la compradora de prueba (titular APRO) |
| Verificar aislamiento de esquemas | Integrante 2 | `psql` cruzado debe fallar |
| Renderizar C1/C2 | Integrante 2 | PlantUML |
| Ejecutar las pruebas con JDK 21 (CI de GitLab) | Integrante 1 | primer pipeline |
| Confirmar con la cátedra que Georef + Mercado Pago cuentan como integraciones | todos | consulta |
