#!/bin/bash
# Prueba de humo del proceso central a traves del Gateway (requiere bash, curl y python3).
#
# Uso con el despliegue de Docker Compose (obtiene los tokens de Keycloak):
#   DEMO_USERS_PASSWORD=<la del .env> ./scripts/prueba-humo.sh
# Uso con tokens ya obtenidos (por ejemplo, en una verificacion sin Keycloak):
#   TOKEN_ADOPTANTE1=... TOKEN_ADOPTANTE2=... TOKEN_COORDINADOR=... ./scripts/prueba-humo.sh
#
# Precondicion: datos de demostracion cargados (ANIMALES_DATOS_DEMO=true) y, para recorrer el
# control final sin esperar, PERIODO_ADAPTACION_DIAS=0. Termina con codigo 1 ante el primer fallo.
set -uo pipefail

GATEWAY="${GATEWAY:-http://localhost:8080}"
KEYCLOAK="${KEYCLOAK:-http://localhost:8180}"
FALLOS=0

token() {
  curl -s -X POST "$KEYCLOAK/realms/adopciones/protocol/openid-connect/token" \
    -d grant_type=password -d client_id=tpi-client -d username="$1" -d password="$DEMO_USERS_PASSWORD" \
    | python3 -c 'import sys,json; print(json.load(sys.stdin)["access_token"])'
}

if [ -z "${TOKEN_ADOPTANTE1:-}" ]; then
  : "${DEMO_USERS_PASSWORD:?Definir DEMO_USERS_PASSWORD o los TOKEN_*}"
  TOKEN_ADOPTANTE1=$(token adoptante1)
  TOKEN_ADOPTANTE2=$(token adoptante2)
  TOKEN_COORDINADOR=$(token coordinador1)
fi

# llamar METODO RUTA TOKEN [CUERPO] -> deja el codigo en $CODIGO y el cuerpo en $CUERPO
llamar() {
  local args=(-s -o /tmp/tpi_resp.json -w '%{http_code}' -X "$1" "$GATEWAY$2" -H "X-Correlation-ID: humo-$RANDOM")
  [ -n "$3" ] && args+=(-H "Authorization: Bearer $3")
  [ -n "${4:-}" ] && args+=(-H 'Content-Type: application/json' -d "$4")
  CODIGO=$(curl "${args[@]}")
  CUERPO=$(cat /tmp/tpi_resp.json)
}

campo() { echo "$CUERPO" | python3 -c 'import sys,json; d=json.load(sys.stdin); print(eval("d"+sys.argv[1]))' "$1"; }

esperar() {
  if [ "$CODIGO" = "$1" ]; then echo "  OK   $2 -> $CODIGO"; else echo "  FALLO $2 -> $CODIGO (esperado $1): $CUERPO"; FALLOS=$((FALLOS+1)); fi
}

echo "== Seguridad"
llamar GET /api/animales "";                               esperar 401 "Sin token"
llamar POST /api/animales "$TOKEN_ADOPTANTE1" '{}';         esperar 403 "Adoptante intenta dar de alta un animal"
llamar GET "/api/animales?estado=DISPONIBLE" "$TOKEN_ADOPTANTE1"; esperar 200 "Catalogo de disponibles"

echo "== Alta de un animal propio para la prueba (COORDINADOR)"
llamar POST /api/animales "$TOKEN_COORDINADOR" '{"nombre":"Humo","especie":"PERRO","edadMeses":18,"tamanio":"MEDIANO","aptoNinios":true,"aptoOtrosAnimales":true,"requiereEspacioExterior":true}'
esperar 201 "Alta de animal"; ANIMAL=$(campo "['id']")

