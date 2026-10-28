# Contratos del backend — TPI Backend 2026

**Estado:** propuesta para aprobación del equipo. Una vez aprobado, **nadie cambia un contrato por su cuenta**: se propone el cambio en un Merge Request que revisen las personas afectadas.

Lo marcado como **(a verificar)** no fue confirmado en documentación oficial y debe comprobarse antes de implementarlo.

## 0. Versión 2 — cambios incorporados durante la implementación

Estos cambios deben revisarse en un Merge Request con las personas afectadas y, si alteran el
compromiso de alcance, comunicarse al equipo docente.

| # | Cambio | Motivo | Sección |
|---|---|---|---|
| 1 | Spring Boot 3.5.0 → **3.5.16**, Spring Cloud **2025.0.3**, springdoc **2.8.17** | Parches de la misma serie (lo permite §1); springdoc 2.5.0 no corresponde a Boot 3.5 | 1, 14 |
| 2 | **R-04 responde 201 con la solicitud RECHAZADA** (no 409) | Contradicción entre §7 (409) y el documento de dominio (“la solicitud queda RECHAZADA con motivo”). Prevalece el dominio: así queda traza del rechazo automático | 7 |
| 3 | Emisor del token **resuelto**: `KC_HOSTNAME=http://localhost:8180`; servicios con `issuer-uri` (público) + `jwk-set-uri` (`keycloak:8080`) | Ver §3.4 | 3.4 |
| 4 | Endpoints agregados: `GET /api/solicitudes/{id}/historial` y `GET /api/adoptantes/{adoptanteId}/perfil` (COORDINADOR) | Trazabilidad de decisiones (R-08) y evaluación del hogar por el Coordinador | 7 |
| 5 | `SolicitudResponse` agrega `animalNombre`, `observacionesControl`, `motivoCierre` | Trazabilidad | 7 |
| 6 | R-01/R-02 en base: columnas de bloqueo `animal_pendiente` y `adoptante_activo` con UNIQUE (no índice parcial) | Funciona con `ddl-auto` y con H2 en las pruebas | 10 |
| 7 | `GET /api/animales` lo pueden usar ADOPTANTE y COORDINADOR (no el token de servicio) | El rol SERVICIO solo usa la API interna | 3.5 |
| 8 | Tarea programada: solo R-09 (vencimiento de entrega). La extensión del plazo “una vez” no se implementa | Pendiente §15.4 y riesgo del dominio | 15 |
| 9 | Variables nuevas: `DEMO_USERS_PASSWORD`, `MP_USAR_SANDBOX_INIT_POINT`, `ANIMALES_DATOS_DEMO`, `SWAGGER_ENABLED` | Realm sin secretos versionados; demo reproducible | 11 |
| 10 | Control final desfavorable exige observaciones (400 si faltan) | Trazabilidad del motivo de la reversión | 7 |

---

## 1. Convenciones generales

| Aspecto | Contrato |
|---|---|
| Estructura | Un único repositorio con Maven multimódulo (reactor) |
| `groupId` | `utnfc.isi.back.tpi` |
| Módulos | `tpi-common`, `ms-animales`, `ms-adopciones`, `ms-donaciones`, `gateway` (más la carpeta `infra/`) |
| Paquetes base | `utnfc.isi.back.tpi.common`, `.animales`, `.adopciones`, `.donaciones`, `.gateway` |
| Java y Spring | Java 21. Spring Boot 3.5.0 (la de la guía de la cátedra) con Spring Cloud 2025.0.0, que según el anuncio oficial de Spring es compatible con Boot 3.5.0 (se puede subir a un parche posterior de la misma serie). **Ojo:** desde esa versión de Spring Cloud el starter del Gateway se llama `spring-cloud-starter-gateway-server-webflux` y las propiedades empiezan con `spring.cloud.gateway.server.webflux`; los apuntes usan los nombres anteriores |
| Capas dentro de cada servicio | `controller`, `service`, `repository`, `model`, `dto`, `exception`, `client` (llamadas a otros sistemas) |
| Formato de datos | JSON. Fechas en formato ISO 8601. Los nombres de campos van en `camelCase` |
| Identificador de usuario | El claim `sub` del JWT. Es el dueño de perfiles, solicitudes y donaciones |
| Enumeraciones | En mayúsculas con guion bajo, igual que en el documento de dominio |

El módulo `gateway` es reactivo (WebFlux) y **no depende de `tpi-common`**, que es para los tres servicios.

