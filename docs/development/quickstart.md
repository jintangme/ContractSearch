# Development quick start

## Basic demo: no model credentials

Use a new clone in a simple directory such as `ContractSearch/`. Prerequisites:
Docker Desktop/Engine with Compose v2, Python 3.10+, and registry network access.
The first build downloads Java/Node dependencies and container images.

```bash
python3 scripts/configure-demo.py
docker compose up --build -d
python3 scripts/smoke-demo.py
```

The app is at http://localhost:8080. Registration accepts a synthetic
`reviewer@example.com` address; no email is sent. Choose your own disposable
password. There are no built-in accounts or published passwords.

The smoke script waits for startup, registers a new synthetic reviewer, then
checks login, BM25 source search, detail, real compilation, and rejection of
public file import. It writes a small demo account into the dedicated demo
database and never prints credentials or calls a model.

## What starts

| Service | Access |
|---|---|
| Nginx/Vue | Loopback 8080 |
| PostgreSQL 18 / pg_search 0.23.0 | Loopback 15432; dedicated demo database/volume |
| Business service / Gateway | Docker network only |
| Contract runtime | Docker network; proxied through the local demo |
| Agent | Optional Compose profile; local internal access on 18082 |
| Lean / discovery / Nacos | Not part of the basic stack |

The database image is pinned by digest. Bootstrap creates the business schema,
then inserts three original demonstration contracts with synthetic `demo:`
identifiers. No embeddings, accounts, or production data are seeded.
Bootstrap runs only when the named database volume is first initialized.

```bash
docker compose ps
docker compose logs --tail=80 service gateway
docker compose down
```

Logs can contain user input and identifiers. Inspect them locally; do not upload
unreviewed logs. `down` retains data. Only if you intend to erase **this demo's**
data, run `docker compose down -v`.

## Host development

To use installed Java 17, Maven 3.9+, and Node 22 with the container database:

```bash
python3 scripts/configure-demo.py  # only if .env does not exist
docker compose up -d db
mvn -f backend/pom.xml package
npm --prefix contract-runtime-service ci
npm --prefix frontend ci
```

Run each service in a separate terminal, from the repository root:

```bash
python3 scripts/with-env.py java -jar backend/cloud-service/target/cloud-service-1.0-SNAPSHOT.jar
python3 scripts/with-env.py java -jar backend/cloud-gateway/target/cloud-gateway-1.0-SNAPSHOT.jar
python3 scripts/with-env.py node contract-runtime-service/src/index.js
```

Then prepare and start the frontend:

```bash
cp frontend/.env.example frontend/.env
npm --prefix frontend run dev
```

Open http://localhost:5173. Vite proxies `/api` to the local Gateway; the
frontend environment points at the local runtime. Root `.env` is loaded by
`with-env.py` for Java/Node processes; copying the file alone is not enough.
The helper parses literal values, never evaluates shell code or expands
`$(...)`. Environment files stay local.

## Agent and optional tools

Follow [Agent mode](agent-mode.md) to enable real provider calls and build the
demo knowledge corpus. Lean is an optional local tool; see its
[README](../../lean-runtime-service/README.md). Offline discovery has a separate
[Nacos/shared-directory setup](../../scse-discover/README.md).

## Troubleshooting

- **Database failed on first startup:** inspect the database logs. Do not restore
  a private dump into the public demo. Correct the bootstrap issue before
  deliberately recreating its disposable volume.
- **Empty results:** use code mode and `owner` against the included dataset.
  Semantic mode needs DashScope and appropriate embeddings.
- **Agent unavailable:** basic mode disables the Agent intentionally. Follow
  the complete configuration guide and rebuild the frontend when flags change.
- **Existing .env:** the generator preserves it. Compare variable names against
  `.env.example`; do not overwrite your existing secrets.
- **Docker BuildKit rejects a non-ASCII path:** clone/export the public source
  into a directory such as `ContractSearch` before building.
- **Large Monaco build chunk warning:** the editor is lazy-loaded; this warning
  alone does not mean the production build failed.
