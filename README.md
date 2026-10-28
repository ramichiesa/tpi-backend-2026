# TPI Backend 2026 — Adopción responsable de animales

Backend de microservicios (Java 21, Spring Boot 3.5) que ordena el ciclo completo de una adopción
—desde que un animal está disponible hasta que la adopción se confirma tras un período de
adaptación— y registra las donaciones a la cuenta común de la organización.

> Documentación complementaria en [`docs/`](docs): análisis y alcance ([ANALISIS.md](docs/ANALISIS.md)),
> contratos ([CONTRATOS.md](docs/CONTRATOS.md)), API ([API.md](docs/API.md)), decisiones
> ([ADR.md](docs/ADR.md)), patrones ([PATRONES.md](docs/PATRONES.md)), auditoría
> ([AUDITORIA.md](docs/AUDITORIA.md)), trabajo en GitLab ([GITLAB.md](docs/GITLAB.md)),
> defensa ([DEFENSA.md](docs/DEFENSA.md)), preguntas ([PREGUNTAS.md](docs/PREGUNTAS.md)) y
> guías individuales ([GUIAS-ESTUDIO.md](docs/GUIAS-ESTUDIO.md)).

## 1. Problema que resuelve

Un refugio coordina adopciones por mensajería y planillas: no hay una visión única de qué animales
están disponibles, varias personas piden el mismo animal, se aprueban adopciones sin verificar la
compatibilidad del hogar ni la distancia, nadie controla si la adopción funcionó y las donaciones no
quedan registradas. Este backend aplica reglas explícitas en cada paso y deja trazabilidad de cada
decisión.

**Actores:** Adoptante (también donante), Coordinador (personal del refugio). **Sistemas externos:**
Keycloak (identidad), API Georef (coordenadas del domicilio) y Mercado Pago (cobro de donaciones).

## 2. Alcance del MVP

| Proceso | Qué hace | Reglas |
|---|---|---|
| P1 Gestión de animales | Alta, modificación, disponibilidad y estado del animal | R-13 |
| P2 Presentación y evaluación | El adoptante solicita; el sistema verifica y evalúa; el Coordinador decide | R-01 a R-08 |
| P3 Entrega | Confirmación o cancelación de la entrega dentro del plazo | R-09, R-10 |
| P4 Adaptación y seguimiento | Control final (conforme o desfavorable) o devolución | R-11, R-12 |
| P5 Donaciones | Cobro con Mercado Pago y confirmación consultando el pago | D-01 a D-04 |

**Proceso central (el que se demuestra de punta a punta):** perfil → solicitud evaluada
(Animales + Georef) → aprobación (Animales reserva) → entrega → control final → animal ADOPTADO,
con historial de cada decisión.

## 3. Arquitectura

```
Postman ──► Keycloak :8180 (OIDC, emite JWT con realm_access.roles)
   │
   └─JWT─► API Gateway :8080 (Security Filter + rutas + X-Correlation-ID)
               ├─► ms-animales   :8081  catálogo y máquina de estados del animal
               ├─► ms-adopciones :8082  proceso de adopción ──► ms-animales /internal (token SERVICIO)
               │                                          └──► API Georef (HTTPS)
               └─► ms-donaciones :8083  donaciones        ──► Mercado Pago (HTTPS)
   PostgreSQL de negocio (esquemas animales | adopciones | donaciones, un usuario por esquema)
   PostgreSQL de Keycloak (instancia propia)
   tpi-common (JAR Maven): errores, excepciones, conversor de roles, correlación — usado por los 3 servicios
```

Diagramas C4 (PlantUML): [C1](docs/diagramas/C1-Contexto.puml), [C2](docs/diagramas/C2-Contenedores.puml)
y la [secuencia de aprobación](docs/diagramas/Secuencia-aprobar.puml).

| Módulo | Responsabilidad | Puerto |
|---|---|---|
| `tpi-common` | JAR reutilizable: `ErrorResponse`, excepciones, `GlobalExceptionHandler`, `KeycloakRoleConverter`, 401/403 en JSON, `ResourceServerDefaults`, filtro e interceptor de correlación. No se despliega | — |
| `ms-animales` | Dueño del animal y su estado; rechaza transiciones inválidas (R-13). API pública y API interna | 8081 |
| `ms-adopciones` | Dueño del proceso: perfiles, solicitudes, evaluación (Georef), entrega, control, historial y tarea de vencimientos | 8082 |
| `ms-donaciones` | Donaciones y su confirmación con Mercado Pago | 8083 |
| `gateway` | Única entrada, enrutamiento, validación del JWT, errores uniformes | 8080 |

## 4. Tecnologías y versiones

| Tecnología | Versión | Nota |
|---|---|---|
| Java | 21 (bytecode `--release 21`) | Las imágenes usan Temurin 21 |
| Spring Boot | 3.5.16 | Parche de la serie 3.5 que fija el contrato |
| Spring Cloud | 2025.0.3 | Gateway: `spring-cloud-starter-gateway-server-webflux` |
| springdoc-openapi | 2.8.17 | Línea compatible con Boot 3.5 |
| PostgreSQL | 17 (alpine) | Negocio y Keycloak en instancias separadas |
| Keycloak | 26.8.1 | Realm importado desde `infra/keycloak/realm-adopciones.json` |
| H2 | gestionada por Boot | Solo en pruebas automatizadas |

