# Bloque A — Análisis, alcance y decisiones

## A.1 Fuentes y prioridad aplicada

| Prioridad | Fuente | Uso |
|---|---|---|
| 1 | Enunciado preliminar TPI 2026 (05/10/2026) | Requisitos técnicos obligatorios (RC-xx) |
| 2 | “TPI 2026 — Alcance, compromiso y criterios de evaluación” y Rúbrica v0.11 | Cómo se evalúa: proceso central, reglas, colaboración, evidencia |
| 3 | Documento de dominio del equipo (v2) y `CONTRATOS.md` | Alcance comprometido (procesos P1–P5, reglas R-01..R-13, D-01..D-04) y contratos |
| 4 | Esqueleto existente (pom multimódulo, Dockerfiles, `tpi-common` inicial, pings) | Se conservaron convenciones: paquetes, puertos, nombres de módulos y de variables |
| 5 | Decisiones de diseño propias | Ver [ADR.md](ADR.md) |

Las guías de Google Distance Matrix y OSRM se leyeron como alternativas de integración; el
documento de dominio ya las descarta (clave paga y despliegue local, respectivamente) y se mantiene esa decisión.

## A.2 Requisitos obligatorios del enunciado (criterios comunes)

| ID | Requisito | Componente | Evidencia |
|---|---|---|---|
| RC-01 | Diagramas C1 y C2 (C4) que representen lo implementado | docs | `docs/diagramas/*.puml` |
| RC-02 | Arquitectura de microservicios con API Gateway como entrada | gateway | `gateway/`, `docker-compose.yml` |
| RC-03 | ≥ 2 microservicios funcionales Java/Spring Boot con responsabilidades coherentes (no 2 CRUD) | ms-animales, ms-adopciones, ms-donaciones | máquinas de estado y reglas |
| RC-04 | Proceso de negocio con colaboración real entre servicios y manejo de fallas | ms-adopciones → ms-animales | `SolicitudService`, `AnimalesClient` |
| RC-05 | Gateway enruta y tiene Security Filter | gateway | `SecurityConfig` (WebFlux), rutas en `application.yml` |
| RC-06 | API pública externa con información necesaria para una regla | ms-adopciones (Georef), ms-donaciones (Mercado Pago) | `GeorefClient`, `MercadoPagoClient` |
| RC-07 | Keycloak + OIDC + JWT + Spring Security + roles + integración con el Gateway + trusted zone | todos | `realm-adopciones.json`, `SecurityConfig` de cada módulo |
| RC-08 | Componente común JAR Maven independiente usado por los ≥ 2 servicios principales vía `.m2` | tpi-common | `pom.xml` de los servicios, Dockerfiles |
| RC-09 | DBMS relacional y estrategia de separación explicada | PostgreSQL | `01-esquemas.sh`, `default_schema` |
| RC-10 | APIs REST: métodos, códigos, JSON, validación, errores, OpenAPI fiel | servicios | controladores, `GlobalExceptionHandler`, springdoc |
| RC-11 | Contenerización y Docker Compose reproducible del sistema completo | infra | Dockerfiles, `docker-compose*.yml` |

## A.3 Compromisos específicos del equipo

| ID | Compromiso | Fuente |
|---|---|---|
| CE-P1 | Gestión de animales y disponibilidad con máquina de estados | Dominio §5 P1, R-13 |
| CE-P2 | Presentación y evaluación (disponibilidad, duplicados, compatibilidad, distancia) y decisión humana | Dominio P2, R-01..R-08 |
| CE-P3 | Entrega con plazo | P3, R-09, R-10 |
| CE-P4 | Adaptación, control final, devolución, reversión | P4, R-11, R-12 |
| CE-P5 | Donaciones con Mercado Pago, sin webhooks | P5, D-01..D-04 |
| CE-T1 | Tarea programada para plazos vencidos | Dominio §10.2 |
| CE-S1 | Doble validación del JWT (Gateway y servicios), API interna no publicada, token de servicio | Dominio §10.8 |
| CE-D1 | PostgreSQL con esquema y credenciales por servicio; Keycloak con base propia | Dominio §10.7 |
| CE-O1 | Correlación `X-Correlation-ID` y logs por servicio | CONTRATOS §5 |

La matriz completa con estado y evidencia está en [AUDITORIA.md](AUDITORIA.md).

## A.4 Problema, MVP y proceso central

**Necesidad:** que cada animal encuentre un hogar adecuado aplicando reglas consistentes y dejando
traza, y saber qué donaciones se acreditaron.

**MVP:** P1–P5 completos según el dominio, con un proceso central demostrable de punta a punta:

1. *Condición inicial:* animal DISPONIBLE (ms-animales), adoptante con perfil (ms-adopciones).
2. *Presentación:* ms-adopciones verifica R-03, R-02; consulta a ms-animales (R-01, aptitudes);
   evalúa R-04 (bloquea) y R-05 (observa); consulta Georef y calcula la distancia (R-06).
   Resultado: PENDIENTE con marcas, o RECHAZADA por incompatibilidad.
3. *Decisión:* el Coordinador aprueba (R-07/R-08) → ms-animales pasa a RESERVADO; o rechaza con motivo.
4. *Entrega:* dentro del plazo (R-09) → ambos EN_ADAPTACION y se fija la fecha de control (R-10);
   si vence, la tarea programada cancela y libera al animal.
