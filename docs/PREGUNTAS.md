# Banco de preguntas y respuestas modelo

Nivel: 🟢 básica · 🟡 intermedia · 🔴 difícil. Las respuestas citan el código real; si algo no está
implementado, se dice.

## Dominio, alcance y MVP
1. 🟢 **¿Qué problema resuelve?** Ordena el ciclo de una adopción con reglas verificables y trazabilidad, y registra donaciones confirmadas por el proveedor de pagos. (README §1)
2. 🟢 **¿Cuál es el proceso central?** Perfil → solicitud evaluada → aprobación con reserva del animal → entrega → control final → animal ADOPTADO, con historial.
3. 🟡 **¿Por qué no es un CRUD?** Porque el resultado de presentar una solicitud depende de datos de otro servicio (estado y aptitudes), de una fuente externa (Georef) y de reglas (R-01..R-06); y las transiciones se validan en dos servicios con plazos (R-09, R-11).
4. 🟡 **¿Qué cambió respecto del compromiso?** R-04 devuelve 201 RECHAZADA (el contrato decía 409; el dominio dice que queda rechazada); no se implementó la extensión del plazo de entrega; se agregaron historial y perfil del postulante. Está en CONTRATOS §0 y AUDITORIA §E.4.
5. 🔴 **¿Cómo demuestran que cumplen el alcance?** Matriz requisito → clase → prueba en AUDITORIA §E.2–E.3, 128 pruebas en verde y la prueba de humo por HTTP. Lo no verificado (Docker, Keycloak real, Mercado Pago real) está marcado como tal.

## Arquitectura y microservicios
6. 🟢 **¿Por qué tres servicios?** Por dueño de la información: animal, proceso, donación (ADR-01).
7. 🟡 **¿Por qué Donaciones no colabora con nadie?** Las donaciones no tienen destino específico (decisión del dominio). La colaboración exigida la cumple la adopción; Donaciones se justifica por datos, reglas y proveedor propios.
8. 🟡 **¿Quién inicia la colaboración y qué necesita?** Adopciones; de Animales necesita estado y aptitudes, y que confirme transiciones (`AnimalesClient`).
9. 🔴 **¿Por qué Animales decide las transiciones si Adopciones ya validó?** Porque Animales es el dueño del estado; si mañana otro proceso lo cambia, la regla sigue protegida. `EstadoAnimal.admiteTransicionDelProceso` y `@Version`.
10. 🟡 **¿Qué pasa si Animales deja de responder?** Presentar o aprobar devuelve 503 y la solicitud no cambia (verificado deteniendo el proceso). La tarea programada reintenta en la próxima ejecución.
11. 🔴 **¿Por qué no mensajería asíncrona?** El usuario necesita la respuesta en el momento (R-01 es bloqueante) y agregaría infraestructura sin una necesidad del dominio (ADR-02).
12. 🟡 **¿Qué hace y qué NO hace el Gateway?** Enruta, autentica en el borde, rechaza tokens de servicio en `/api/**`, genera X-Correlation-ID y traduce caídas a 503. No decide reglas de negocio ni pertenencia.
13. 🟢 **¿Qué muestran C1 y C2?** C1: actores, sistema como caja negra y externos. C2: Gateway, tres servicios, JAR común (no desplegable), dos PostgreSQL y Keycloak, con relaciones de seguridad, colaboración y dependencia Maven.

## Seguridad
14. 🟢 **¿Autenticar es autorizar?** No. Autenticar: el JWT es válido (firma, emisor, vencimiento) → si no, 401. Autorizar: el rol o la pertenencia permiten la operación → si no, 403.
15. 🟡 **¿Cómo se valida un JWT?** El Resource Server baja las claves públicas (JWKS) de Keycloak, verifica la firma RS256, `iss` igual a `issuer-uri` y `exp`. Config en `application.yml` (`issuer-uri` + `jwk-set-uri`).
16. 🟡 **¿Cómo llegan los roles a Spring?** Keycloak los pone en `realm_access.roles`; `KeycloakRoleConverter` los convierte en `ROLE_<ROL>`; `hasRole("COORDINADOR")` los compara.
17. 🔴 **¿Alcanza con validar en el Gateway?** No: si alguien llega a un servicio sin pasar por el Gateway (red interna, puerto de desarrollo), el servicio vuelve a validar. Verificado: llamada directa sin token → 401.
18. 🔴 **¿Por qué no reenviar el token del usuario a Animales?** La API interna exige SERVICIO; con el token del usuario, un coordinador podría ordenar transiciones salteando el proceso. Adopciones usa client credentials.
19. 🟡 **¿Qué es el problema del emisor y cómo lo resolvieron?** El `iss` depende de la URL; con `KC_HOSTNAME=http://localhost:8180` todos los tokens tienen el mismo emisor y los servicios bajan las claves por `keycloak:8080` (ADR-05).
20. 🟡 **¿Dónde están los secretos?** Solo en `.env` (ignorado por git). El realm usa placeholders `${ADOPCIONES_SERVICE_CLIENT_SECRET}` y `${DEMO_USERS_PASSWORD}`. *Pendiente de verificar con Keycloak real que los placeholders se resuelven.*
21. 🟡 **¿Por qué password grant en Postman?** Simplificación de TPI declarada en el contrato; en producción se usaría Authorization Code + PKCE desde un frontend.

