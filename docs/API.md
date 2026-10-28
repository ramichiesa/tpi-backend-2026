# Referencia de la API

Todas las rutas públicas se consumen por el Gateway (`http://localhost:8080`) con
`Authorization: Bearer <JWT>`. Formato de error único:

```json
{"timestamp":"2026-10-27T10:15:30","status":409,"error":"Conflicto",
 "message":"El animal 7 no esta disponible (estado RESERVADO) (R-01)","path":"/api/solicitudes","errores":[]}
```
`errores` lista `{campo, mensaje}` solo en los 400 de validación. Códigos: 200/201 (con `Location`),
400 datos inválidos, 401 sin token o token inválido, 403 rol insuficiente o recurso ajeno, 404,
409 regla o estado, 503 otro servicio o proveedor no responde (504 si el Gateway agota la espera).

La especificación OpenAPI de cada servicio está en `/v3/api-docs` y `/swagger-ui.html` de su puerto.

## ms-animales

| Método y ruta | Rol | Cuerpo | Respuestas |
|---|---|---|---|
| `POST /api/animales` | COORDINADOR | `AnimalRequest` | 201 `AnimalResponse`, 400 |
| `GET /api/animales?estado=&especie=` | ADOPTANTE, COORDINADOR | — | 200 lista, 400 si el filtro no existe |
| `GET /api/animales/{id}` | ADOPTANTE, COORDINADOR | — | 200, 404 |
| `PUT /api/animales/{id}` | COORDINADOR | `AnimalRequest` | 200, 400, 404 (no cambia el estado) |
| `POST /api/animales/{id}/marcar-no-disponible` | COORDINADOR | `{"motivo"}` | 200, 400, 404, 409 (R-13 o no DISPONIBLE) |
| `POST /api/animales/{id}/marcar-disponible` | COORDINADOR | — | 200, 404, 409 |
| `GET /internal/animales/{id}` | SERVICIO (no publicada) | — | 200, 404 |
| `PUT /internal/animales/{id}/estado` | SERVICIO (no publicada) | `{"nuevoEstado"}` | 200, 400, 404, 409 transición inválida |

`AnimalRequest`: `nombre` (obligatorio, ≤100), `especie` (`PERRO|GATO|OTRO`), `edadMeses` (0–400),
`tamanio` (`PEQUENIO|MEDIANO|GRANDE`), `necesidadesEspeciales` (opcional), `aptoNinios`,
`aptoOtrosAnimales`, `requiereEspacioExterior` (booleanos obligatorios).
`AnimalResponse`: lo anterior + `id`, `estado`, `motivoNoDisponible`.

## ms-adopciones

| Método y ruta | Rol | Cuerpo | Respuestas |
|---|---|---|---|
| `PUT /api/adoptantes/me/perfil` | ADOPTANTE | `PerfilRequest` | 200, 400 |
| `GET /api/adoptantes/me/perfil` | ADOPTANTE | — | 200, 404 |
| `GET /api/adoptantes/{adoptanteId}/perfil` | COORDINADOR | — | 200, 404 |
| `POST /api/solicitudes` | ADOPTANTE | `{"animalId"}` | 201 PENDIENTE o RECHAZADA (R-04); 404 animal; 409 R-01/R-02/R-03; 503 Animales |
| `GET /api/solicitudes?estado=` | ADOPTANTE (propias), COORDINADOR (todas) | — | 200 |
| `GET /api/solicitudes/{id}` | dueño o COORDINADOR | — | 200, 403, 404 |
| `GET /api/solicitudes/{id}/historial` | dueño o COORDINADOR | — | 200 lista de eventos |
| `POST /api/solicitudes/{id}/cancelar` | ADOPTANTE dueño | — | 200, 403, 409 si no PENDIENTE |
| `POST /api/solicitudes/{id}/aprobar` | COORDINADOR | `{"justificacion"}` opcional | 200, 409 (estado, R-08, animal no reservable), 503 |
| `POST /api/solicitudes/{id}/rechazar` | COORDINADOR | `{"motivo"}` | 200, 400 sin motivo, 409 |
| `POST /api/solicitudes/{id}/confirmar-entrega` | COORDINADOR | — | 200, 409 (no APROBADA o plazo vencido: se cancela), 503 |
| `POST /api/solicitudes/{id}/cancelar-entrega` | COORDINADOR | `{"motivo"}` | 200, 400, 409, 503 |
| `POST /api/solicitudes/{id}/control-final` | COORDINADOR | `{"resultado":"CONFORME|DESFAVORABLE","observaciones"}` | 200, 400 (desfavorable sin observaciones), 409 (R-11), 503 |
| `POST /api/solicitudes/{id}/devolucion` | ADOPTANTE dueño | `{"motivo"}` | 200, 403, 409 si no EN_ADAPTACION |

`PerfilRequest`: `nombreCompleto`, `telefono`, `provincia`, `localidad`, `direccion` (calle y altura),
`tipoVivienda` (`CASA|DEPARTAMENTO`), `tienePatio`, `hayNinios`, `hayOtrosAnimales` — todos obligatorios.

`SolicitudResponse`: `id`, `animalId`, `animalNombre`, `adoptanteId`, `estado`, `fechaCreacion`,
`distanciaKm`, `requiereExcepcion`, `distanciaNoVerificada`, `observaciones[]`, `motivoRechazo`,
`justificacionExcepcion`, `fechaAprobacion`, `fechaEntrega`, `fechaControl`, `resultadoControl`,
`observacionesControl`, `motivoCierre`.

Ejemplo real (verificación local del 27/10/2026, Georef en vivo):
```json
{"estado":"PENDIENTE","distanciaKm":2.7,"requiereExcepcion":false,"distanciaNoVerificada":false,
 "observaciones":["El animal necesita espacio exterior y el hogar no tiene patio (R-05)"]}
```

## ms-donaciones

| Método y ruta | Rol | Cuerpo | Respuestas |
|---|---|---|---|
| `POST /api/donaciones` | ADOPTANTE | `{"monto": 500}` | 201 con `linkPago`, 400 (D-01 o validación), 503 Mercado Pago |
| `GET /api/donaciones` | ADOPTANTE (propias), COORDINADOR (todas) | — | 200 |
| `GET /api/donaciones/{id}` | dueño o COORDINADOR | — | 200 (si PENDIENTE consulta el pago), 403, 404 |
| `GET /api/donaciones/total` | COORDINADOR | — | 200 `{"totalConfirmado","cantidad"}` |

`DonacionResponse`: `id`, `donanteId`, `monto`, `moneda` (ARS), `estado` (`PENDIENTE|CONFIRMADA`),
`fechaCreacion`, `fechaActualizacion`, `linkPago`, `verificado`.

## Integraciones externas

| Proveedor | Llamada | Uso | Falla |
|---|---|---|---|
| Georef | `GET https://apis.datos.gob.ar/georef/api/v2.0/direcciones?direccion&provincia&localidad&max=1` | `direcciones[0].ubicacion.lat/lon` → distancia al refugio (R-06) | timeout 4 s, error, sin resultados o formato inesperado → `distanciaNoVerificada` |
| Mercado Pago | `POST https://api.mercadopago.com/checkout/preferences` | `id`, `init_point` → `linkPago` | 503 y no se guarda la donación |
| Mercado Pago | `GET https://api.mercadopago.com/v1/payments/search?external_reference=` | `results[].status == "approved"` → CONFIRMADA (D-02) | queda PENDIENTE con `verificado=false` |
| Keycloak | `POST /realms/adopciones/protocol/openid-connect/token` (client credentials) | token SERVICIO | 503 |