---

## 2. Servicios, puertos y red

| Servicio (nombre en Compose) | Puerto interno | Publicado al host | Notas |
|---|---|---|---|
| `gateway` | 8080 | 8080 siempre | Única entrada a la API |
| `ms-animales` | 8081 | Solo en desarrollo | |
| `ms-adopciones` | 8082 | Solo en desarrollo | |
| `ms-donaciones` | 8083 | Solo en desarrollo | |
| `keycloak` | 8080 | 8180 siempre | Hace falta desde afuera para obtener tokens |
| `postgres` (negocio) | 5432 | 5433 solo en desarrollo | |
| `keycloak-db` | 5432 | No se publica | |

- **Red:** una sola red llamada `tpi-net`. Los servicios se llaman por su nombre (por ejemplo `http://ms-animales:8081`).
- **Compose:** `docker-compose.yml` (solo Gateway y Keycloak publicados) más `docker-compose.override.yml` (puertos de desarrollo), igual que en el apunte 22.
- Los puertos distintos por servicio permiten correrlos también fuera de Docker.

### Rutas del Gateway (sin reescritura de rutas)

| Ruta pública | Destino |
|---|---|
| `/api/animales/**` | `ms-animales` |
| `/api/adoptantes/**` y `/api/solicitudes/**` | `ms-adopciones` |
| `/api/donaciones/**` | `ms-donaciones` |
| `/internal/**` | **No se enruta** (API interna) |

---

## 3. Seguridad

### 3.1 Keycloak

| Elemento | Valor |
|---|---|
| Realm | `adopciones` |
| Roles de realm | `ADOPTANTE`, `COORDINADOR`, `SERVICIO` |
| Cliente para usuarios | `tpi-client`, público (sin secreto), con acceso directo por usuario y contraseña habilitado para poder pedir tokens desde Postman. Es una simplificación válida para el TPI y no una práctica de producción |
| Cliente de servicio | `adopciones-service`, confidencial, con cuenta de servicio y el rol `SERVICIO` asignado |
| Usuarios de prueba | `adoptante1`, `adoptante2` (rol ADOPTANTE) y `coordinador1` (rol COORDINADOR) |
| Reproducibilidad | El realm se **exporta a un archivo y se importa al arrancar Keycloak**. No debe quedar ninguna configuración manual |

### 3.2 JWT

| Claim | Uso |
|---|---|
| `sub` | Identifica al dueño del recurso |
| `realm_access.roles` | Roles. El conversor del JAR los transforma en `ROLE_<ROL>` |
| `iss` | Emisor que se valida |

Tokens de `adoptante1` o `coordinador1`: para usuarios. Token de `adopciones-service`: para llamadas internas.

### 3.3 Dónde se valida

1. El **Gateway** valida el JWT en su Security Filter y devuelve 401 si falta o es inválido.
2. **Cada servicio vuelve a validarlo** (no confía en que el Gateway lo haya hecho).
3. La **API interna** (`/internal/**` de Animales) exige el rol `SERVICIO` y el Gateway no la publica.
4. Los servicios no están publicados al host en producción, solo en desarrollo.

### 3.4 Punto técnico conocido: el emisor del token

El emisor (`iss`) que Keycloak escribe en el token depende de la URL con la que se lo consultó. Postman lo consulta como `localhost:8180`, pero los servicios dentro de Docker lo ven como `keycloak:8080`. Si no se alinea, **todos los tokens serán rechazados**. Es una de las primeras cosas que se prueban el día 1 (decodificar un token y comparar su `iss`). Responsables: Virginia y Roberta.

**Solución implementada (v2):**
1. Keycloak arranca con `KC_HOSTNAME=http://localhost:8180`: todos los tokens llevan `iss=http://localhost:8180/realms/adopciones`, aunque se pidan por `keycloak:8080` (como hace ms-adopciones).
2. Gateway y servicios configuran `issuer-uri` = URL pública (se valida contra `iss`) y `jwk-set-uri` = `http://keycloak:8080/.../certs` (se bajan las claves por la red interna). Con ambas propiedades Spring Boot no hace *discovery* al arrancar.
3. Verificación: el test de Postman “Token …” decodifica el token y compara `iss`. Un token con otro emisor se rechaza con 401 (verificado localmente con un emisor de prueba).

### 3.5 Acceso por endpoint

