# Agent mode

Agent mode adds model-directed retrieval, source inspection and contract-editing
tools to the [local demo](quickstart.md). It requires a model provider account;
provider requests may incur charges.

## Configure and start

Edit the ignored root `.env`, retaining its generated internal tokens and
database credentials:

```dotenv
AGENT_ENABLED=true
AGENT_ROUTE_ENABLED=true
AGENT_MINI_LOOP_ENABLED=true
AGENT_KNOWLEDGE_ENABLED=true
AGENT_PERSISTENCE_ENABLED=true
AGENT_MODEL=glm-4.5-air
ZHIPU_BASE_URL=https://open.bigmodel.cn/api/paas/v4
ZHIPU_API_KEY=replace_in_private_environment
DASHSCOPE_API_KEY=replace_in_private_environment
```

The model endpoint supplies Agent decisions. An alternative endpoint must accept
the provider client's structured chat/tool format and `thinking` parameter; its
URL and key still use the `ZHIPU_*` variables. DashScope supplies description
semantic search. The public catalog has no precomputed embeddings, so semantic
coverage requires a matching ingestion process; code BM25 and knowledge text
search are separate channels.

From the repository root:

```bash
docker compose --profile agent up --build -d
curl --fail http://127.0.0.1:18082/actuator/health
```

Wait for the Agent health response to report `"status":"UP"`. If startup fails,
inspect `docker compose --profile agent logs agent`. Compose applies the Agent
migrations to its dedicated demo database and configures corpus
`contractsearch / demo-v1`. Then seed knowledge from the three demo contracts:

```bash
python3 scripts/with-env.py python3 scripts/seed-demo-knowledge.py
```

Seeding checks source hashes and index integrity without making model calls.
If `DEMO_AGENT_PORT` was changed, use that port for the health URL and provide
its loopback URL to the seed command with `--base-url`.

To add pinned upstream references, review the [data licenses](../reference/data-sources.md)
and run:

```bash
python3 scripts/with-env.py python3 scripts/agentic-rag/sync-authoritative-knowledge.py --dry-run
python3 scripts/with-env.py python3 scripts/agentic-rag/sync-authoritative-knowledge.py
```

Reference synchronization fetches upstream content from the network and stores
it in the local database. It makes no model calls.

## Use the Agent

For example: “Find the demo contract whose write operation is restricted to its
owner, read the source, check whether it compiles, and explain the restriction
with sources.” The conversation displays tool activity and expandable evidence.

The Agent chooses its tools and may stop early. For a modification request, it
can freeze behavior checks, execute a candidate and inspect discrepancies.
Compilation, behavior checks and advisory model review have distinct meanings;
none is a general security guarantee. See the [service guide](../../backend/cloud-agent-service/README.md)
for the available tools and budget settings.

## Run services on the host

Load root configuration with `scripts/with-env.py`. Start the database, business
service and Solidity runtime before `cloud-agent-service`; follow the
[backend guide](backend.md) for database settings. Set
`AGENT_KNOWLEDGE_CORPUS_NAME=contractsearch` and
`AGENT_KNOWLEDGE_CORPUS_VERSION=demo-v1` when using the demo knowledge. In the
frontend environment, set `VITE_AGENT_ENABLED=true` and restart Vite or rebuild.
Server flags and authentication control access independently of that UI flag.

## Return to basic mode

Set `AGENT_ENABLED`, `AGENT_ROUTE_ENABLED`, `AGENT_MINI_LOOP_ENABLED`,
`AGENT_KNOWLEDGE_ENABLED` and `AGENT_PERSISTENCE_ENABLED` to `false`, then run:

```bash
docker compose up --build -d web gateway service
docker compose --profile agent stop agent
```

The database volume and saved records remain available for a later restart.
