# Bloque G — Defensa oral (20 minutos, demo incluida)

Sin diapositivas. Se muestran: el repositorio en el IDE, los diagramas C1/C2 renderizados, Postman y
una terminal con `docker compose logs`. El cronómetro lo lleva el Integrante 5.

## G.1 Cronograma

| Bloque | Tiempo | Responsable | Tema |
|---|---|---|---|
| 1 | 00:00–03:00 | Integrante 1 | Problema, alcance y negocio |
| 2 | 03:00–06:00 | Integrante 2 | Arquitectura y microservicios |
| 3 | 06:00–09:00 | Integrante 3 | Reglas, persistencia e integraciones |
| 4 | 09:00–12:00 | Integrante 4 | Seguridad |
| 5 | 12:00–17:00 | Integrante 5 (+ intervenciones breves) | Demostración con Postman |
| 6 | 17:00–20:00 | Todos (36 s cada uno) | Calidad, decisiones y cierre |

**Control aritmético:** 3 + 3 + 3 + 3 + 5 + 3 = **20 minutos**. La demo (5 min) está dentro del total.
Las preguntas de la cátedra no están dentro de estos 20 minutos; si interrumpen, se recorta el bloque
6 primero y luego los pasos opcionales de la demo (marcados ◇).

## G.2 Guion

### Bloque 1 — Integrante 1 (00:00–03:00) · Problema y negocio
*Muestra:* README §1–2 en pantalla.

> “Elegimos la adopción responsable. El problema concreto es que un refugio coordina por mensajería:
> no sabe qué animales están disponibles, dos personas piden el mismo, se aprueban adopciones sin
> mirar si el hogar es compatible o si la persona vive cerca para hacer los controles, y la entrega se
> toma como el final: nadie verifica si funcionó. Además, las donaciones no quedan registradas.
>
> Hay dos actores: el Adoptante, que también puede donar, y el Coordinador del refugio. Y tres sistemas
> externos: Keycloak para la identidad, Georef para ubicar el domicilio y Mercado Pago para los cobros.
>
> Definimos que una adopción es exitosa recién cuando pasan tres cosas, en orden: el Coordinador aprueba,
> se confirma la entrega y, después del período de adaptación, el control final es conforme. Ese es
> nuestro proceso central y lo vamos a mostrar de punta a punta.
>
> No es un CRUD porque cada paso aplica reglas que combinan datos de dos servicios y de una fuente
> externa. Por ejemplo: R-01, un animal solo recibe solicitudes si está disponible y no tiene otra
> pendiente; R-04, si el animal no es apto con niños y en el hogar hay niños, la solicitud queda
> rechazada automáticamente; R-06, si el domicilio está a más de 100 km, se marca y el Coordinador
> solo puede aprobar justificándolo; R-09, si la entrega no se confirma en 7 días, una tarea programada
> cancela y libera al animal. En total implementamos trece reglas de adopción y cuatro de donaciones,
> cada una con pruebas.
>
> El criterio de diseño es: el sistema recomienda y bloquea, pero la decisión final es humana.”

*Transición:* “Para que estas reglas no se mezclen, separamos responsabilidades. Lo explica [2].”

### Bloque 2 — Integrante 2 (03:00–06:00) · Arquitectura
*Muestra:* C1, luego C2, luego `pom.xml` raíz y `tpi-common`.

> “En el C1 el sistema es una caja negra con los dos actores y los tres externos. En el C2 se abre:
> un API Gateway como única entrada y tres servicios Spring Boot.
>
> Separamos por dueño de la información. ms-animales es dueño del animal y de su estado; un animal
> existe aunque nadie lo pida. ms-adopciones es dueño del proceso: perfiles, solicitudes, decisiones,
> entrega, control e historial. ms-donaciones es dueño de las donaciones y de su confirmación con
> Mercado Pago; cambiar de plataforma de pagos no toca a los otros dos.
>
> La colaboración real está entre Adopciones y Animales: al presentar, Adopciones necesita el estado y
> las aptitudes del animal; al aprobar, entregar o cerrar, le pide a Animales la transición. Pero
> Animales decide si es válida: tiene su máquina de estados en `EstadoAnimal` y rechaza con 409, por
> ejemplo, reservar un animal que no está disponible. Esa API es interna: el Gateway no la enruta.
>
> La comunicación es REST síncrona con RestClient y timeouts de tres segundos, porque el usuario necesita
> la respuesta en el momento; si Animales no responde, devolvemos 503 y no avanzamos.
>
> El componente común es un JAR Maven, `tpi-common`, que se instala en el `.m2` y usan los tres
> servicios: formato único de error, manejador global, conversor de roles de Keycloak y correlación.
> Lo que NO compartimos son entidades ni reglas: hasta los estados del animal que usa Adopciones son
> una copia, porque son contrato HTTP, no modelo compartido.
>
> Persistencia: un PostgreSQL con un esquema y un usuario por servicio, sin permisos cruzados, y
> Keycloak con su propia base.”