## Persistencia y consistencia
22. 🟢 **¿Cómo separan los datos?** Un esquema y un usuario dueño por servicio; Keycloak con su instancia (ADR-08).
23. 🔴 **¿Qué pasa si dos personas piden el mismo animal a la vez?** Las dos pueden pasar la verificación en memoria, pero la columna `animal_pendiente` con UNIQUE hace que la base rechace la segunda → 409 (`SolicitudRepositoryTest`).
24. 🔴 **¿Y si dos coordinadores aprueban a la vez?** El segundo encuentra la solicitud ya no PENDIENTE (o falla por `@Version`), o Animales rechaza RESERVADO→RESERVADO → 409.
25. 🔴 **¿Cómo mantienen la consistencia entre dos bases?** Cambio local + flush, luego Animales; si Animales falla, rollback. Caso residual documentado en ADR-06.
26. 🟡 **¿Por qué `ddl-auto: update` y no migraciones?** Decisión del contrato por simplicidad; la mejora es Flyway.
27. 🟡 **¿Las transacciones incluyen llamadas remotas?** La consulta inicial y Georef no (se hacen antes); la transición sí incluye la llamada a Animales para poder revertir el cambio local.

## Integraciones externas
28. 🟢 **¿Por qué Georef es necesario y no configuración?** El domicilio cambia con cada postulante; solo el refugio es fijo.
29. 🟡 **¿Qué devuelve Georef y qué usan?** `direcciones[0].ubicacion.lat/lon`; la distancia la calculamos con Haversine (`CalculadoraDistancia`).
30. 🟡 **¿Qué pasa si Georef no responde o no encuentra?** `distanciaNoVerificada=true`, la solicitud entra PENDIENTE, decide el Coordinador.
31. 🟡 **¿Cómo confirman una donación sin webhooks?** Al consultarla, buscan pagos por `external_reference` y si alguno está `approved` pasa a CONFIRMADA (D-02).
32. 🔴 **¿Probaron Mercado Pago real?** No en este entorno (sin credenciales). El contrato se probó con respuestas simuladas según la documentación oficial; falta la prueba con la cuenta vendedora de prueba.

## REST, validaciones y errores
33. 🟢 **¿Qué códigos usan?** 200, 201+Location, 400, 401, 403, 404, 409, 503 (504 en el Gateway).
34. 🟡 **¿Por qué 409 y no 400 para reglas?** 400 es una petición mal formada; 409 es una petición válida que el estado actual impide.
35. 🟡 **¿Cómo es el formato de error y dónde se arma?** `ErrorResponse` del JAR; `GlobalExceptionHandler` para errores del controlador y `JsonAuthenticationEntryPoint`/`JsonAccessDeniedHandler` para 401/403 de la cadena de filtros.
36. 🟡 **¿Un servicio devuelve los errores de otro?** No: `AnimalesClient` traduce 404/409/5xx a excepciones propias.

## Pruebas, Docker y GitLab
37. 🟢 **¿Qué prueban?** Reglas, seguridad, clientes HTTP, restricciones, flujo completo. 128 pruebas.
38. 🟡 **¿Cómo prueban plazos de 7 y 30 días?** Con un `Clock` inyectable que el test adelanta (`FlujoAdopcionIntegrationTest.RelojMovil`).
39. 🟡 **¿Las pruebas dependen de Internet?** No: `MockRestServiceServer` y H2.
40. 🟡 **¿Cómo se usa el JAR común en Docker?** El Dockerfile instala el POM padre y `tpi-common` en el `.m2` de la imagen y compila el servicio contra esa dependencia.
41. 🟡 **¿Qué se publica al exterior?** En la base, solo Gateway (8080) y Keycloak (8180). El override publica servicios y Postgres para desarrollo.
42. 🔴 **¿Cómo saben que Keycloak está listo antes del Gateway?** Healthcheck de Keycloak en `/health/ready` (puerto 9000) y `depends_on: condition: service_healthy`.
43. 🟡 **¿Qué evidencia de trabajo en GitLab tienen?** Todavía ninguna: el repositorio local no tiene commits. El plan está en GITLAB.md.

## Patrones y mejoras
44. 🟡 **¿Qué patrones usan?** API Gateway, Adapter, Repository, DTO, modelo de dominio con máquina de estados, bloqueo optimista, correlación (PATRONES.md).
45. 🔴 **¿Por qué no Circuit Breaker?** Una sola dependencia síncrona crítica con timeouts de 3 s: el circuito daría el mismo 503 sin mejorar la regla.
46. 🟡 **¿Qué mejorarían con más tiempo?** Flyway, reconciliación de inconsistencias residuales, webhooks de Mercado Pago, distancia por ruta, Testcontainers con PostgreSQL y Keycloak reales, Authorization Code + PKCE.
47. 🔴 **¿Qué parte no está verificada?** Despliegue con Docker Compose, Keycloak real (import y placeholders), script de esquemas y Mercado Pago real.
