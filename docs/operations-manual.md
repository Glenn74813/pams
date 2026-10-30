# PAMS operations manual

For the administrators who keep PAMS running: the MIS unit, the system administrator and the property office's power users.
It assumes PAMS is installed as in the [deployment guide](deployment-windows.md): Windows Server, SQL Server, one Windows service, in `C:\PAMS`.
How to use each page is in the Help inside the app; the API is in the [API reference](api-reference.md).

## 1. What is running

| Part | Where | Notes |
|---|---|---|
| Application (API and web app) | Windows service `pams`, `C:\PAMS\pams-backend.jar`, `C:\PAMS\web` | Restarts itself after a crash (after 10 seconds, then 60) |
| Database | SQL Server, database `pams` | Tables are created and upgraded by PAMS when it starts |
| Settings and secrets | `C:\PAMS\.env` | Administrators and the service account only |
| TLS certificate | `C:\PAMS\certs\pams.pfx` | Note its expiry date in your calendar |
| Uploaded files | `C:\PAMS\storage\public` | Signatures, logos, documents, asset and item photos |
| Documents sent for signing | `C:\PAMS\storage\signing` | Served only after an access check |
| Database backups | `C:\PAMS\storage\backups` (or `PAMS_BACKUP_DIR`) | Daily, kept 7 days |
| Logs | `C:\PAMS\pams-service.out.log`, `.err.log` | Rolled at 10 MB, 10 files kept |

PAMS can also run on PostgreSQL. Where it matters, this manual says so.

## 2. Routine checks

**Every working day**

1. Open PAMS and sign in. This confirms the service, the database and the certificate together.
2. Check the notification bell for **Scheduled job failed**, then confirm last night's backup: a new `backup_*` file in the backups folder with a sensible size.

   > PAMS notifies everyone with `manage_system_settings` (and Super Admins) when a job fails, once a day per job. It **cannot** tell you when the server was off at 02:00 or the database was down, so still look for the file.
3. Read the dashboard's "Waiting on you".

**Every week**

