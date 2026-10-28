# Bloque D — Organización del equipo en GitLab

> **Estado real del repositorio local (27/10/2026):** `git init` hecho, rama `master`, **sin commits**.
> No se accedió a GitLab. No existen todavía ramas, Merge Requests ni aportes registrados: todo lo
> que sigue es un **plan**. Las contribuciones individuales deben quedar en el historial real.

## D.1 Integrantes y responsabilidades planificadas

Los nombres los asigna el equipo (el documento de dominio menciona a Virginia, Roberta, Ramiro,
Lucas y Nicole; `CONTRATOS.md` ya asigna infraestructura a Virginia y el emisor del token a
Virginia y Roberta). Cada integrante tiene un área principal y **revisa** el área de otro.

| Integrante | Área principal | Revisa | Defiende en el oral |
|---|---|---|---|
| 1 | Dominio y ms-adopciones: presentación y evaluación (R-01..R-06), `EvaluadorSolicitud`, perfiles | 3 | 00:00–03:00 negocio |
| 2 | Arquitectura, ms-animales, `tpi-common`, diagramas C4, persistencia | 1 | 03:00–06:00 arquitectura |
| 3 | ms-adopciones decisiones/entrega/seguimiento (R-07..R-12), tarea programada, ms-donaciones y Mercado Pago, Georef | 2 | 06:00–09:00 reglas e integración |
| 4 | Seguridad: Keycloak (realm), Gateway (Security Filter), `SecurityConfig` de servicios, token de servicio | 5 | 09:00–12:00 seguridad |
| 5 | Docker/Compose, CI, Postman, prueba de humo, coordinación de la integración | 4 | 12:00–17:00 demo |

Nadie concentra toda la seguridad ni toda la integración: la seguridad de cada servicio la escribe
el dueño del servicio usando lo que define el Integrante 4; la integración Adopciones→Animales la
hacen 1 y 3 contra el contrato que mantiene 2.

## D.2 Ramas, commits y Merge Requests

- `main` protegida: solo entra por MR aprobado por **otra persona** y con el pipeline en verde.
- Ramas: `feature/<area>-<tema>` (ej. `feature/adopciones-evaluacion`), `fix/<tema>`, `test/<tema>`, `docs/<tema>`, `infra/<tema>`.
- Commits pequeños en imperativo: `adopciones: valida R-02 antes de consultar Animales`.
- MR con plantilla: qué cambia, qué regla/requisito cubre, cómo se probó, capturas de Postman si aplica.
- Cambios de contrato (`docs/CONTRATOS.md`) en un MR propio, revisado por los afectados **antes** del código que lo usa.
- Conflictos: el autor del MR hace `git rebase origin/main` y vuelve a correr `mvn clean install`.

## D.3 Entregas incrementales sugeridas para subir el código

Como el código ya existe, se propone incorporarlo en MR temáticos para que la historia refleje
quién revisa y explica cada parte (no para simular un desarrollo que no ocurrió así).

| # | Rama | Responsable | Contenido (archivos) | Depende de | Verificación para integrar |
|---|---|---|---|---|---|
| 1 | `infra/esqueleto` | 5 | POM raíz, `.gitignore`, `.gitattributes`, `.dockerignore`, `.env.example`, `.gitlab-ci.yml` | — | pipeline corre |
| 2 | `docs/contratos-v2` | 2 | `docs/CONTRATOS.md`, `docs/ANALISIS.md`, diagramas | 1 | revisión de los 5 |
| 3 | `feature/common` | 2 | `tpi-common/**` | 1 | 13 pruebas en verde |
| 4 | `feature/animales` | 2 | `ms-animales/**` | 3 | 32 pruebas |
| 5 | `feature/seguridad-keycloak` | 4 | `infra/keycloak/**`, `gateway/**` | 3 | 7 pruebas del Gateway |
| 6 | `feature/adopciones-evaluacion` | 1 | modelo, perfiles, `EvaluadorSolicitud`, `GeorefClient`, `SolicitudService.crear` y sus pruebas | 4 | pruebas R-01..R-06 |
| 7 | `feature/adopciones-proceso` | 3 | aprobar/entrega/control, `TareaVencimientos`, `AnimalesClient`, `ServiceTokenProvider`, flujo completo | 6 | 62 pruebas |
| 8 | `feature/donaciones` | 3 | `ms-donaciones/**` | 3 | 14 pruebas |
| 9 | `infra/compose` | 5 (con 4) | `docker-compose*.yml`, Dockerfiles, `infra/postgres/**`, `scripts/` | 4–8 | `docker compose up` + prueba de humo |
| 10 | `docs/defensa` | todos | `postman/**`, `docs/*` restantes, README | 9 | ensayo cronometrado |

## D.4 Tablero

| Tarea | Estado actual verificable |
|---|---|
| Código de los 5 módulos | Listo para integrar (compila, 128 pruebas en verde localmente) |
| Docker Compose | En revisión (no ejecutado: falta un entorno con Docker) |
| Keycloak real | En revisión (no ejecutado) |
| Mercado Pago real | Pendiente (credenciales de prueba) |
| Colección Postman | Listo para integrar (rutas verificadas por la prueba de humo) |
| Pipeline GitLab | Pendiente (el repositorio aún no está en GitLab) |
| Ensayo de la defensa | Pendiente |

Estados posibles: Pendiente → En desarrollo → En revisión → Listo para integrar → Integrado y verificado.

## D.5 Integración continua

`.gitlab-ci.yml`: etapa `compilar` (`mvn -DskipTests install`) y `pruebas` (`mvn clean install`
con reportes JUnit en el MR). Imagen `maven:3.9-eclipse-temurin-21`, caché del `.m2` por rama.
Criterio de MR: pipeline verde + 1 aprobación + documentación actualizada si cambia un contrato.

## D.6 Evidencias reales disponibles hoy

Ninguna en GitLab. Localmente: el código, los resultados de `mvn clean install` y de la prueba de
humo registrados en [AUDITORIA.md](AUDITORIA.md).
