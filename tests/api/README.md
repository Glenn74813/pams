# PAMS API test suites

Python scripts that drive a running PAMS backend through its API and check the results, on PostgreSQL or SQL
Server. Each suite prints one `PASS` or `FAIL` line per check.

**The suites delete data.** Before each suite, `run.sh` empties every table except roles, departments, settings and
the Super Admin account. Run them only on the disposable copies described below. `run.sh` refuses the usual
ports (5432, 8095, 8096) and the SQL Server database `pams`.

## Requirements

- Python 3, `psql`, `pdftotext` (poppler-utils), Docker.
- The `pams-mssql` container for SQL Server, and `backend-java/.env` with `DB_PASSWORD` and `MSSQL_SA_PASSWORD`.
- The backend compiled: `cd backend-java && ./mvnw -DskipTests compile`.

## Running

1. Make the disposable copies: `tests/api/throwaway-db.sh up`. This creates a PostgreSQL container on port 55432,
   loaded from the local `inventory` database, and a SQL Server database `pams_test` restored from `pams`.
2. Start a backend on each copy, with the login limit raised so the suites can sign in as often as they need, and port 18765 on this machine allowed so `integration_test` can reach the fake system it runs there:

   ```bash
   cd backend-java
   export PAMS_SECURITY_LOGIN_REQUESTS_PER_MINUTE=100000 PAMS_INTEGRATIONS_ALLOWED_HOSTS=127.0.0.1:18765
   DB_URL=jdbc:postgresql://127.0.0.1:55432/inventory SERVER_PORT=8097 PAMS_STORAGE_DIR=/tmp/pams-test-pg ./mvnw -q spring-boot:run
   ```

   ```bash
   cd backend-java
   export PAMS_SECURITY_LOGIN_REQUESTS_PER_MINUTE=100000 PAMS_INTEGRATIONS_ALLOWED_HOSTS=127.0.0.1:18765
   MSSQL_URL="jdbc:sqlserver://127.0.0.1:14330;databaseName=pams_test;encrypt=true;trustServerCertificate=true;sendTimeAsDatetime=false" \
     SPRING_PROFILES_ACTIVE=sqlserver SERVER_PORT=18099 PAMS_STORAGE_DIR=/tmp/pams-test-mssql ./mvnw -q spring-boot:run
   ```

3. Run the suites:

   ```bash
   PAMS_API=http://127.0.0.1:8097/api tests/api/run.sh
   PAMS_DB=mssql PAMS_API=http://127.0.0.1:18099/api tests/api/run.sh
   ```

   Name suites to run only those, e.g. `tests/api/run.sh rfq_test bid_test`. Each suite's output goes to
   `tests/api/out/`. The script exits non-zero if any check fails.
4. Remove the copies: `tests/api/throwaway-db.sh down`.

## Suites

| Suite | Covers |
|---|---|
| `rfq_test` | RFQs, quotations, awards, NOA and NTP, the BAC |
| `bid_test` | Competitive Bidding: invitation, opening, evaluation, post-qualification, award |
| `po_test` | Purchase orders from awards, IARs and deliveries |
| `con_test` | Contracts, amendments, securities, liquidated damages |
| `sup_test` | The supplier register, documents, eligibility, sanctions |
| `ps_test` | PS-DBM requests, CNAS, delivery |
| `proc_test` | PPMP, APP and purchase requests |
| `iss_test` | Requests and issuance, stock cards |
| `asset_test`, `asm_test` | Assets, transfers, assembly |
| `fix_test`, `fix2_test` | Checks added for specific issues |
| `tor_test`, `period_test` | TOR reports and their Excel, CSV and PDF output |
| `gaps_test` | GPPB Circular 06-2026 documents, sealed prices, failed LSB and Dialogue, partial PS stock, Unsolicited Offers |
| `cnas_test` | Lines released from PS-DBM, with no CNAS time limit |
| `integration_test` | Settings → Integrations against a fake payment system: refused addresses, OAuth tokens, encrypted keys, failures, who sees what |
| `alert_test` | Scheduled-job failure alerts; needs a second backend whose backup fails (`PAMS_API_FAILING_BACKUP`), so it is not in the default run |

## Fixtures

`sql/fixtures_*.sql` adds a trigger, on the disposable copy only, that gives suppliers named `ZZ …` the documents
competitive selection and the common modes need, and marks them verified. The older suites register suppliers by name
and expect them to be awardable. A few names are left out on purpose: `ZZ Office…`, `ZZ Paper…`, `ZZ Black…`, `ZZ Sup…`
and `ZZ Art…`, which the supplier and documents checks set up themselves.

`pams_db.py` runs the raw SQL the suites use on either database, translating the few PostgreSQL forms they need.