## 5. Reglas de negocio y dónde están

| Regla | Dónde se implementa | Prueba |
|---|---|---|
| R-01 animal disponible y sin otra PENDIENTE | `SolicitudService.crear`, columnas UNIQUE en `Solicitud` | `FlujoAdopcionIntegrationTest.r01*`, `SolicitudRepositoryTest` |
| R-02 una solicitud activa por adoptante | `SolicitudService.crear`, columna `adoptante_activo` | `r02*`, `SolicitudRepositoryTest` |
| R-03 perfil completo | `SolicitudService.crear`, `PerfilRequest` | `r03SinPerfilNoSePuedeSolicitar` |
| R-04 incompatibilidad dura → RECHAZADA | `EvaluadorSolicitud.incompatibilidadDura` | `EvaluadorSolicitudTest`, `r04*` |
| R-05 exterior sin patio → observación | `EvaluadorSolicitud.observacionesDeCompatibilidad` | `EvaluadorSolicitudTest.r05*` |
| R-06 distancia con Georef | `EvaluadorSolicitud.evaluarDistancia`, `GeorefClient`, `CalculadoraDistancia` | `EvaluadorSolicitudTest.r06*`, `GeorefClientTest` |
| R-07 aprobar solo PENDIENTE y animal reservable | `Solicitud.aprobar` + `AnimalesClient.cambiarEstado(RESERVADO)` | `r07*`, `siAnimalesNoResponde*` |
| R-08 motivo y justificación | `Solicitud.verificarAprobable`, `SolicitudRequests.Motivo` | `SolicitudTest.r08*`, `r06YR08*` |
| R-09 plazo de entrega | `Solicitud.plazoEntregaVencido`, `TareaVencimientos` | `r09*` |
| R-10 entrega y fecha de control | `Solicitud.confirmarEntrega` | `SolicitudTest.r10*` |
| R-11 control desde la fecha | `Solicitud.registrarControl` | `SolicitudTest.r11*`, flujo completo |
| R-12 desfavorable o devolución → REVERTIDA | `Solicitud.registrarControl`, `Solicitud.devolver` | `r12*` |
| R-13 sin NO_DISPONIBLE con adopción en curso | `Animal.marcarNoDisponible` | `AnimalTest.r13*` |
| D-01 a D-04 | `DonacionService`, `Donacion`, `SecurityConfig` de donaciones | `FlujoDonacionIntegrationTest` |

## 6. Seguridad

- **Autenticación:** el usuario obtiene su JWT de Keycloak (cliente público `tpi-client`). El backend nunca ve contraseñas.
- **Gateway (Security Filter):** valida firma, emisor y vencimiento; solo deja pasar a `/api/**` tokens con rol ADOPTANTE o COORDINADOR; `/internal/**` no tiene ruta.
- **Cada servicio vuelve a validar el JWT** y aplica la matriz de permisos por endpoint; la pertenencia (“esta solicitud es tuya”) la verifica el servicio (403).
- **Llamadas internas:** ms-adopciones usa un token de servicio (client credentials, cliente `adopciones-service`, rol SERVICIO) para la API interna de Animales.
- **Emisor del token:** `KC_HOSTNAME=http://localhost:8180` fija `iss` para todos los tokens; los servicios validan ese emisor y descargan las claves por `http://keycloak:8080/.../certs` (sin *discovery* al arrancar).

| Quién | Puede |
|---|---|
| Sin token | `/actuator/health` (y Swagger directo en cada servicio cuando `SWAGGER_ENABLED=true`) |
| ADOPTANTE | su perfil; crear, ver, cancelar sus solicitudes; pedir devolución; ver animales; donar y ver sus donaciones |
| COORDINADOR | administrar animales; ver y decidir solicitudes; perfiles de postulantes; entrega; control final; todas las donaciones y el total |
| SERVICIO | solo `/internal/animales/**` |

## 7. Persistencia

Una instancia PostgreSQL de negocio con un esquema por servicio y un usuario dueño de cada esquema
(`infra/postgres/init/01-esquemas.sh`): ningún servicio puede leer las tablas de otro. Keycloak usa su
propia instancia (`keycloak-db`). Tablas creadas por JPA (`ddl-auto: update`). Volúmenes nombrados:
`tpi-postgres-data`, `tpi-keycloak-db-data` (los datos sobreviven a `docker compose down`).

## 8. Ejecución

### Requisitos
JDK 21 (o superior), Maven 3.9+, Docker con el plugin Compose v2 e Internet (Georef, Mercado Pago,
descarga de imágenes y dependencias).