| Quién | Qué puede hacer |
|---|---|
| Sin token | Solo `/actuator/health` y la documentación Swagger en desarrollo |
| `ADOPTANTE` | Su perfil, crear y consultar sus solicitudes, cancelar una pendiente, pedir devolución, ver animales, crear y consultar sus donaciones |
| `COORDINADOR` | Administrar animales, ver y decidir todas las solicitudes, entrega, control final, ver todas las donaciones y el total |
| `SERVICIO` | Solo la API interna de Animales |

---

## 4. Errores y códigos HTTP

Formato único, definido en `tpi-common`:

```json
{
  "timestamp": "2026-10-26T10:15:30",
  "status": 409,
  "error": "Conflicto",
  "message": "El animal 7 no está disponible",
  "path": "/api/solicitudes",
  "errores": []
}
```

`errores` solo se completa en un 400 de validación (lista de campo y motivo).

| Código | Cuándo |
|---|---|
| 200 / 201 / 204 | Éxito. 201 incluye el encabezado `Location` |
| 400 | Datos inválidos |
| 401 | Falta el token o es inválido |
| 403 | Rol insuficiente o recurso ajeno |
| 404 | Recurso inexistente |
| 409 | Regla de negocio o estado que impide la operación |
| 503 | Otro servicio o el proveedor externo no responde |

Los servicios usan el manejador global del JAR (`@RestControllerAdvice`). Un servicio **no** devuelve la excepción original de otro: la traduce a este formato.

---

## 5. Trazabilidad y logs

- Encabezado `X-Correlation-ID`. Lo genera el Gateway si no viene, y cada servicio lo lee, lo pone en el contexto de logs y lo reenvía en sus llamadas.
- Los servicios usan un filtro de servlet; el Gateway necesita un filtro propio por ser reactivo.
- Los logs van a la salida estándar (no a archivos) con el nombre del servicio y el identificador de correlación.
- **Nunca** se registran tokens, contraseñas, credenciales de Mercado Pago ni direcciones completas.
- Cada servicio expone `/actuator/health` (se usa en los healthchecks de Compose). No se incorporan ELK, Loki ni Grafana.

---

## 6. API de Animales (`ms-animales`)

### 6.1 Pública (por el Gateway)

| Método y ruta | Rol | Cuerpo | Respuesta |
|---|---|---|---|
| `POST /api/animales` | COORDINADOR | `AnimalRequest` | 201 `AnimalResponse` |
| `GET /api/animales?estado=&especie=` | Cualquier autenticado | — | 200 lista de `AnimalResponse` |
| `GET /api/animales/{id}` | Cualquier autenticado | — | 200 `AnimalResponse` / 404 |
| `PUT /api/animales/{id}` | COORDINADOR | `AnimalRequest` | 200 `AnimalResponse` |
| `POST /api/animales/{id}/marcar-no-disponible` | COORDINADOR | `{ "motivo": "..." }` | 200 / 409 si está RESERVADO o EN_ADAPTACION (R-13) |
| `POST /api/animales/{id}/marcar-disponible` | COORDINADOR | — | 200 / 409 |

### 6.2 Interna (solo `SERVICIO`, no publicada)

| Método y ruta | Cuerpo | Respuesta |
|---|---|---|
| `GET /internal/animales/{id}` | — | 200 `AnimalResponse` (estado y aptitudes) / 404 |
| `PUT /internal/animales/{id}/estado` | `{ "nuevoEstado": "RESERVADO" }` | 200 `AnimalResponse` / 404 / 409 si la transición no es válida |

### 6.3 Transiciones que Animales acepta por la API interna

| Desde | Hacia | La pide Adopciones cuando |
|---|---|---|
| DISPONIBLE | RESERVADO | Se aprueba una solicitud |
| RESERVADO | EN_ADAPTACION | Se confirma la entrega |
| RESERVADO | DISPONIBLE | Se cancela la entrega o vence el plazo |
| EN_ADAPTACION | ADOPTADO | El control final es conforme |
| EN_ADAPTACION | DISPONIBLE | Control desfavorable o devolución |

Cualquier otra transición devuelve 409. Si el Coordinador quiere dejar al animal NO_DISPONIBLE tras una reversión, lo hace **después**, con el endpoint público (así se respeta R-13).

### 6.4 DTO

`AnimalRequest` / `AnimalResponse`:

