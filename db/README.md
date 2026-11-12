# worker — database (`db/`)

The worker remembers which notices it already sent in the schema `worker` of the single
PostgreSQL instance (ADR-013): the transversal services have no `-db` repository (norm 4.5.1), so
the migrations live in this folder, with the layout and the rules of a `-db` (Annex A, ADR-011).
The instance and the `worker_app` login belong to `market-agri-infra`.

```
changelog/changelog-master.yaml   Liquibase entry point
01_ddl/ … 04_tcl/                 schema, table, index, roles and grants
05_rollbacks/                     rollback SQL mirroring every forward change
tests/                            SQL checks of the schema and the table rules, run by db-ci (step 3b)
../deploy/compose.yml             worker-db-migrate, the runner infra includes
../.github/workflows/db-ci.yml    rebuild, isolation, table rules, full rollback, rebuild
```

| Table | Purpose | Main rules |
|---|---|---|
| `processed_event` | Notices already sent (ADR-013, ADR-016) | primary key `(event_id, recipient_id)`: at most one notice per event and recipient (FR-033, INV-008); written **after** the e-mail; nothing else is stored (ADR-008) |

`worker_writer` (granted to `worker_app`) reads, inserts and deletes — the delete is only for the
purge job, which removes rows older than the redelivery window in bounded batches (ADR-016
point 7, served by `idx_processed_event_processed_at`); no `UPDATE`. Migrate from
`market-agri-infra` (`run --rm worker-db-migrate`) before starting the service; the service never
migrates.
