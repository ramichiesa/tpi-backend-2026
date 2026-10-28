# Registro de decisiones arquitectónicas (ADR)

Formato: contexto → alternativas → decisión → consecuencias → evidencia. Estado de todas: **aceptada**
(sujeta a revisión del equipo en un Merge Request).

---

## ADR-01. Tres servicios separados por dueño de la información

- **Contexto:** RC-03 exige ≥ 2 servicios con responsabilidades coherentes, no dos CRUD.
- **Alternativas:** (a) un monolito modular; (b) Animales + Adopciones (Donaciones dentro de Adopciones); (c) tres servicios; (d) un servicio por entidad (perfil, solicitud…).
- **Decisión:** (c). Animales es dueño del estado del animal; Adopciones, del proceso; Donaciones, del ciclo de pago.
- **Justificación:** dueños de datos distintos (el animal existe sin solicitudes), ritmos de cambio distintos, reglas distintas (Animales protege la coherencia del estado; Adopciones, la del proceso). Donaciones tiene datos, reglas y proveedor propios: cambiar de plataforma de pagos no toca a los otros. (d) multiplicaría llamadas sin reglas nuevas.
- **Consecuencias:** Donaciones no colabora con otro servicio (punto a defender: la colaboración exigida la cubre la adopción). Animales es chico pero tiene una máquina de estados que rechaza transiciones.
- **Evidencia:** `ms-animales/.../model/Animal.java`, `EstadoAnimal.java`; `ms-adopciones/.../service/SolicitudService.java`; `ms-donaciones/.../service/DonacionService.java`.

## ADR-02. Comunicación síncrona REST con API interna

- **Contexto:** al presentar y al decidir, Adopciones necesita el estado actual del animal y que Animales confirme la transición antes de responder al usuario.
- **Alternativas:** mensajería asíncrona (RabbitMQ/Kafka), réplica local del catálogo, REST síncrono.
- **Decisión:** REST síncrono con `RestClient` (lo que indica la cátedra para Spring MVC), timeouts de 3 s, a una API interna `/internal/animales/**` que el Gateway no enruta.
- **Consecuencias:** si Animales no responde, presentar y aprobar fallan con 503 (R-01 es bloqueante: “ante la duda no se avanza”). No se agrega infraestructura de mensajería. Acoplamiento temporal asumido.
- **Evidencia:** `AnimalesClient`, `ClientesConfig`, `AnimalInternoController`, `gateway/application.yml` (sin ruta interna), prueba `siAnimalesNoRespondeAlAprobarLaSolicitudSiguePendiente`.

## ADR-03. Animales decide la validez de cada transición

- **Contexto:** dos servicios podrían querer cambiar el estado del animal (Coordinador por la API pública y el proceso por la interna).
- **Decisión:** el estado solo cambia por métodos de la entidad `Animal`; las transiciones del proceso están tabuladas en `EstadoAnimal`. Adopciones *pide*; Animales *decide* (409 si es inválida). `@Version` evita que dos operaciones concurrentes se pisen.
- **Evidencia:** `EstadoAnimal.admiteTransicionDelProceso`, `Animal.cambiarEstadoPorProceso`, `AnimalTest` (5 válidas, 7 inválidas, R-13).

## ADR-04. Seguridad en dos capas y token de servicio (trusted zone)

- **Contexto:** RC-07; el enunciado pide justificar la trusted zone.
- **Alternativas:** validar solo en el Gateway; reenviar el token del usuario a Animales; mTLS entre servicios.
- **Decisión:** el Gateway valida el JWT (autenticación en el borde y rechazo de tokens de servicio en `/api/**`); **cada servicio vuelve a validarlo** y aplica la autorización fina por rol y por pertenencia. Adopciones obtiene su propio token (client credentials, rol SERVICIO) para la API interna. Los servicios no publican puertos en modo producción.
- **Justificación:** si alguien alcanzara un servicio sin pasar por el Gateway, igual necesita un token válido y el rol correcto (verificado: llamada directa sin token → 401; usuario a `/internal` → 403). Reenviar el token del usuario haría que un COORDINADOR pudiera ordenar transiciones del proceso directamente.
- **Consecuencias:** doble verificación de firma (costo bajo: claves en caché). mTLS descartado por complejidad.
- **Evidencia:** `gateway/.../SecurityConfig.java`, `*/config/SecurityConfig.java`, `ServiceTokenProvider`, `realm-adopciones.json`.

## ADR-05. Emisor del token fijo con KC_HOSTNAME

- **Contexto:** el `iss` depende de la URL usada para pedir el token (CONTRATOS §3.4).
- **Decisión:** `KC_HOSTNAME=http://localhost:8180`; servicios con `issuer-uri` público + `jwk-set-uri` interno.
- **Alternativas:** validar sin emisor (inseguro); poner `keycloak` en el archivo hosts de cada máquina (paso manual oculto).
- **Evidencia:** `docker-compose.yml` (`x-keycloak-validacion`), `application.yml` de cada módulo, verificación local: token con `iss=http://keycloak:8080/...` → 401.

## ADR-06. Consistencia entre servicios sin transacción distribuida

