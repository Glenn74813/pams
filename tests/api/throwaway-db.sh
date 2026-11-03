#!/bin/bash
# Makes disposable copies of the PAMS databases for the API suites, which delete data as they run.
#   PostgreSQL: a docker container "pams-pgtest" on 127.0.0.1:55432, loaded from the local "inventory" database.
#   SQL Server: database "pams_test" restored from "pams" inside the "pams-mssql" container.
#   tests/api/throwaway-db.sh up | down
set -euo pipefail
cd "$(dirname "$0")"
ENV=../../backend-java/.env
PW=$(grep ^DB_PASSWORD "$ENV" | cut -d= -f2-)
SA=$(grep ^MSSQL_SA_PASSWORD "$ENV" | cut -d= -f2-)
MS=(docker exec pams-mssql /opt/mssql-tools18/bin/sqlcmd -C -S localhost -U sa -P "$SA" -b)
case "${1:-up}" in
up)
  docker rm -f pams-pgtest >/dev/null 2>&1 || true
  docker run -d --name pams-pgtest -e POSTGRES_USER=inventory -e POSTGRES_PASSWORD="$PW" -e POSTGRES_DB=inventory -p 127.0.0.1:55432:5432 postgres:16 >/dev/null
  until PGPASSWORD=$PW psql -h 127.0.0.1 -p 55432 -U inventory -d inventory -Atc 'select 1' >/dev/null 2>&1; do sleep 1; done
  PGPASSWORD=$PW pg_dump -h 127.0.0.1 -p 5432 -U inventory -d inventory --no-owner --no-privileges \
    | grep -v '^SET transaction_timeout' | grep -v '^\\restrict\|^\\unrestrict' \
    | PGPASSWORD=$PW psql -h 127.0.0.1 -p 55432 -U inventory -d inventory -q >/dev/null
  "${MS[@]}" -Q "BACKUP DATABASE pams TO DISK='/var/opt/mssql/data/pams_copy.bak' WITH INIT, COPY_ONLY" >/dev/null
  "${MS[@]}" -Q "IF DB_ID('pams_test') IS NOT NULL BEGIN ALTER DATABASE pams_test SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE pams_test; END;
    RESTORE DATABASE pams_test FROM DISK='/var/opt/mssql/data/pams_copy.bak' WITH MOVE 'pams' TO '/var/opt/mssql/data/pams_test.mdf', MOVE 'pams_log' TO '/var/opt/mssql/data/pams_test.ldf'" >/dev/null
  echo "Copies ready. Start the backends on them (see README.md), then run the suites."
  ;;
down)
  docker rm -f pams-pgtest >/dev/null 2>&1 || true
  "${MS[@]}" -Q "IF DB_ID('pams_test') IS NOT NULL BEGIN ALTER DATABASE pams_test SET SINGLE_USER WITH ROLLBACK IMMEDIATE; DROP DATABASE pams_test; END" >/dev/null
  docker exec -u 0 pams-mssql rm -f /var/opt/mssql/data/pams_copy.bak
  echo "Copies removed."
  ;;
esac