echo "== Perfiles"
llamar PUT /api/adoptantes/me/perfil "$TOKEN_ADOPTANTE1" '{"nombreCompleto":"Ana Adoptante","telefono":"3515550000","provincia":"Cordoba","localidad":"Cordoba","direccion":"Av Colon 1000","tipoVivienda":"DEPARTAMENTO","tienePatio":false,"hayNinios":true,"hayOtrosAnimales":false}'
esperar 200 "Perfil adoptante1"
llamar PUT /api/adoptantes/me/perfil "$TOKEN_ADOPTANTE2" '{"nombreCompleto":"Bruno Adoptante","telefono":"3515550001","provincia":"Cordoba","localidad":"Cordoba","direccion":"Bv San Juan 500","tipoVivienda":"CASA","tienePatio":true,"hayNinios":false,"hayOtrosAnimales":false}'
esperar 200 "Perfil adoptante2"

echo "== P2: presentar (Adopciones consulta a Animales y a Georef)"
llamar POST /api/solicitudes "$TOKEN_ADOPTANTE1" "{\"animalId\":$ANIMAL}"
esperar 201 "Solicitud de adoptante1"; SOL=$(campo "['id']")
echo "       estado=$(campo "['estado']") distanciaKm=$(campo "['distanciaKm']") noVerificada=$(campo "['distanciaNoVerificada']") observaciones=$(campo "['observaciones']")"
llamar POST /api/solicitudes "$TOKEN_ADOPTANTE2" "{\"animalId\":$ANIMAL}"; esperar 409 "R-01: segunda solicitud sobre el mismo animal"

echo "== P2: decision del Coordinador"
llamar POST "/api/solicitudes/$SOL/aprobar" "$TOKEN_ADOPTANTE1" '{}'; esperar 403 "Adoptante intenta aprobar"
llamar POST "/api/solicitudes/$SOL/aprobar" "$TOKEN_COORDINADOR" '{}'; esperar 200 "Aprobar (animal -> RESERVADO)"
llamar GET "/api/animales/$ANIMAL" "$TOKEN_COORDINADOR";               esperar 200 "Animal reservado: $(campo "['estado']" 2>/dev/null)"
llamar POST "/api/animales/$ANIMAL/marcar-no-disponible" "$TOKEN_COORDINADOR" '{"motivo":"Prueba"}'; esperar 409 "R-13: no se puede dejar NO_DISPONIBLE"

echo "== P3 y P4: entrega y control final"
llamar POST "/api/solicitudes/$SOL/confirmar-entrega" "$TOKEN_COORDINADOR"; esperar 200 "Confirmar entrega (animal -> EN_ADAPTACION)"
llamar POST "/api/solicitudes/$SOL/control-final" "$TOKEN_COORDINADOR" '{"resultado":"CONFORME","observaciones":"Adaptacion exitosa"}'
esperar 200 "Control final conforme: $(campo "['estado']" 2>/dev/null)"
llamar GET "/api/animales/$ANIMAL" "$TOKEN_ADOPTANTE1"; esperar 200 "Animal final: $(campo "['estado']" 2>/dev/null)"
llamar GET "/api/solicitudes/$SOL/historial" "$TOKEN_ADOPTANTE1"; esperar 200 "Historial con $(campo ".__len__()" 2>/dev/null) eventos"
llamar GET "/api/solicitudes/$SOL" "$TOKEN_ADOPTANTE2"; esperar 403 "Otro adoptante no ve la solicitud ajena"

echo "== P5: donaciones (requiere MP_ACCESS_TOKEN valido; sin el, 503 es el comportamiento esperado)"
llamar POST /api/donaciones "$TOKEN_ADOPTANTE1" '{"monto":0.5}'; esperar 400 "D-01: monto menor al minimo"
llamar POST /api/donaciones "$TOKEN_ADOPTANTE1" '{"monto":500}'; echo "  INFO crear donacion -> $CODIGO"
llamar GET /api/donaciones/total "$TOKEN_COORDINADOR"; esperar 200 "Total confirmado"

echo
if [ "$FALLOS" -eq 0 ]; then echo "Prueba de humo superada"; else echo "$FALLOS verificacion(es) fallaron"; exit 1; fi
