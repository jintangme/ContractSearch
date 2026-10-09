# Discovery deployment

Follow the [README setup](README.md) first. This optional collector is deployed
separately from the public demo; do not expose its port to the internet.

1. Start Nacos at `NACOS_SERVER`; publish `scse-discover.yaml` / `DEFAULT_GROUP`
   using [this schema](NACOS_CONFIG.md). Use your Nacos installation's console
   address, not the ContractSearch gateway.
2. Provide `DUNE_API_KEY`, `ETHERSCAN_API_KEY`, and `CONTRACT_IMPORT_TOKEN` through
   a private environment file or secret store.
3. Choose a shared output directory. Set `DISCOVERY_DATA_DIR` for the collector
   and `CONTRACT_IMPORT_ROOT` for `cloud-service` to that same absolute path.
   Configure the identical import token on the backend.
4. Set `callback.url` to the internal backend, for example
   `http://127.0.0.1:18081/api/contracts/process` for processes on one machine.
   Separate containers use the backend's internal DNS name; `localhost` would
   refer to the collector itself. The public gateway denies this route.
5. Run one collector worker:

   ```bash
   uvicorn app.main:app --host 127.0.0.1 --port 18000
   ```

The Dockerfile listens on `18000` inside the container. Keep it on a private
network and mount the shared volume at the same absolute path in both containers.
`.dockerignore` excludes credentials, generated data, virtual environments and
local caches from the build context.

Verify `/health`, then trigger a batch only after credentials and query access
are ready. The asynchronous trigger can incur provider usage. Failed callbacks
leave Parquet files on disk for explicit retry.

Troubleshooting:

- Startup configuration error: check Nacos Data ID, group and required fields.
- Import `401`/`403`: check matching tokens; use the internal backend.
- Rejected path: check `CONTRACT_IMPORT_ROOT`, mount paths and `.parquet` suffix.
- Empty batch: check Dune access and Etherscan source availability.

Keep real `.env`, responses containing credentials, generated batches and Nacos
snapshots out of repository examples and screenshots.
