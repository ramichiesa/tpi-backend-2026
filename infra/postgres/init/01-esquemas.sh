#!/bin/bash
# Se ejecuta UNA sola vez, cuando el volumen de PostgreSQL esta vacio (primer arranque).
# Crea un usuario por servicio y un esquema propiedad de ese usuario. Ningun usuario tiene
# permisos sobre el esquema de otro: un servicio no puede leer ni escribir las tablas ajenas.
set -euo pipefail

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL
    CREATE USER ${ANIMALES_DB_USER} WITH PASSWORD '${ANIMALES_DB_PASSWORD}';
    CREATE USER ${ADOPCIONES_DB_USER} WITH PASSWORD '${ADOPCIONES_DB_PASSWORD}';
    CREATE USER ${DONACIONES_DB_USER} WITH PASSWORD '${DONACIONES_DB_PASSWORD}';

    CREATE SCHEMA animales AUTHORIZATION ${ANIMALES_DB_USER};
    CREATE SCHEMA adopciones AUTHORIZATION ${ADOPCIONES_DB_USER};
    CREATE SCHEMA donaciones AUTHORIZATION ${DONACIONES_DB_USER};

    ALTER ROLE ${ANIMALES_DB_USER} SET search_path = animales;
    ALTER ROLE ${ADOPCIONES_DB_USER} SET search_path = adopciones;
    ALTER ROLE ${DONACIONES_DB_USER} SET search_path = donaciones;

    -- Nadie crea objetos en el esquema public
    REVOKE CREATE ON SCHEMA public FROM PUBLIC;
EOSQL

echo "Esquemas animales, adopciones y donaciones creados con usuarios separados"