| Campo | Tipo | Validación |
|---|---|---|
| `id` (solo en respuesta) | número | — |
| `nombre` | texto | Obligatorio |
| `especie` | `PERRO`, `GATO`, `OTRO` | Obligatorio |
| `edadMeses` | entero | Mayor o igual a 0 |
| `tamanio` | `PEQUENIO`, `MEDIANO`, `GRANDE` | Obligatorio |
| `necesidadesEspeciales` | texto | Opcional |
| `aptoNinios` | booleano | Obligatorio |
| `aptoOtrosAnimales` | booleano | Obligatorio |
| `requiereEspacioExterior` | booleano | Obligatorio |
| `estado` (solo en respuesta) | `DISPONIBLE`, `RESERVADO`, `EN_ADAPTACION`, `ADOPTADO`, `NO_DISPONIBLE` | — |
| `motivoNoDisponible` (solo en respuesta) | texto | Obligatorio al marcar NO_DISPONIBLE |

---

## 7. API de Adopciones (`ms-adopciones`)

| Método y ruta | Rol | Cuerpo | Respuesta y errores |
|---|---|---|---|
| `PUT /api/adoptantes/me/perfil` | ADOPTANTE | `PerfilRequest` | 200 `PerfilResponse` |
| `GET /api/adoptantes/me/perfil` | ADOPTANTE | — | 200 / 404 si no lo completó |
| `POST /api/solicitudes` | ADOPTANTE | `{ "animalId": 7 }` | 201 `SolicitudResponse`. 409 por R-01, R-02, R-03 o R-04. 503 si Animales no responde |
| `GET /api/solicitudes?estado=` | ADOPTANTE (las suyas) / COORDINADOR (todas) | — | 200 lista |
| `GET /api/solicitudes/{id}` | Dueño o COORDINADOR | — | 200 / 403 / 404 |
| `POST /api/solicitudes/{id}/cancelar` | ADOPTANTE dueño | — | 200 / 409 si no está PENDIENTE |
| `POST /api/solicitudes/{id}/aprobar` | COORDINADOR | `{ "justificacion": "..." }` | 200 / 409. La justificación es obligatoria si `requiereExcepcion` es verdadero (R-08) |
| `POST /api/solicitudes/{id}/rechazar` | COORDINADOR | `{ "motivo": "..." }` | 200. El motivo es obligatorio (R-08) |
| `POST /api/solicitudes/{id}/confirmar-entrega` | COORDINADOR | — | 200 / 409 (R-09, R-10) |
| `POST /api/solicitudes/{id}/cancelar-entrega` | COORDINADOR | `{ "motivo": "..." }` | 200 / 409 |
| `POST /api/solicitudes/{id}/control-final` | COORDINADOR | `{ "resultado": "CONFORME", "observaciones": "..." }` | 200 / 409 si es anterior a la fecha de control (R-11) |
| `POST /api/solicitudes/{id}/devolucion` | ADOPTANTE dueño | `{ "motivo": "..." }` | 200 / 409 si no está EN_ADAPTACION (R-12) |

`PerfilRequest`: `nombreCompleto`, `telefono`, `provincia`, `localidad`, `direccion` (todos obligatorios), `tipoVivienda` (`CASA`, `DEPARTAMENTO`), `tienePatio`, `hayNinios`, `hayOtrosAnimales` (booleanos).

`SolicitudResponse`: `id`, `animalId`, `adoptanteId`, `estado`, `fechaCreacion`, `distanciaKm` (puede ser nulo), `requiereExcepcion`, `distanciaNoVerificada`, `observaciones` (lista de textos), `motivoRechazo`, `justificacionExcepcion`, `fechaAprobacion`, `fechaEntrega`, `fechaControl`, `resultadoControl`.

### Cómo se evalúa una solicitud nueva (orden)

1. R-03: perfil completo. 2. R-02: sin otra solicitud activa. 3. Consulta a Animales (interna): R-01 (estado DISPONIBLE y sin otra PENDIENTE sobre ese animal). 4. R-04: incompatibilidad dura (la solicitud queda RECHAZADA con motivo, según la regla; **respuesta 201 con `estado: RECHAZADA`**, ver §0). 5. R-05: observaciones. 6. R-06: distancia con Georef (si falla: `distanciaNoVerificada` verdadero). Resultado: solicitud PENDIENTE con las marcas.

Endpoints agregados en v2: `GET /api/solicitudes/{id}/historial` (dueño o COORDINADOR) y `GET /api/adoptantes/{adoptanteId}/perfil` (COORDINADOR).

---

