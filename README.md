# market-agri-worker

Transversal **notifications worker** (ADR-016) of the **Marketplace Agrícola Huila** distributed
system: Java 21, Spring Boot 3.5, hexagonal architecture in three Maven modules (Annex D). It turns
domain events into e-mails — one consumer per event (norm 5.7) — and exposes no business
interface: only `GET /health` (norm 5.7.1). It is never routed by the API Gateway.

## Modules

```
worker-core/      domain/model, application/port/{in,out}, application/usecase — plain Java, no framework
worker-adapters/  adapter/in/http (liveness) and, with the consumer, adapter/in/messaging and the outbound adapters
worker-app/       composition root: entry point, wiring and every limit (application.yml)
db/               the worker schema with Liquibase, as a -db repository (ADR-013): see db/README.md
deploy/           Dockerfile and compose.yml (service and worker-db-migrate), included by market-agri-infra
```

`worker-core` declares no framework: a Spring or JDBC type there does not compile. The service
connects as `worker_app` and never migrates: `worker-db-migrate` does it, from infra.

## What the scaffold already does

| Rule | Where |
|---|---|
| `GET /health` → `200 {"status":"UP"}` (liveness, E-60) | `adapter/in/http/HealthController` |

## Run locally

```bash
./mvnw -B verify                      # build, unit and HTTP tests (Windows: mvnw.cmd)
java -jar worker-app/target/worker-app-0.0.1-SNAPSHOT.jar
curl -i http://localhost:8080/health
```

## Branching

`develop` ← `feat/…`, `fix/…`, `chore/…` (squash); `qa` ← `qa-<SUBTASK-ID>` (rebase, re-applied
with `git cherry-pick -x`); `main` ← `release.2.Y.Z` with the teacher's approval. Full policy:
`market-agri-docs/00-governance/git-conventions.md`.
