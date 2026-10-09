# Discovery architecture

Discovery is an optional offline producer. Online search reads contracts already
stored by the backend and does not wait for this service.

```text
Nacos: query IDs + cron + callback URL
                  |
          scheduled/manual run
                  |
         Dune -> Etherscan
                  |
          shared Parquet file
                  |
 authenticated callback with file path
                  |
     cloud-service :18081 -> database
```

`app/main.py` loads Nacos configuration and starts APScheduler. `ContractFetchTask`
fetches addresses and enriches them with a thread pool; `etherscan_service.py`
limits requests per configured API key and retries selected provider/network
failures. Classification is heuristic. Output includes name, address, source,
ABI and optional bytecode.

Credentials are environment variables. The callback uses a separate
`CONTRACT_IMPORT_TOKEN`, shared with `cloud-service`; the backend also requires
`CONTRACT_IMPORT_ROOT` and resolves submitted paths inside that root.

The callback carries a path rather than contents. `DISCOVERY_DATA_DIR` and the
backend import root must identify the same shared directory, visible at the same
absolute path. The public gateway denies this import endpoint.

`/health` reports startup, not provider availability or successful ingestion.
`/trigger` submits background work without awaiting completion. Concurrent manual
triggers are not a durable queue; use one worker and avoid overlapping manual and
scheduled runs. There is no automatic callback retry or public API authentication.
