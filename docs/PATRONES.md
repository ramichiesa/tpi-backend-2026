# Bloque F — Patrones presentes en el código

Solo se listan patrones cuya **intención** se cumple en el código. Al final, los que se evaluaron y no se usan.

| Patrón | Categoría | Dónde | Problema que resuelve |
|---|---|---|---|
| API Gateway | Arquitectónico (microservicios) | `gateway/` | Único punto de entrada, oculta la topología, autentica en el borde |
| Database per Service (variante esquema por servicio) | Arquitectónico | `01-esquemas.sh`, `default_schema` | Cada servicio es dueño de sus datos |
| Access Token / Service Account | Seguridad (OAuth2) | `ServiceTokenProvider`, cliente `adopciones-service` | Llamadas internas autenticadas sin un usuario |
| Adapter (cliente de API externa) | GoF estructural / integración | `GeorefClient`, `MercadoPagoClient`, `AnimalesClient` | Traducir un protocolo/formato ajeno al modelo y a las excepciones propias |
| Repository | Empresarial (DDD/PoEAA) | `*Repository` (Spring Data JPA) | Acceso a datos detrás de una interfaz de colección |
| Data Transfer Object | Empresarial (PoEAA) | `dto/*`, records | No exponer entidades; contratos HTTP estables |
| Domain Model rico / máquina de estados | Empresarial (PoEAA, DDD) | `Animal`, `Solicitud`, `Donacion`, `EstadoAnimal`, `EstadoSolicitud` | Las reglas viven junto a los datos; no hay setters que las salteen |
| Aggregate | DDD | `Solicitud` + `EventoSolicitud` + observaciones | Una sola raíz controla las transiciones y el historial; se guarda en cascada |
| Optimistic Offline Lock | Empresarial (PoEAA) | `@Version` en `Animal`, `Solicitud`, `Donacion` | Evitar que dos operaciones concurrentes se pisen |
| Template Method (de Spring) | GoF de comportamiento (uso) | `TransactionTemplate.execute(...)` en `SolicitudService`/`DonacionService` | Delimitar transacciones explícitas alrededor de llamadas remotas |
| Chain of Responsibility (de Spring) | GoF de comportamiento (uso) | `SecurityFilterChain`, `CorrelationIdFilter`, `CorrelationIdGlobalFilter`, `CorrelationIdInterceptor` | Procesar la petición en etapas desacopladas |
| Correlation Identifier | Integración (EIP) | `X-Correlation-ID` en Gateway, filtro e interceptor | Seguir una operación distribuida en los logs |
| Dependency Injection | Mecanismo de Spring | constructores de todos los `@Service`, `@Bean` de `ClientesConfig` | Desacoplar creación y uso; reemplazar dependencias en pruebas |
| Timeout + fallo rápido / degradación | Resiliencia | `ClientesConfig` (3–5 s), `EvaluadorSolicitud.evaluarDistancia` | Que una dependencia lenta no cuelgue el servicio; Georef degrada a “no verificada” |

## Detalle de los principales

### Adapter — `GeorefClient`, `MercadoPagoClient`, `AnimalesClient`
- **Funcionamiento:** cada clase recibe un `RestClient`, arma la petición del proveedor y devuelve tipos propios (`Coordenadas`, `Cobro`, `AnimalDto`). Traduce errores (`RestClientException`, 404, 409, 5xx, formato inesperado) a `RecursoNoEncontrado`, `Conflicto` o `ServicioNoDisponible`.
- **Por qué:** el resto del servicio no conoce `direcciones[0].ubicacion` ni `results[].status`; un cambio del proveedor se arregla en un solo lugar y las pruebas lo verifican con `MockRestServiceServer`.
- **Costo:** una clase más por proveedor.
- **Cómo decirlo:** “El servicio habla con Georef a través de un adaptador que convierte su JSON en coordenadas y sus errores en nuestras excepciones; por eso el evaluador solo ve ‘coordenadas o no verificada’.”
- **Pregunta probable:** “¿Es GoF Adapter o un simple cliente?” → Cumple la intención de Adapter (convertir una interfaz ajena a la que espera el cliente); no definimos una interfaz `Target` separada porque hay una sola implementación — si hubiera dos proveedores de pagos, extraeríamos `ProveedorPagos`.

### Domain Model / máquina de estados — `Animal`, `Solicitud`
- **Funcionamiento:** `Solicitud.aprobar()` verifica estado y R-08, fija fecha, cambia el estado, recalcula las columnas de bloqueo y agrega el evento. No hay `setEstado`.
- **Alternativa:** servicio “transaction script” con entidades anémicas (más fácil de leer al principio, pero las reglas se dispersan).
- **Pregunta probable:** “¿Por qué la regla está en la entidad y no en el servicio?” → Porque así es imposible cambiar el estado sin pasar por la regla, y se prueba sin Spring (`SolicitudTest`, `AnimalTest`).

### Repository y DTO
- Interfaces `JpaRepository` con consultas derivadas (`existsByAdoptanteIdAndEstadoIn`). Los controladores devuelven records `*Response` creados con `desde(entidad)`; la dirección del adoptante solo aparece en `PerfilResponse`.

### Optimistic Lock + UNIQUE
- `@Version` hace que un `UPDATE` con versión vieja falle → 409 (`GlobalExceptionHandler.handleConcurrencia`). Las UNIQUE de bloqueo cubren la carrera de inserción de R-01/R-02 (`SolicitudRepositoryTest`).

## Evaluados y NO usados

| Patrón | Por qué no |
|---|---|
| Circuit Breaker (Resilience4j) | Hay una sola dependencia síncrona crítica y timeouts cortos; un circuito abierto daría el mismo 503 sin cambiar la regla. Se agregaría si el volumen justificara cortar llamadas. |
| Retry automático | Reintentar un `PUT` de transición sin idempotencia podría duplicar efectos; la tarea programada reintenta en la próxima ejecución. |
| Saga orquestada con eventos | Requiere mensajería; se resolvió con orden local-primero y *rollback* (ADR-06). |
| Strategy | Las reglas R-04/R-05/R-06 son métodos de `EvaluadorSolicitud`; una interfaz por regla no aportaba (no se intercambian en ejecución). |
| Observer / eventos de dominio | No hay suscriptores: el historial se escribe en el mismo agregado. |
| Factory Method (GoF) | `Solicitud.pendiente()` y `rechazadaPorIncompatibilidad()` son *static factory methods* (Effective Java), no el patrón GoF con subclases. |
