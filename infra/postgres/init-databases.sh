#!/bin/bash
# =============================================================================
# init-databases.sh
# Crea las bases de datos de cada microservicio.
#
# Este script es ejecutado por PostgreSQL UNA SOLA VEZ: cuando el volumen
# postgres_data no existe todavía. Si el volumen ya tiene datos, este script
# NO vuelve a correr aunque lo modifiques.
# Para forzar una reinicialización: docker-compose down -v
# =============================================================================
set -e

psql -v ON_ERROR_STOP=1 --username "$POSTGRES_USER" --dbname "$POSTGRES_DB" <<-EOSQL

    -- Cada microservicio tiene su propia base de datos.
    -- NUNCA habrá joins entre ellas: la comunicación es vía eventos de Kafka.

    CREATE DATABASE auth_db;
    CREATE DATABASE auth_test;
    CREATE DATABASE user_db;
    CREATE DATABASE user_test;
    CREATE DATABASE project_db;
    CREATE DATABASE project_test;
    CREATE DATABASE task_db;
    CREATE DATABASE task_test;
    CREATE DATABASE ai_db;
    CREATE DATABASE ai_test;
    CREATE DATABASE notification_db;
    CREATE DATABASE notification_test;
    CREATE DATABASE keycloak_db;

    GRANT ALL PRIVILEGES ON DATABASE auth_db          TO epm_admin;
    GRANT ALL PRIVILEGES ON DATABASE auth_test        TO epm_admin;
    GRANT ALL PRIVILEGES ON DATABASE user_db          TO epm_admin;
    GRANT ALL PRIVILEGES ON DATABASE user_test        TO epm_admin;
    GRANT ALL PRIVILEGES ON DATABASE project_db       TO epm_admin;
    GRANT ALL PRIVILEGES ON DATABASE project_test     TO epm_admin;
    GRANT ALL PRIVILEGES ON DATABASE task_db          TO epm_admin;
    GRANT ALL PRIVILEGES ON DATABASE task_test        TO epm_admin;
    GRANT ALL PRIVILEGES ON DATABASE ai_db            TO epm_admin;
    GRANT ALL PRIVILEGES ON DATABASE ai_test          TO epm_admin;
    GRANT ALL PRIVILEGES ON DATABASE notification_db  TO epm_admin;
    GRANT ALL PRIVILEGES ON DATABASE notification_test TO epm_admin;
    GRANT ALL PRIVILEGES ON DATABASE keycloak_db      TO epm_admin;

EOSQL

echo "✅ Databases created: auth_db, user_db, project_db, task_db, ai_db, notification_db, keycloak_db"