*Transición:* “Veamos dónde están las reglas y cómo tratamos las fallas. [3].”

### Bloque 3 — Integrante 3 (06:00–09:00) · Reglas, persistencia e integraciones
*Muestra:* `Solicitud.java` (método `aprobar`, `cambiarEstado`), `SolicitudService.crear` y `conCambioDeAnimal`, `GeorefClient`.

> “Las reglas están en el modelo. `Solicitud` no tiene setters: para aprobar se llama a `aprobar()`,
> que verifica que esté PENDIENTE y que, si requiere excepción por distancia, haya justificación (R-08);
> después cambia el estado y agrega un evento al historial con fecha, actor y motivo.
>
> La presentación sigue un orden: perfil completo, sin otra solicitud activa, consulta a Animales,
> incompatibilidad dura, observaciones y por último Georef. Georef nos da la latitud y longitud del
> domicilio; la distancia al refugio la calculamos nosotros con Haversine. Si Georef no responde o no
> encuentra la dirección, la solicitud igual entra con la marca ‘distancia no verificada’: una falla
> externa no bloquea porque la decisión es humana.
>
> Para la concurrencia tenemos dos defensas: `@Version` para que dos operaciones no se pisen, y
> columnas de bloqueo con UNIQUE: `animal_pendiente` vale el id del animal solo mientras la solicitud
> está pendiente. Si dos personas piden el mismo animal en el mismo milisegundo, la base rechaza la
> segunda.
>
> Como no hay transacción distribuida, al aprobar primero aplicamos el cambio local y hacemos flush, y
> recién después pedimos RESERVADO a Animales. Si Animales dice que no o no responde, se revierte: la
> solicitud nunca queda aprobada con el animal libre. Lo prueba `FlujoAdopcionIntegrationTest`.
>
> Donaciones usa Mercado Pago: creamos la preferencia con una referencia propia y, al consultar la
> donación, buscamos un pago aprobado con esa referencia. Si el proveedor falla al crear, no guardamos
> nada y devolvemos 503; si falla al verificar, queda pendiente con `verificado: false`.”

*Transición:* “Todo esto supone que sabemos quién llama. [4].”

### Bloque 4 — Integrante 4 (09:00–12:00) · Seguridad
*Muestra:* `realm-adopciones.json`, `gateway/.../SecurityConfig.java`, `tpi-common/.../KeycloakRoleConverter.java`, `ms-adopciones/.../SecurityConfig.java`.

> “El usuario se autentica contra Keycloak con OpenID Connect y recibe un JWT firmado. El backend nunca
> ve contraseñas. Los roles ADOPTANTE, COORDINADOR y SERVICIO están en el realm, que se importa al
> arrancar: no hay configuración manual.
>
> El Gateway tiene el Security Filter: valida firma con las claves públicas de Keycloak, emisor y
> vencimiento. Sin token o con token inválido, 401. Además, en `/api/**` solo acepta tokens de
> usuarios: un token de servicio recibe 403.
>
> Pero no confiamos solo en el Gateway: cada servicio vuelve a validar el JWT y aplica su propia matriz
> de permisos. Autenticar es saber quién sos; autorizar es decidir qué podés hacer. Un adoptante
> autenticado que intenta aprobar recibe 403, y uno que pide la solicitud de otra persona también:
> eso lo verifica el servicio comparando el `sub` del token con el dueño.
>
> Keycloak pone los roles en `realm_access.roles`; Spring espera `ROLE_...`. El conversor del JAR común
> hace esa traducción.
>
> Para la API interna, Adopciones pide su propio token con client credentials y rol SERVICIO. No
> reenvía el token del usuario, porque así un coordinador podría ordenar transiciones salteando el
> proceso.
>
> Un problema real que resolvimos: el emisor del token depende de la URL con que se lo pide. Fijamos
> `KC_HOSTNAME` en localhost:8180 y los servicios validan ese emisor pero bajan las claves por la red
> interna. Los secretos están solo en el `.env`, que no se versiona.”

*Transición:* “Ahora lo vemos funcionando. [5].”

### Bloque 5 — Integrante 5 + intervenciones (12:00–17:00) · Demostración

**Antes de empezar (no cuenta en el tiempo):** sistema levantado con `PERIODO_ADAPTACION_DIAS=0`,
base limpia, tokens ya pedidos (carpeta 0 de la colección), animal Toby creado, perfiles cargados.
Tener abierta una terminal con `docker compose logs -f ms-adopciones ms-animales`.