1. Copy the backups and both storage folders off the server, if your backup system does not already.
2. Audit Trail → **Check integrity**: every entry should be intact (see [section 7](#7-audit-trail-and-logs)).
3. Log Trails: look for repeated failed logins from one address.
4. Check free disk space on the application and database servers.

**Every month**

1. After the 1st, confirm depreciation posted for last month (asset → Depreciation tab). It runs at 01:00 on the 1st.
2. Review Users & Roles: remove people who have left, and check who holds Super Admin and `manage_system_settings`.

**Every quarter**

1. Do a restore drill ([section 5.3](#53-restore-drill)).
2. Check the expiry dates of the TLS certificate, the SSO client secret and the trusted CAs.

## 3. Starting, stopping and checking the service

From an administrator PowerShell in `C:\PAMS`:

```powershell
.\pams-service.exe status
.\pams-service.exe stop
.\pams-service.exe start
.\pams-service.exe restart
```

Restarting takes about 15–30 seconds and signs nobody out, but anyone mid-save will see an error and must retry. Do it outside office hours where you can.

A monitoring tool can poll `GET https://<address>/api/auth/sso/config`. It needs no sign-in and answers 200 when the service is up, but **it does not touch the database**. To test the database as well, sign in with a monitoring account, or call `GET /api/auth/me` with its token.

## 4. Scheduled jobs

Times are Philippine time. Monitoring → **Scheduled alerts** (needs `manage_system_settings`) lists them and can run any of them on demand; the result shows on screen at that moment and is not kept. Scheduled runs write their result to the log. When any job fails, a **Scheduled job failed** notification goes to those same people, with the reason; clicking it opens Monitoring. Notifications appear inside PAMS only, not by email, so someone must sign in to see them.

| Job | When | What it does |
|---|---|---|
| `backup` | Daily 02:00 | Full database backup, keeps 7 days |
| `procurement_deadlines` | Daily 08:15 | Alerts on upcoming procurement deadlines |
| `contracts` | Daily 08:30 | Alerts on contract delivery and expiry |
| `supplier_documents` | Daily 08:40 | Alerts on expiring supplier documents |
| `low_stock` | Daily 08:45 | Alerts on items at or below their minimum |
| `expiring_stock` | Daily 08:50 | Alerts on lots near expiry |
| `procurement_schedule` | 08:20, first day of each quarter | Reminds about APP lines due this quarter |
| `depreciation` | 01:00, first day of each month | Posts monthly depreciation |
| `rsmi` | 23:59, last day of each month | Prepares the month's RSMI |
| `clean_notifications` | Sundays 03:00 | Deletes read notifications older than 30 days |

The server must be on and the service running at these times. A job missed because the server was down does not catch up by itself: run it on demand. Depreciation is safe to re-run, because it only posts months not yet posted.

## 5. Backup and restore

### 5.1 What to back up

All three belong together, because the database refers to the files:

1. The database backups.
2. `C:\PAMS\storage\public`.
3. `C:\PAMS\storage\signing`.

Also keep a copy of `C:\PAMS\.env` and the `.pfx` somewhere secure and separate. Without the same `PAMS_JWT_SECRET` (and `PAMS_AUDIT_SEAL_KEY`, if set), a restored system invalidates printed QR labels and shows the whole audit trail as altered.

The built-in backup keeps only 7 days on the same server. It protects against mistakes, not against losing the server. Copy it elsewhere, or use SQL Server Agent jobs with your backup system.

The SQL Server service account needs write access to the backup folder. Without it the job fails with "Operating system error 5 (Access is denied)".

### 5.2 Restoring on SQL Server

Stop the service first, so nothing writes while you restore.

```sql
-- See the logical file names in the backup
RESTORE FILELISTONLY FROM DISK = 'C:\PAMS\storage\backups\backup_2026-10-30_02-00-00.bak';

-- Check the file is readable
RESTORE VERIFYONLY FROM DISK = 'C:\PAMS\storage\backups\backup_2026-10-30_02-00-00.bak';

-- Replace the database (takes it from the file above)
ALTER DATABASE pams SET SINGLE_USER WITH ROLLBACK IMMEDIATE;
RESTORE DATABASE pams FROM DISK = 'C:\PAMS\storage\backups\backup_2026-10-30_02-00-00.bak' WITH REPLACE;
ALTER DATABASE pams SET MULTI_USER;
```

Then restore both storage folders from the same date, start the service, sign in, and run Audit Trail → **Check integrity**.

Anything entered after that backup is lost. Tell users what date the system went back to, so they can re-enter their work.

### 5.3 Restore drill

Restore into a **different** database name, so the live one is untouched:

```sql
RESTORE DATABASE pams_drill FROM DISK = 'C:\PAMS\storage\backups\backup_2026-10-30_02-00-00.bak'
  WITH MOVE 'pams' TO 'D:\Data\pams_drill.mdf', MOVE 'pams_log' TO 'D:\Data\pams_drill.ldf';

SELECT (SELECT COUNT(*) FROM pams.dbo.users) AS live_users, (SELECT COUNT(*) FROM pams_drill.dbo.users) AS drill_users;
DBCC CHECKDB (pams_drill) WITH NO_INFOMSGS;
DROP DATABASE pams_drill;
```

Use the names `RESTORE FILELISTONLY` shows, and any data folder with room. The counts will differ by whatever was entered since the backup. Write down the date and result.

### 5.4 PostgreSQL

The backup is a `pg_dump` custom-format `.dump`. `pg_dump` must be on the server's `PATH`. To restore, with the service stopped, into an empty database:

```bash
pg_restore -h localhost -U inventory -d inventory --no-owner backup_2026-10-30_02-00-00.dump
```

Check a file is readable with `pg_restore --list <file>`.

## 6. Users and access

| Task | Where |
|---|---|
| Add or edit a user | Users & Roles → Users |
| Someone left | **Deactivate** them: they cannot sign in, and their records and audit history stay. Start a clearance first if they hold property (Turnover & Clearance). |
| Locked out after 5 wrong passwords | **Unlock** on the user, or wait 15 minutes |
| Change what a role can do | Users & Roles → Roles |
| Password rules, idle sign-out | Settings → System (defaults: 8 characters, no expiry, sign out after 30 idle minutes) |
| Who signs the forms | Settings → Signatories |

After installing, sign in as `superadmin@inventory.com` and **change the default password at once**. Keep at least two accounts that can administer users, so one person leaving cannot lock you out. Keep duties separate: see Help → Separation of Duties.

**Single sign-on.** PAMS matches the provider's sign-in to an existing active account by email, and remembers the provider's ID afterwards. Create the user in PAMS first. Password login keeps working alongside SSO. If the client secret expires, sign-in by SSO fails until `PAMS_SSO_CLIENT_SECRET` is updated and the service restarted.

**Digital signatures.** Add the PNPKI root and intermediate certificates (or the agency's CA) under Digital Signatures → Trusted CAs. PAMS checks that a certificate leads to a trusted CA and is in date, but **does not check revocation lists**. If a signer's certificate is revoked, do not rely on signatures made after that date.

## 7. Audit trail and logs

- **Audit Trail** records every change: who, when, from where, old and new values. Entries cannot be edited or deleted, even by Super Admin, because the database refuses it. Export to CSV for auditors.
- **Check integrity** recalculates each entry's seal. `altered` above zero means an entry was changed outside PAMS, or the seal key changed. Treat it as an incident unless you changed the key yourself.
- **Log Trails** shows sign-ins, failed attempts and stock movements.
- **Application log** (`pams-service.out.log`) holds errors with stack traces. Users see only a short message, so look here first for "something went wrong".

## 8. Changing and upgrading

### 8.1 Upgrades

1. Tell users, and pick a quiet time.
2. Run the `backup` job on demand and copy the backup and storage folders aside. Keep the old `pams-backend.jar` and `web` folder too.
3. Stop the service. Replace `pams-backend.jar` and the contents of `C:\PAMS\web`. Start the service.
4. Database changes apply automatically on start. Watch the log for `Successfully applied` and `Started PamsBackendApplication`.
5. Sign in, open a few pages, and run Check integrity.

**Rolling back:** database changes have no undo. Stop the service, restore the database and files from step 2, and put back the old jar and web folder. Anything entered since the upgrade is lost.

### 8.2 Settings that are hard to change

| Setting | Effect of changing it |
|---|---|
| `PAMS_JWT_SECRET` | Everyone is signed out and **printed QR labels stop verifying**. Unless `PAMS_AUDIT_SEAL_KEY` is set, the audit trail also shows as altered. |
| `PAMS_AUDIT_SEAL_KEY` | Existing audit entries show as altered in Check integrity |
| App URL (Settings → System) | New labels use it. Printed labels keep the old address, so set it before printing in bulk. |
| A property number | The asset's old sticker stops working; reprint it |

To renew the TLS certificate, replace the `.pfx`, update `SERVER_SSL_KEY_STORE_PASSWORD` if it changed, and restart the service.

## 9. Troubleshooting

| What you see | Likely cause | What to do |
|---|---|---|
| Site unreachable | Service stopped, port blocked, or wrong address (`http` when only `https` is served) | `pams-service.exe status`; read the log; check the firewall rule |
| Service starts, then stops | Database unreachable, wrong `.env` value, or a failed database change | Read the end of the log; fix it, then start again |
| "Validate failed" or a Flyway error at start | A migration failed part-way, or someone changed the schema by hand | Do not edit `flyway_schema_history` by guesswork. Restore the backup from before the upgrade. |
| Everyone sees errors, but the site loads | Database down | Check the SQL Server service. PAMS reconnects by itself once it is back. |
| SQL Server stopped by itself and left a dump, with `ASSERT ... sosschedmon` in its log | The scheduler watchdog fired, usually because the server was starved of CPU or memory or was paused | Start SQL Server, run `DBCC CHECKDB (pams)`, then check what else was loading the server |
| Backup: "Access is denied" | The SQL Server service account cannot write to the folder | Grant it access, or set `PAMS_BACKUP_DIR` |
| Backup: "pg_dump exited" (PostgreSQL) | `pg_dump` missing or wrong version | Install matching client tools; see `backup.log` in the backups folder |
| Certificate warning in browsers | Expired or wrong certificate | Renew it; the name must match the address people use |
| QR scan says "not valid" | The tag or `PAMS_JWT_SECRET` changed since printing | Reprint the label |
| The camera does not open on a phone | The page is not on HTTPS, or the browser lacks the barcode feature (Safari on iPhone) | Use HTTPS and Chrome on Android, or a handheld scanner |
| SSO: "No PAMS account matches" | No user with that email | Add the user in PAMS |
| SSO: "could not be verified" | Wrong client ID or issuer, or a bad clock | Compare the settings with the provider; check the server's time |
| Signing: "not issued by a trusted certificate authority" | The CA or an intermediate is missing | Add it under Trusted CAs, or include the intermediates with the signature |
| Signing: "does not match" | The file changed after download, or the wrong algorithm was chosen | Download again, sign that exact file, and pick the matching algorithm |
| Audit: entries show as altered | Seal key changed, or tampering | See [section 10](#10-incident-response) |
| PDF of a form looks different from Excel | The PDF is drawn from the Excel file, using standard fonts | Report the form and record; meanwhile use the Excel file |

## 10. Incident response

Use this for attacks, suspected breaches and major failures. Agree the names before you need them:

| Role | Name | Contact |
|---|---|---|
| System administrator | | |
| IMISU / MIS lead | | |
| Data Protection Officer | | |
| Supplier support | | |

### Severity and response times

From the TOR's Service Level Agreement:

| Level | Meaning | Resolve within |
|---|---|---|
| 1 Critical | Service has stopped; includes successful attacks and hacking attempts | 4 hours |
| 2 High | Running, but performance is degraded | 1 working day |
| 3 Moderate | Running, but some functions do not work as agreed | 5 working days |
| 4 Low | Improvements | Next update |

### What to do

**1. Identify.** Signs include: Check integrity reports altered entries; many failed logins in Log Trails; changes in the Audit Trail nobody recognises; new administrators or role changes; unexplained files or processes on the server; a security tool's alert.

**2. Contain.** Choose what fits, and write down what you do and when:

- A stolen account: **Deactivate** the user. This takes effect on their next request.
- Several accounts or an unknown attacker: change `PAMS_JWT_SECRET` and restart to sign everyone out. This also breaks printed QR labels (see [section 8.2](#82-settings-that-are-hard-to-change)), so use it when the risk justifies that.
- An attack in progress: block the address at the firewall, or stop the service.
- Stolen secrets: replace the database password, `PAMS_SIGNING_PLATFORM_KEY` and `PAMS_SSO_CLIENT_SECRET`.

**3. Preserve evidence, before you fix anything.**

- Copy the application logs, export the Audit Trail to CSV, and save the relevant Log Trails pages (they have no export, so print them to PDF, or query `login_attempts` in the database).
- Take a fresh database backup under a new name, and keep it apart from the rotating 7 days.
- Note server times and what you have already changed. Do not restore over the only copy of the evidence.

**4. Investigate.** Use the Audit Trail (who, what, from which address), Log Trails, the application log, and the web server and firewall logs. Decide what was seen or changed, whose personal data was involved, and how they got in.

**5. Recover.** Close the way in first. Then, if data was damaged, restore the last good backup ([section 5.2](#52-restoring-on-sql-server)), reset affected passwords, run Check integrity, and watch closely for a few days.

**6. Report.** Tell the IMISU lead and the Data Protection Officer as soon as personal data may be involved. The DPO decides on notifying the National Privacy Commission and the people affected, under the Data Privacy Act and NPC Circular 16-03, which sets a 72-hour deadline from knowledge of a reportable breach. Do not wait for the investigation to finish before telling the DPO.

**7. Learn.** Within two weeks, write up what happened, the timeline, the cause and what will change. Fix the gap, and update this manual if it misled you.

## 11. Data care

- **Personal data held:** names, emails, offices, signature images, login records with IP addresses, and the names of accountable officers on forms.
- **Removed automatically:** read notifications after 30 days, and backups after 7 days on the server. Nothing else is deleted by itself. Set retention for copies held elsewhere.
- **Kept on purpose:** the audit trail cannot be deleted, and users with history can only be deactivated. Government records have their own retention rules, so check with the Records Officer before any bulk removal.
- **Demo data:** `backend-java/tools/seed_demo.py` is for test systems only. Never run it, or the test suites' wipe scripts, on a live system.
- **Supplier data at contract end:** the TOR requires any personal data the supplier processed to be deleted under NPC supervision, with a certificate of disposal. Include test systems and backups held by the supplier.
