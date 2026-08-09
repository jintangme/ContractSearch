# ContractSearch

ContractSearch is an AI-assisted smart-contract library built with Vue 3, Spring Cloud, PostgreSQL/ParadeDB, GLM and an Agentic RAG knowledge layer. It combines semantic/code search, reusable contract sources, Contract Lab, security precheck and optional Lean verification.

## Repository layout

```text
backend/                    Spring Cloud Maven reactor
frontend/                   Vue 3 + Vite application
scse-discover/              Contract discovery/import service
contract-runtime-service/   solc/ethers/Ganache runtime
lean-runtime-service/       Lean verification runtime
database/                   Local database seed (ignored by Git)
docs/agentic-rag/            Architecture, reports and runbooks
scripts/agentic-rag/         Controlled ingestion/evaluation scripts
plan.md                     Delivery plan and current stage status
```

## Local ports

| Component | Port |
|---|---:|
| Frontend | 5173 |
| Nacos | 8848 |
| Gateway | 18080 |
| Main service | 18081 |
| Agent service | 18082 |
| User service | 18083 |
| Auth service | 18084 |
| Contract runtime | 19090 |
| Lean runtime | 19100 |

## Development

- Frontend: run `npm run dev` from `frontend/`.
- Java services: run Maven from `backend/` or the required child module.
- Contract/Lean runtimes: run `npm start` from the corresponding runtime directory.
- Agent and Knowledge configuration: see `backend/cloud-agent-service/README.md` and `docs/agentic-rag/authoritative-knowledge-runbook.md`.

Secrets belong in local environment variables or ignored `.env` files. Database dumps, dependencies and build output are intentionally excluded from Git.