### Pasos
```bash
cp .env.example .env
```
Editar `.env`: contraseñas, `ADOPCIONES_SERVICE_CLIENT_SECRET`, `DEMO_USERS_PASSWORD` y, para donar,
`MP_ACCESS_TOKEN` de la **cuenta vendedora de prueba**. Para la demostración conviene
`PERIODO_ADAPTACION_DIAS=0`.

```bash
mvn clean install
```
Compila todo, ejecuta las pruebas e instala `tpi-common` en `~/.m2` (mecanismo mínimo del enunciado).

```bash
docker compose up --build -d
```
Levanta las 7 piezas. Los Dockerfiles compilan desde la raíz del repositorio: no necesitan JARs del host.

```bash
docker compose ps
```
Esperar a que todos figuren `healthy` (Keycloak tarda 30–90 s).

```bash
./scripts/prueba-humo.sh
```
Con `DEMO_USERS_PASSWORD` exportada, recorre el proceso central por el Gateway.

Luego importar en Postman `postman/TPI-Adopciones.postman_collection.json` y
`postman/TPI-local.postman_environment.json` (completar `demoPassword`).

### Operación
```bash
docker compose down
```
Detiene y conserva los datos.

```bash
docker compose down -v
```
Borra también los volúmenes (base limpia; Keycloak reimporta el realm).

```bash
docker compose -f docker-compose.yml up --build -d
```
Modo “producción”: solo publica el Gateway (8080) y Keycloak (8180).

```bash
docker compose logs -f ms-adopciones
```
Logs con `[servicio] [X-Correlation-ID]` en cada línea.

### Sin Docker (desarrollo)
Con PostgreSQL en `localhost:5433` (el contenedor `postgres` con el override) y Keycloak en
`localhost:8180`, cada servicio arranca con `mvn -pl <modulo> spring-boot:run`; los valores por defecto
de `application.yml` apuntan a esas direcciones.

### Documentación OpenAPI
`http://localhost:8081/swagger-ui.html`, `:8082` y `:8083` (puertos publicados por el override).

## 9. Pruebas

```bash
mvn clean install
```
| Módulo | Qué verifica |
|---|---|
| tpi-common | conversor de roles, manejador global de errores, filtro de correlación |
| ms-animales | máquina de estados y R-13, repositorio (H2), matriz de permisos, arranque con datos demo |
| ms-adopciones | agregado `Solicitud` (R-07 a R-12), evaluador (R-04 a R-06), Haversine, clientes HTTP de Animales, Georef y Keycloak con servidor simulado, restricciones UNIQUE, **flujo completo** por HTTP con seguridad, fallas de Animales y Georef, vencimientos con reloj controlado |
| ms-donaciones | cliente de Mercado Pago (contrato y fallas), flujo D-01 a D-04 |
| gateway | 401/403, API interna no publicada, servicio caído → 503, correlación, 404 |

Las pruebas no requieren Docker, Keycloak ni Internet (H2 y dobles de prueba). El resultado de la
última ejecución está registrado en [AUDITORIA.md](docs/AUDITORIA.md).

## 10. Errores frecuentes

| Síntoma | Causa y solución |
|---|---|
| Todo responde 401 con un token recién emitido | El `iss` del token no coincide. Decodificar el token: debe ser `http://localhost:8180/realms/adopciones`. Pedir el token a `localhost:8180`, no a otro host |
| `POST /api/solicitudes` responde 503 | ms-animales caído o el secreto del cliente `adopciones-service` no coincide con el realm (ver logs de ms-adopciones: “Animales rechazó el token de servicio”) |
| Keycloak no queda `healthy` | Primera descarga lenta o `keycloak-db` sin iniciar; `docker compose logs keycloak` |
| Cambié el realm JSON y no se aplica | `--import-realm` no pisa un realm existente: `docker compose down -v` |
| `POST /api/donaciones` responde 503 | `MP_ACCESS_TOKEN` vacío o inválido (log: “Revisar MP_ACCESS_TOKEN”) |
| El script de init de PostgreSQL no corre | Solo corre con el volumen vacío; en Windows, el archivo debe tener fin de línea LF (`.gitattributes` lo fuerza) |
| `distanciaNoVerificada: true` | Georef no respondió o no encontró la dirección: comportamiento esperado (R-06) |

## 11. Limitaciones conocidas

- La distancia es en línea recta (Haversine), no por ruta.
- Sin webhooks de Mercado Pago: una donación se actualiza al consultarla.
- No hay transacción distribuida: si Animales confirma un cambio y luego falla el *commit* local (caso
  residual), queda una inconsistencia que debe corregirse a mano (ver ADR-06).
- `start-dev` de Keycloak (HTTP, sin TLS) y cliente con *password grant*: simplificaciones de TPI.
- La tarea programada cubre R-09; el control final vencido (R-11) se consulta filtrando
  `EN_ADAPTACION` por `fechaControl`, sin automatismo.
- El despliegue completo con Docker Compose no pudo ejecutarse en la máquina donde se generó el
  proyecto (sin *daemon* de Docker). Ver [AUDITORIA.md](docs/AUDITORIA.md).