5. *Seguimiento:* control CONFORME → FINALIZADA + ADOPTADO (R-11); DESFAVORABLE o devolución → REVERTIDA + DISPONIBLE (R-12).
6. *Trazabilidad:* cada transición queda en `evento_solicitud` con fecha, actor (`sub`) y motivo.

Por qué no es un CRUD: el resultado depende de reglas que combinan datos de dos servicios y de una
fuente externa, de transiciones de estado validadas en dos lugares y de plazos.

## A.5 Reglas de negocio implementadas (condición, momento, responsable, falla)

| Regla | Condición | Momento | Servicio / clase | Si no se cumple | Demostración |
|---|---|---|---|---|---|
| R-01 | Animal DISPONIBLE y sin otra PENDIENTE | Presentar | ms-adopciones `SolicitudService.crear` + UNIQUE | 409 | Postman 3: adoptante2 pide el mismo animal |
| R-02 | Una activa por adoptante | Presentar | ídem + UNIQUE `adoptante_activo` | 409 | Postman 3: segunda solicitud de adoptante1 |
| R-03 | Perfil completo | Presentar | `SolicitudService.crear` | 409 | prueba `r03*` |
| R-04 | No apto con niños/otros animales vs hogar | Presentar | `EvaluadorSolicitud` | 201 RECHAZADA con motivo | Postman 4 |
| R-05 | Necesita exterior y no hay patio | Presentar | `EvaluadorSolicitud` | Observación | Postman 3 (Toby + departamento) |
| R-06 | Distancia ≤ 100 km (Georef) | Presentar | `EvaluadorSolicitud`, `GeorefClient` | `requiereExcepcion` / `distanciaNoVerificada` | `distanciaKm` en la respuesta |
| R-07 | Aprobar solo PENDIENTE; animal reservable | Aprobar | `Solicitud.aprobar` + ms-animales | 409 / 503 sin aprobar | Postman 3 |
| R-08 | Motivo en rechazo; justificación si requiere excepción | Aprobar/rechazar | `Solicitud`, DTO | 400 / 409 | Postman 4 |
| R-09 | Entrega dentro de 7 días | Confirmar / tarea | `Solicitud`, `TareaVencimientos` | CANCELADA + animal DISPONIBLE | prueba `r09*` |
| R-10 | Fecha de control = entrega + 30 días | Confirmar | `Solicitud.confirmarEntrega` | 409 si no APROBADA | `fechaControl` |
| R-11 | Control desde la fecha de control | Control final | `Solicitud.registrarControl` | 409 | prueba `r11*` |
| R-12 | Desfavorable o devolución | Control / devolución | `Solicitud` + ms-animales | REVERTIDA, animal DISPONIBLE | prueba `r12*` |
| R-13 | Sin NO_DISPONIBLE con adopción en curso | Marcar | ms-animales `Animal.marcarNoDisponible` | 409 | Postman 3 |
| D-01 | Monto ≥ mínimo | Crear donación | `DonacionService` | 400 | Postman 5 |
| D-02 | CONFIRMADA solo con pago `approved` | Consultar | `DonacionService.obtener` | sigue PENDIENTE | prueba de integración |
| D-03 | CONFIRMADA es final; un pago, una donación | Consultar | `Donacion.confirmar`, UNIQUE `pago_id` | se ignora / PENDIENTE | prueba `d03*` |
| D-04 | Cada donante ve lo suyo | Consultar | `DonacionService`, `SecurityConfig` | 403 | Postman 5 |

## A.6 Contradicciones detectadas y cómo se resolvieron

| # | Contradicción | Resolución |
|---|---|---|
| 1 | R-04: CONTRATOS §7 dice 409; el dominio dice “la solicitud queda RECHAZADA con motivo” | Se sigue el dominio: 201 con `estado: RECHAZADA` (traza). Registrado en CONTRATOS §0 |
| 2 | CONTRATOS fija Boot 3.5.0; existen parches 3.5.x | Se usa 3.5.16 (la propia regla del contrato lo permite) |
| 3 | La guía de la cátedra usa springdoc 2.5.0 y Lombok | springdoc 2.8.17 (compatible con Boot 3.5). Sin Lombok: records para DTO y getters explícitos (sin procesador de anotaciones, compila con cualquier JDK ≥ 21). Ver ADR-12 |
| 4 | El dominio dice que la tarea programada vigila R-09 **y R-11** | No hay estado de “control vencido” definido. Se implementa R-09; R-11 vencido queda como consulta. Documentado como limitación |
| 5 | R-09 “el Coordinador podría extender el plazo una vez (a validar)” | No implementado (no validado) |
| 6 | Enunciado: “Swagger documentado”; CONTRATOS: Swagger sin token solo en desarrollo | Swagger está permitido a nivel servicio, que no se publica en modo producción; además `SWAGGER_ENABLED` lo apaga |
| 7 | Entorno: el JDK local es 27 y no hay *daemon* de Docker | Se compila con `--release 21`; las imágenes usan Temurin 21. Docker Compose no pudo ejecutarse aquí (ver AUDITORÍA) |

## A.7 Arquitectura, tecnologías y alternativas

Ver README §3–4 y [ADR.md](ADR.md). Alternativas descartadas principales: un único servicio
(no cumple RC-03), Animales como CRUD sin reglas (responsabilidad débil), comunicación asíncrona con
mensajería (agrega infraestructura sin necesidad: el proceso requiere respuesta inmediata),
reenviar el token del usuario a Animales (permitiría a un usuario ordenar transiciones),
OSRM/Google Distance Matrix (despliegue local / clave paga).
