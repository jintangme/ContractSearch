# Contract Discovery Service Architecture

`scse-discover` is a FastAPI service that populates the ContractSearch contract
repository with real on-chain contracts. It is an offline ingestion utility and
is not part of the online request path.

## Configuration

Runtime settings are read from Nacos. Query definitions, the schedule and the
callback URL live in the `scse-discover.yaml` Nacos configuration:

```yaml
dune:
  queries:
    - query_name: top100
      query_id: 6922033
    - query_name: get_contract_addresses
      query_id: 6915930

schedule:
  cron: "0 */6 * * *"

callback:
  url: "http://localhost:18080/api/contracts/process"
```

API keys are never stored in Nacos or in the repository. They are injected as
process environment variables (see `.env.example`):

```dotenv
DUNE_API_KEY=replace_with_dune_api_key
ETHERSCAN_API_KEY=replace_with_key_1,replace_with_key_2
```

## Pipeline

A run can be scheduled by the cron expression or triggered manually. It executes
three ordered stages:

1. **Dune discovery** - runs each configured Dune query and writes the returned
   contract addresses to `data/{query_name}_{timestamp}.txt`, one address per
   line, filtering out empty values.
2. **Etherscan enrichment** - reads each address file and fetches contract
   details (`address`, `abi`, `source_code`, `contract_name`, `bytecode`) from
   the Etherscan v2 API. Because the free tier is limited to 3 QPS per key, the
   service rotates multiple environment-provided keys, caps each key at 3 QPS and
   uses a thread pool plus connection pool. Results are stored as
   `data/{query_name}_info_{timestamp}.parquet`. A missing `bytecode` is
   tolerated by the rest of the platform.
3. **Callback** - posts the generated batch to the Spring service
   (`/api/contracts/process`) for persistence and embedding.

Generated `data/` output and Nacos snapshot data are excluded from version
control.
