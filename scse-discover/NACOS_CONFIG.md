# Nacos configuration

Create YAML with Data ID `scse-discover.yaml`, group `DEFAULT_GROUP`, in the
namespace used by the service (default `public`).

```yaml
dune:
  queries:
    - query_name: top100
      query_id: 1234567 # Replace with an accessible Dune query ID.

schedule:
  cron: "0 */6 * * *" # minute hour day month weekday

callback:
  url: "http://127.0.0.1:18081/api/contracts/process"
```

`query_name` may contain only letters, digits, `_` and `-`; it forms part of the
output filename. `top100` maps to backend group `1`; other names map to `0`.
Queries must return the address field expected by `app/services/dune_service.py`.
`query_id` identifies a Dune query, not SQL executed by this service.

The schedule uses five-field cron syntax. `0 */6 * * *` runs every six hours and
`*/30 * * * *` every thirty minutes. Configuration changes update the query list,
callback and schedule without restarting the process.

Keep secrets outside Nacos: `DUNE_API_KEY`, `ETHERSCAN_API_KEY` (optionally a
comma-separated list), and `CONTRACT_IMPORT_TOKEN` are environment variables.
The callback reaches `cloud-service` directly on the private network. Its
`CONTRACT_IMPORT_ROOT` and the collector's `DISCOVERY_DATA_DIR` refer to the same
shared directory and absolute path; no file bytes are transferred by callback.
