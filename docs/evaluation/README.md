# Validation

The repository includes automated checks for application behavior, tool
boundaries and the local demonstration. These checks do not require paid model
calls. Current CI results are available in [GitHub Actions](https://github.com/jintangme/ContractSearch/actions).

## Local checks

Run from the repository root after installing the dependencies described in the
[quickstart](../development/quickstart.md):

```bash
mvn -f backend/pom.xml test
npm --prefix frontend test
npm --prefix frontend run build
npm --prefix contract-runtime-service test
npm --prefix lean-runtime-service test
python3 -m unittest discover -s scripts/tests -v
python3 scripts/check-public-content.py
python3 scripts/check-doc-links.py
```

| Area | What is checked |
|---|---|
| Java services | Authentication, routing, application APIs, Agent actions, budgets and evidence handling |
| Frontend | User-facing component behavior and production compilation |
| Solidity runtime | Compilation, local execution, candidate scenarios and session handling |
| Lean wrapper | Request validation and result handling with a controlled executor |
| Repository scripts | Demo setup, public-file checks and documentation links |

Database migrations, live service adapters and the complete Docker workflow
need running dependencies. Follow the [backend guide](../development/backend.md)
and [Agent setup](../development/agent-mode.md), using a disposable database.
The Lean wrapper tests do not substitute for running an installed Lean toolchain.

## Interpreting runtime feedback

Agentic RAG records exact-source compilation separately from behavior checks.
Candidate checks execute an Agent-authored, frozen plan and report actual
scenario outcomes. Passing those checks supports only the declared scenarios;
it is not an independent benchmark score or a security audit. Optional model
review is advisory and does not create an execution receipt.

When reporting an application test, record the commit, input, dependency and
model versions, commands, and actual outcome. A comparative quality study also
needs independently specified tasks and scoring, consistent method budgets,
and retained failures. Local runtime feedback should not be presented as such
a study.