- **Contexto:** aprobar cambia dos bases (solicitud y animal).
- **Alternativas:** 2PC/XA (no disponible en REST), saga con eventos y compensaciones, orden local-primero con *rollback*.
- **Decisión:** dentro de una transacción local se aplica el cambio de la solicitud y se hace `flush` (aparecen conflictos de versión o UNIQUE **antes** de tocar Animales); luego se llama a Animales; si falla, la excepción revierte el cambio local. Las llamadas a Georef y la consulta inicial a Animales se hacen fuera de la transacción.
- **Consecuencias:** nunca queda una solicitud APROBADA con el animal sin reservar. Caso residual: Animales confirma y el *commit* local falla (por ejemplo, caída de la base en ese instante); quedaría el animal RESERVADO sin solicitud APROBADA. Se registra en logs y se corrige manualmente; una mejora sería una tarea de reconciliación.
- **Evidencia:** `SolicitudService.conCambioDeAnimal`, pruebas `r07SiAnimalesRechazaLaReservaNoSeAprueba` y `siAnimalesNoRespondeAlAprobar...`.

## ADR-07. Concurrencia: columnas de bloqueo con UNIQUE + bloqueo optimista

- **Contexto:** R-01 y R-02 ante dos peticiones simultáneas (riesgo señalado en el dominio §13).
- **Alternativas:** índice parcial de PostgreSQL (`WHERE estado='PENDIENTE'`), bloqueo pesimista, verificación solo en memoria.
- **Decisión:** columnas `animal_pendiente` y `adoptante_activo` que la entidad completa según el estado (null si no aplica) con restricción UNIQUE; más `@Version` en `Solicitud`, `Animal` y `Donacion`.
- **Justificación:** funciona con `ddl-auto` (contrato) y con H2 en las pruebas; PostgreSQL admite múltiples NULL en UNIQUE.
- **Evidencia:** `Solicitud` (Javadoc y `cambiarEstado`), `SolicitudRepositoryTest`.

## ADR-08. Persistencia: una instancia, esquema y usuario por servicio; Keycloak aparte

- **Alternativas:** una base por servicio (más contenedores), esquemas compartidos con un solo usuario (aislamiento débil).
- **Decisión:** base `tpi` con esquemas `animales`, `adopciones`, `donaciones`, cada uno propiedad de su usuario; Keycloak en `keycloak-db`.
- **Consecuencias:** aislamiento real por permisos con un solo contenedor de negocio; Keycloak se recrea sin tocar datos de negocio. `ddl-auto: update` simplifica pero no versiona el esquema (mejora: Flyway).
- **Evidencia:** `infra/postgres/init/01-esquemas.sh`, `hibernate.default_schema`.

## ADR-09. Componente común: qué se comparte y qué no

- **Se comparte** (igual en los tres servicios): `ErrorResponse`, excepciones, `GlobalExceptionHandler`, 401/403 en JSON, `KeycloakRoleConverter`, `ResourceServerDefaults`, `Roles`, correlación.
- **No se comparte:** entidades, DTO, reglas y rutas de seguridad de cada servicio; los estados del animal que usa Adopciones son una copia local (`adopciones.client.EstadoAnimal`): son contrato HTTP, no modelo compartido.
- **Distribución:** `mvn install` al `.m2`; dependencias Spring en `provided`. Los Dockerfiles repiten ese mecanismo dentro de la imagen.
- **Gateway:** no depende del JAR (reactivo); repite 15 líneas del conversor de roles a propósito.

## ADR-10. Georef como fuente de ubicación y distancia en línea recta

- **Alternativas:** OSRM (requiere desplegar y procesar el mapa; dudoso como “API pública por Internet”), Google Distance Matrix (clave y costos).
- **Decisión:** Georef `/direcciones` + Haversine propio; falla externa → `distanciaNoVerificada` (no bloquea: la decisión es humana).
- **Evidencia:** `GeorefClient` (formato verificado contra la API real), `CalculadoraDistancia`, `EvaluadorSolicitud`.

## ADR-11. Mercado Pago sin webhooks

- **Decisión:** Checkout Pro (preferencia + `init_point`); la confirmación se obtiene consultando `/v1/payments/search` por `external_reference` (UUID propio) al consultar la donación.
- **Consecuencias:** sin endpoint público para notificaciones (no hay HTTPS público en el TPI); una donación puede quedar PENDIENTE hasta que alguien la consulte.
- **Evidencia:** `MercadoPagoClient`, `DonacionService.obtener`.

## ADR-12. Sin Lombok; records para DTO

- **Contexto:** la cátedra usa Lombok. El procesador de anotaciones depende de la versión del compilador (el equipo tiene JDK distintos).
- **Decisión:** records para DTO y configuración; entidades con getters explícitos y métodos de dominio en lugar de setters.
- **Consecuencia:** algo más de código, pero las entidades no exponen setters que permitan saltear las reglas.

## ADR-13. Pruebas sin infraestructura externa

- **Decisión:** H2 en modo PostgreSQL, `MockRestServiceServer` con respuestas grabadas de Georef y documentadas de Mercado Pago, `@MockitoBean` para clientes en las pruebas de flujo, reloj inyectable (`Clock`) para plazos, `jwt()`/`mockJwt()` de Spring Security Test.
- **Consecuencia:** CI reproducible sin Docker ni Internet; no prueban el dialecto real de PostgreSQL ni Keycloak (lo cubre la prueba de humo con Docker).

## ADR-14. Docker: construcción en dos etapas desde la raíz

- **Decisión:** cada Dockerfile instala POM padre y `tpi-common` en el `.m2` de la imagen y compila el servicio; imagen final `eclipse-temurin:21-jre`. Healthchecks sin `curl` (bash `/dev/tcp`); `depends_on` con `service_healthy`; logging con rotación.