## 8. API de Donaciones (`ms-donaciones`)

| Método y ruta | Rol | Cuerpo | Respuesta y errores |
|---|---|---|---|
| `POST /api/donaciones` | ADOPTANTE | `{ "monto": 500 }` | 201 `DonacionResponse` con `linkPago`. 400 si el monto es menor al mínimo (D-01). 503 si el proveedor no responde |
| `GET /api/donaciones` | ADOPTANTE (las suyas) / COORDINADOR (todas) | — | 200 lista |
| `GET /api/donaciones/{id}` | Dueño o COORDINADOR | — | 200 / 403 / 404. Si está PENDIENTE, consulta al proveedor y actualiza el estado |
| `GET /api/donaciones/total` | COORDINADOR | — | 200 `{ "totalConfirmado": 1500, "cantidad": 3 }` |

`DonacionResponse`: `id`, `donanteId`, `monto`, `moneda` (siempre `ARS`), `estado` (`PENDIENTE`, `CONFIRMADA`), `fechaCreacion`, `fechaActualizacion`, `linkPago`, `verificado` (falso si no se pudo consultar al proveedor en esa llamada).

**Decidido:** las donaciones no tienen estado de rechazo. Sin vencimiento, un pago rechazado puede reintentarse con el mismo enlace, así que la donación sigue PENDIENTE hasta que haya un pago aprobado, y solo entonces pasa a CONFIRMADA (estado final).

---

## 9. Integraciones externas

### 9.1 Georef (desde Adopciones)

- Configuración: `GEOREF_BASE_URL` (por defecto el valor indicado en la documentación; **a verificar** al implementar).
- Se consulta el recurso de direcciones con dirección, provincia y localidad (los nombres exactos de los parámetros son **a verificar**) y se toma la latitud y longitud de la ubicación que devuelve.
- La distancia al refugio se calcula **en nuestro servicio** (línea recta). Las coordenadas del refugio salen de la configuración.
- Falla, dirección no encontrada o respuesta inesperada: `distanciaNoVerificada = true`, sin interrumpir la solicitud.
- Tiempo máximo de espera configurable (propuesta: pocos segundos).

### 9.2 Mercado Pago (desde Donaciones)

- Confirmado en documentación oficial: se puede consultar un pago por su identificador y buscar pagos por referencia externa.
- **A verificar antes de implementar:** el endpoint y el cuerpo exactos para crear la preferencia de pago, los valores del estado de un pago, los montos mínimos y los límites de uso.
- La referencia externa es el identificador de la donación.
- Resultado: si hay un pago aprobado, la donación pasa a CONFIRMADA; si no hay resultado, sigue PENDIENTE.
- Credenciales solo en la variable `MP_ACCESS_TOKEN`, y solo las de la cuenta vendedora **de prueba**.
- Falla al crear el cobro: 503 y no se crea la donación. Falla al verificar: queda PENDIENTE con `verificado = false`.

---

## 10. Persistencia

| Elemento | Contrato |
|---|---|
| Instancia de negocio | Contenedor `postgres`, base `tpi`, un esquema por servicio: `animales`, `adopciones`, `donaciones` |
| Usuarios | Uno por esquema (`animales_user`, `adopciones_user`, `donaciones_user`), con permisos solo sobre su esquema. Se crean con un script de inicialización |
| Keycloak | Contenedor `keycloak-db` propio |
| Volúmenes | Nombrados y descriptivos (`tpi-postgres-data`, `tpi-keycloak-db-data`), como recomienda el apunte 22 |
| Esquema de tablas | `ddl-auto: update` de JPA (propuesta, por simplicidad; no se usan migraciones) |
| Regla | Ningún servicio accede a las tablas de otro. Para saber el estado de un animal, Adopciones lo consulta por la API interna |
| Unicidad (R-01) | Restricción en la base de Adopciones: una sola solicitud PENDIENTE por animal y una sola activa por adoptante |

---

## 11. Variables de entorno

Se definen en un archivo `.env` local que **no se versiona**. Se versiona `.env.example` con los nombres y valores ficticios.