| Min | Solicitud (colección) | Esperado | Quién comenta (≤ 15 s) |
|---|---|---|---|
| 12:00 | 0 · “Token coordinador1” → mostrar el JWT decodificado en jwt.io o en la consola (iss, roles) | 200 | 4: “acá están los roles del realm” |
| 12:30 | 1 · “Sin token” | 401 formato único | 4 |
| 12:45 | 1 · “Adoptante intenta dar de alta un animal” | 403 | 4: “autenticado no es autorizado” |
| 13:05 | 3 · “Adoptante1 presenta la solicitud” | 201 PENDIENTE, `distanciaKm` (Georef), observación R-05 | 3: “la distancia viene de Georef en vivo” |
| 13:50 | 3 · “Adoptante2 pide el mismo animal” | 409 R-01 | 1 |
| 14:10 | 3 · “Coordinador aprueba” | 200 APROBADA; en la terminal se ve el PUT interno con el mismo X-Correlation-ID | 2: “Adopciones pidió, Animales decidió” |
| 14:40 | 3 · “El animal quedó RESERVADO” | 200 RESERVADO | 2 |
| 14:55 | 3 · “R-13: no se puede marcar NO_DISPONIBLE” | 409 | 2 |
| 15:15 | 3 · “Confirmar entrega” | 200 EN_ADAPTACION, `fechaControl` | 3 |
| 15:40 | 3 · “Control final CONFORME” | 200 FINALIZADA | 3 |
| 16:00 | 3 · “El animal quedó ADOPTADO” | 200 ADOPTADO | 1: “resultado de negocio” |
| 16:15 | 3 · “Historial” | 4 eventos con actor y fecha | 1 |
| 16:35 ◇ | 5 · “D-01 monto menor al mínimo” y “Crear donación” (mostrar `linkPago`) | 400 / 201 | 3 |
| 16:50 | Integrante 5 cierra: “Recorrimos el proceso completo con persistencia, reglas y colaboración real.” | | |

### Bloque 6 — Todos (17:00–20:00, ~36 s cada uno)
- **Integrante 5 — Pruebas y despliegue:** “`mvn clean install` ejecuta 128 pruebas sin Docker ni Internet: reglas, seguridad, clientes HTTP con servidor simulado y el flujo completo con un reloj controlable. Docker Compose levanta las siete piezas con healthchecks; solo publica Gateway y Keycloak.”
- **Integrante 2 — Patrones:** “Usamos API Gateway, adaptadores para cada API externa, repositorio, DTO, modelo de dominio con máquina de estados y bloqueo optimista. Evaluamos Circuit Breaker y lo descartamos: con una sola dependencia síncrona y timeouts cortos daba el mismo 503 con más complejidad.”
- **Integrante 3 — Decisiones y alternativas:** “Elegimos REST síncrono en vez de mensajería, esquema por servicio en vez de una base por servicio, y Georef en vez de OSRM o Google por ser pública y no requerir clave.”
- **Integrante 4 — Limitaciones:** “La distancia es en línea recta; no hay webhooks de Mercado Pago; y existe un caso residual sin transacción distribuida que documentamos en el ADR-06.”
- **Integrante 1 — Alcance y cierre:** “Cumplimos los cinco procesos comprometidos. Cambiamos una cosa del contrato —R-04 devuelve la solicitud rechazada en vez de un 409— y lo dejamos documentado. El valor del backend es que una adopción solo se da por exitosa cuando se verificó, con cada decisión trazada. Gracias.”

## G.3 Plan alternativo

| Falla | Qué hacer | Qué decir (honesto) |
|---|---|---|
| Sin Internet / Georef caído | Seguir: la solicitud sale con `distanciaNoVerificada: true` | “Esto es el comportamiento ante falla de R-06; no es una medición real de distancia.” |
| Mercado Pago no responde o sin credenciales | Mostrar el 503 y la prueba `FlujoDonacionIntegrationTest` | “La integración se probó con respuestas simuladas; en vivo no está disponible.” |
| Keycloak tarda en arrancar | Levantar 15 min antes; `docker compose ps` hasta `healthy`; tokens pedidos de antemano (duran 30 min) | — |
| Token vencido durante la demo | Re-ejecutar la carpeta 0 (3 clics) | — |
| Docker no levanta en la máquina de la defensa | Ejecutar `mvn test -pl ms-adopciones -Dtest=FlujoAdopcionIntegrationTest` y mostrar el reporte | “Es la misma secuencia con H2 y dobles de prueba; no reemplaza el despliegue real.” |
| Datos sucios de un ensayo | `docker compose down -v && docker compose up -d` (≈ 2 min) | — |

## G.4 Lista de comprobación previa

- [ ] `.env` completo; `PERIODO_ADAPTACION_DIAS=0`; `MP_ACCESS_TOKEN` de la vendedora de prueba.
- [ ] `docker compose down -v` y `docker compose up --build -d` 15 minutos antes; todo `healthy`.
- [ ] `./scripts/prueba-humo.sh` en verde (luego `down -v` y `up` otra vez para empezar limpio).
- [ ] Postman: entorno “TPI local” con `demoPassword`; carpeta 0 ejecutada; Toby creado; perfiles cargados.
- [ ] C1 y C2 renderizados y abiertos; IDE con las clases del guion en pestañas.
- [ ] Terminal con logs filtrados por correlación.
- [ ] Ensayo cronometrado completo al menos dos veces, rotando quién responde preguntas.
- [ ] Ventana de incógnito con la cuenta compradora de prueba de Mercado Pago (◇ opcional).
