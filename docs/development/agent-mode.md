# Agent mode

Agent mode uses real GLM requests and may incur provider charges. The basic demo
does not need it. An Agent result must come from a live run; do not replace it
with a fabricated demonstration response.

## Configuration

Start from a working [basic demo](quickstart.md). Edit only the ignored root
`.env`, keeping generated tokens and database credentials:

```dotenv
AGENT_ENABLED=true
AGENT_ROUTE_ENABLED=true
AGENT_MINI_LOOP_ENABLED=true
AGENT_KNOWLEDGE_ENABLED=true
AGENT_PERSISTENCE_ENABLED=true
AGENT_MIGRATIONS_ENABLED=true
ZHIPU_API_KEY=replace_in_private_environment
DASHSCOPE_API_KEY=replace_in_private_environment
```

GLM supplies Agent decisions and optional precheck. DashScope supplies the
description semantic-search channel. Missing catalog embeddings can reduce
semantic coverage; chunk text search and code BM25 remain separate channels.

```bash
docker compose --profile agent up --build -d
```

Detached startup returns before the Agent is ready. Cold startup can take time
while Java initializes and database migrations run. Check its health endpoint:

```bash
curl --fail http://127.0.0.1:18082/actuator/health
```

If the connection is refused or the check fails, wait a few seconds and retry.
Continue only after the response reports `"status":"UP"`; if it remains
unavailable, inspect `docker compose --profile agent logs agent` locally before
proceeding. Then seed the demo knowledge:

```bash
python3 scripts/with-env.py python3 scripts/seed-demo-knowledge.py
```

These commands use the default Agent port. If `DEMO_AGENT_PORT` was changed,
use that port in the health URL and pass the matching loopback URL to the
seeding script with `--base-url`.

The Compose Agent applies additive V1–V6 migrations to the dedicated demo
database. It uses `contractsearch / demo-v1`, not a private historical corpus.
The seeding script imports the three demo sources through the loopback internal
endpoint and runs integrity validation. It makes no model calls.

Optionally import the upstream reference pack after reviewing
[data licenses](../reference/data-sources.md):

```bash
python3 scripts/with-env.py python3 scripts/agentic-rag/sync-authoritative-knowledge.py --dry-run
python3 scripts/with-env.py python3 scripts/agentic-rag/sync-authoritative-knowledge.py
```

The reference sync fetches pinned upstream content from the network. Imported
data stays in your local database and is not added to Git.

## Host-run services

Load root configuration with `scripts/with-env.py` for all three Java services.
Start `cloud-agent-service` after the database and runtime. In
`frontend/.env`, set `VITE_AGENT_ENABLED=true`, then restart Vite or rebuild.
The frontend flag controls presentation; server-side flags and authentication
control access.

## One representative task

Ask: “Find the demo contract whose write operation is restricted to its owner,
read the source, check whether it compiles, and explain the restriction with
sources.”

Check that the UI shows actual tool activity, the final answer's references can
be expanded, and source/line metadata agrees with the contract. Compilation is
not a proof of safety. If no supported evidence is available, abstention is an
acceptable outcome.

Keep provider keys, tokens, database passwords, user prompts, and raw logs out
of screenshots and repository commits. Publish only a reviewed, explicitly
dated case study.

## Return to basic mode

Set the five enabled Agent/route/loop/knowledge/persistence flags to `false`,
rebuild the frontend with `docker compose up --build -d web gateway service`,
and stop the optional service with `docker compose --profile agent stop agent`.
Retain its database history unless you intend to delete the demo volume.