| Variable | Uso |
|---|---|
| `POSTGRES_USER`, `POSTGRES_PASSWORD`, `POSTGRES_DB` | Instancia de negocio |
| `ANIMALES_DB_USER` / `_PASSWORD`, `ADOPCIONES_DB_USER` / `_PASSWORD`, `DONACIONES_DB_USER` / `_PASSWORD` | Usuarios por esquema |
| `KC_DB_USER`, `KC_DB_PASSWORD` | Base de Keycloak |
| `KEYCLOAK_ADMIN`, `KEYCLOAK_ADMIN_PASSWORD` | Administración de Keycloak |
| `KEYCLOAK_ISSUER_URI`, `KEYCLOAK_JWK_URI` | Validación de tokens |
| `ADOPCIONES_SERVICE_CLIENT_SECRET` | Secreto del cliente de servicio |
| `MP_ACCESS_TOKEN` | Credencial de la cuenta vendedora de prueba |
| `GEOREF_BASE_URL` | Georef |
| `REFUGIO_LAT`, `REFUGIO_LON` | Ubicación del refugio. Valores acordados: `REFUGIO_LAT=-31.4334` y `REFUGIO_LON=-64.1918` (Córdoba; refugio ficticio) |
| `DISTANCIA_MAX_KM` (100), `PLAZO_ENTREGA_DIAS` (7), `PERIODO_ADAPTACION_DIAS` (30), `DONACION_MONTO_MINIMO` (1) | Parámetros de política |

---

## 12. Contrato del JAR común (`tpi-common`)

| Contiene | No contiene |
|---|---|
| Formato `ErrorResponse` | Entidades de persistencia |
| Excepciones base (no encontrado, conflicto, no disponible, solicitud inválida) | Reglas de negocio |
| Manejador global de excepciones | DTO propios de cada servicio |
| Conversor de roles de Keycloak (`realm_access.roles` a `ROLE_*`), que tolera que el claim falte | Configuración de rutas de seguridad de cada servicio |
| Filtro de correlación y interceptor para propagarlo en las llamadas | Nada reactivo (el Gateway no lo usa) |

- Cada servicio define su propia cadena de seguridad y reutiliza el conversor.
- Las dependencias de Spring se declaran como `provided` para no arrastrarlas.
- Versión `1.0.0-SNAPSHOT`. Se construye desde la raíz con `mvn clean install`, lo que lo instala en el repositorio local `.m2` (mecanismo mínimo que pide el enunciado).

---

## 13. Docker

- Un `Dockerfile` por servicio, con **contexto en la raíz del repositorio** y construcción en dos etapas: la primera compila todo el reactor (incluido `tpi-common`) y la segunda copia solo el JAR del servicio sobre una imagen liviana de Java 21. Así se puede levantar desde un entorno limpio, sin JARs locales.
- Los apuntes usan la imagen `openjdk`, que creo que está deprecada; propongo `eclipse-temurin` (los nombres de las etiquetas son **a verificar**).
- `healthcheck` en cada contenedor y `depends_on` con condición de salud (Keycloak tarda en arrancar y `depends_on` por sí solo no espera a que esté listo, como indica el apunte 22).
- `.dockerignore` en la raíz.
- Logging con rotación (`json-file`, tamaño y cantidad de archivos limitados), como en el apunte 24.

---

## 14. Documentación de las APIs

- Cada servicio expone OpenAPI/Swagger (springdoc, como en la guía de la cátedra) en `/swagger-ui.html` y `/v3/api-docs`.
- Se accede directamente por el puerto del servicio en desarrollo. Esas rutas están permitidas sin token solo en el perfil de desarrollo.
- La documentación debe coincidir con lo implementado.

---

## 15. Pendientes de este documento

1. ~~Solución del emisor del token (sección 3.4).~~ Resuelto (ver 3.4).
2. ~~Versión de springdoc.~~ Resuelto: 2.8.17.
3. Georef **verificado** contra la API real el 27/10/2026: `GET {base}/direcciones?direccion=&provincia=&localidad=&max=1`, se toma `direcciones[0].ubicacion.lat/lon`; sin resultados devuelve `cantidad: 0`; falta de `direccion` → 400. Mercado Pago **según documentación oficial**: `POST /checkout/preferences` (items con `title`, `quantity`, `unit_price`, `currency_id`; `external_reference`) devuelve `id` e `init_point`; `GET /v1/payments/search?external_reference=` devuelve `results[].status` (`approved` = acreditado). Con la cuenta vendedora de prueba el token empieza con `APP_USR`. Montos mínimos y límites de uso del proveedor: **siguen sin verificar**.
4. Plazos vencidos: los resuelve la tarea programada (`TareaVencimientos`) y también el intento de confirmar una entrega fuera de plazo.
5. Confirmación final de este documento por los cinco integrantes.
