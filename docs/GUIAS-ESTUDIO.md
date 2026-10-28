# Guías individuales de estudio

> Las responsabilidades son **planificadas** (ver GITLAB.md). No hay todavía evidencia de aportes
> individuales en el repositorio; cada integrante debe dejarla en sus commits y MR.
> **Todos** deben poder explicar el proceso central, la arquitectura del C2 y las reglas R-01, R-04,
> R-06, R-07 y R-13, porque la cátedra puede preguntarle a cualquiera.

## Base común (leer todos)
1. README completo y `docs/diagramas/Secuencia-aprobar.puml`.
2. Recorrer en el IDE: `SolicitudController.crear` → `SolicitudService.crear` → `AnimalesClient.obtener` → `EvaluadorSolicitud` → `Solicitud.pendiente`.
3. Ejecutar `mvn clean install` y la colección de Postman al menos una vez.
4. Errores conceptuales a evitar: decir que el Gateway “aplica las reglas”; confundir 401 con 403; decir que el JAR “comparte entidades”; decir que Mercado Pago “se probó en vivo” si no se hizo.

---

## Integrante 1 — Dominio y evaluación de solicitudes
- **Responsabilidad:** proceso P2 (presentación) y su justificación de negocio.
- **Archivos:** `ms-adopciones/.../service/SolicitudService.java` (`crear`), `EvaluadorSolicitud.java`, `CalculadoraDistancia.java`, `model/Perfil.java`, `dto/PerfilRequest.java`, `controller/PerfilController.java`, `FlujoAdopcionIntegrationTest` (r01–r06), `EvaluadorSolicitudTest`.
- **Conceptos:** orden de evaluación, diferencia bloqueo (R-04) vs observación (R-05) vs marca (R-06), por qué R-04 queda RECHAZADA y no 409, privacidad de la dirección.
- **Reglas:** R-01..R-06. **Endpoints:** `PUT/GET /api/adoptantes/me/perfil`, `POST /api/solicitudes`.
- **Patrones:** DTO, Domain Model (fábricas `Solicitud.pendiente` y `rechazadaPorIncompatibilidad`).
- **Preguntas probables y respuestas:** 1–5 y 28–30 de PREGUNTAS.md. “¿Por qué Georef al final?” → porque si hay incompatibilidad dura no hace falta llamar a un externo.
- **Oral:** 00:00–03:00 y cierre en el bloque 6.

## Integrante 2 — Arquitectura, Animales, JAR común y persistencia
- **Archivos:** `pom.xml` raíz, `tpi-common/**`, `ms-animales/**` (`Animal`, `EstadoAnimal`, `AnimalService`, `AnimalInternoController`, `DatosDemo`), `infra/postgres/init/01-esquemas.sh`, diagramas.
- **Conceptos:** límites por dueño de la información, API interna, máquina de estados, `@Version`, `provided` en el JAR, `mvn install` y `.m2`, esquema por servicio.
- **Reglas:** R-13 y las transiciones de la tabla 6.3 del contrato. **Endpoints:** `/api/animales/**`, `/internal/animales/**`.
- **Patrones:** Repository, Optimistic Lock, Database per Service (variante), qué NO se comparte y por qué.
- **Preguntas:** 6–9, 13, 22, 26, 40, 44. “¿Por qué Animales no es un CRUD?” → rechaza activamente transiciones inválidas (7 casos en `AnimalTest`).
- **Oral:** 03:00–06:00 y patrones en el bloque 6.

## Integrante 3 — Decisiones, entrega, seguimiento, integraciones
- **Archivos:** `Solicitud.java` (aprobar…devolver, columnas de bloqueo), `SolicitudService` (`conCambioDeAnimal`, `confirmarEntrega`, `vencerEntrega`), `TareaVencimientos`, `AnimalesClient`, `GeorefClient`, `ms-donaciones/**` (`DonacionService`, `MercadoPagoClient`, `Donacion`).
- **Conceptos:** consistencia sin transacción distribuida (ADR-06), UNIQUE con NULL, plazos con `Clock`, Mercado Pago sin webhooks, `external_reference`, `verificado`.
- **Reglas:** R-07..R-12, D-01..D-04. **Endpoints:** acciones de `/api/solicitudes/{id}/...`, `/api/donaciones/**`.
- **Patrones:** Adapter, Aggregate, Template Method (`TransactionTemplate`).
- **Preguntas:** 10, 23–25, 27, 31, 32, 38. “¿Qué pasa si Animales confirma y se cae la base?” → caso residual documentado; se corrige manualmente; mejora: reconciliación.
- **Oral:** 06:00–09:00 y decisiones en el bloque 6.

## Integrante 4 — Seguridad
- **Archivos:** `infra/keycloak/realm-adopciones.json`, `gateway/.../security/*`, `tpi-common/.../security/*`, `*/config/SecurityConfig.java`, `ServiceTokenProvider`, `docker-compose.yml` (`KC_HOSTNAME`, `x-keycloak-validacion`), `GatewaySecurityTest`, `AnimalControllerSecurityTest`.
- **Conceptos:** OIDC vs OAuth2, JWT firmado (JWS), JWKS, claims `sub`/`iss`/`exp`/`realm_access`, `ROLE_`, Resource Server, client credentials, trusted zone, 401 vs 403, por qué cada servicio revalida.
- **Endpoints a explicar:** matriz del README §6.
- **Preguntas:** 14–21, 17 y 18 son las más probables.
- **Oral:** 09:00–12:00 y limitaciones en el bloque 6.

## Integrante 5 — Infraestructura, pruebas y demostración
- **Archivos:** Dockerfiles, `docker-compose*.yml`, `.env.example`, `.gitlab-ci.yml`, `scripts/prueba-humo.sh`, `postman/**`, `docs/DEFENSA.md`, `docs/AUDITORIA.md`.
- **Conceptos:** construcción en dos etapas, healthchecks y `depends_on`, volúmenes nombrados, override de desarrollo, qué se publica, CI, pruebas con H2 y dobles, plan alternativo.
- **Preguntas:** 37–43, 47. “¿Qué no está verificado?” → Docker Compose, Keycloak real, script de esquemas y Mercado Pago real (AUDITORIA §E.1).
- **Oral:** coordina 12:00–17:00 y abre el bloque 6 con pruebas y despliegue.
