# Contract Discovery Service

Optional offline ingestion service for ContractSearch. It discovers Ethereum
contract addresses through Dune queries, fetches source and ABI from Etherscan,
writes Parquet batches, and asks `cloud-service` to import them. It is not on the
online search request path and is not required for the sample demo.

## Prerequisites

- Python **3.11**, using the pinned [requirements](requirements.txt).
- Nacos with a valid `scse-discover.yaml` configuration.
- Dune and Etherscan credentials with access to the configured queries.
- `cloud-service` and a directory both processes can read at the **same absolute
  path**. The callback sends a file path, not an uploaded file.

## Configure and run

From this directory:

```bash
python3.11 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
cp .env.example .env
```

Fill in the local `.env`; it is ignored by Git. Set `DUNE_API_KEY`,
`ETHERSCAN_API_KEY`, and a generated `CONTRACT_IMPORT_TOKEN` of at least 24
characters. `NACOS_SERVER` defaults to `localhost:8848`.

Create the Nacos configuration described in [NACOS_CONFIG.md](NACOS_CONFIG.md).
It contains query IDs, cron schedule and callback URL, never credentials. Use
Dune queries you own or can access; example IDs are placeholders.

Choose an output directory with `DISCOVERY_DATA_DIR` (default `./data`). Configure
the backend with the same `CONTRACT_IMPORT_TOKEN` and set its
`CONTRACT_IMPORT_ROOT` to that directory's absolute path. The recommended callback
is `http://127.0.0.1:18081/api/contracts/process`, directly to `cloud-service`.
The public gateway rejects this import endpoint.

```bash
uvicorn app.main:app --host 127.0.0.1 --port 18000
```

The scheduler starts with the application. Use one worker to avoid duplicate
scheduled jobs. Stopping the process stops the scheduler.

## API

| Endpoint | Purpose |
|---|---|
| `GET /health` | Returns `{"status":"ok"}` after application startup |
| `POST /trigger` | Submits an asynchronous collection job; returns `{"status":"triggered"}` |
| `GET /docs` | FastAPI interactive API documentation |

```bash
curl http://127.0.0.1:18000/health
curl -X POST http://127.0.0.1:18000/trigger
```

`triggered` acknowledges submission, not successful collection or import. Inspect
local logs and the backend import result. The old `/get/contracts/address` and
`/get/contracts/info` endpoints are not implemented. `/trigger` has no public
authentication layer; keep this service local or on a restricted internal network.
A trigger can consume provider API quota.

## Data flow and boundaries

1. Dune returns addresses for each configured query.
2. Etherscan provides source, ABI and metadata; source availability varies and
   a missing `bytecode` field is tolerated.
3. Batches are written as `{query_name}_info_{timestamp}.parquet` beside address
   lists in `DISCOVERY_DATA_DIR`.
4. The callback sends `filePath`, `groupId` and `timestamp`, authenticated with
   `X-Contract-Import-Token`. It does not follow redirects.
5. `cloud-service` checks the token and restricts `.parquet` paths to
   `CONTRACT_IMPORT_ROOT` before import.

Containers need a shared volume mounted at the same path. Separate machines need
an explicitly shared filesystem or a different ingestion adapter; HTTP alone
cannot transfer the batch. Failed callbacks retain the local file; automatic
callback retry is not implemented.

Generated data, `.env`, caches and Nacos snapshots stay outside Git and container
build contexts. Fetched contract sources retain their original authors' licenses.
See [architecture](ARCHITECTURE.md), [deployment](DEPLOYMENT.md), and
[third-party components](../docs/reference/third-party-components.md).

## Tests

After installing the requirements:

```bash
python -m unittest discover -s tests
```

Tests use mocked requests and do not contact providers, Nacos or the database.
