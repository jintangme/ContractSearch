# Frontend development

The Vue 3 application keeps route views under `frontend/src/views/`. Reusable
business logic and feature-specific UI live under `frontend/src/features/`.
Shared controls, authentication stores and API clients remain in their own
top-level directories.

```text
frontend/src/
  api/
    index.js                     Business API client and authenticated Agent requests
    runtime.js                   Compile, deploy, call and Lean JSON transports
  config/
    features.js                  Basic/full mode and default route selection
  features/
    contract-lab/
      abi.js                     ABI input validation and runtime value formatting
      monaco.js                  Lazy editor loading, language and theme registration
      useLabLayout.js            Resizing, layout persistence and listener cleanup
      templates.js              Solidity examples and Lean starter source
      contract-lab.css           Workbench styles, imported with Vue scoped semantics
    conversation/
      EvidenceSources.vue       Expandable sources, line locations and source links
      evidence.js               Source identity, citation ordering and safe links
      tasks.js                  Request cancellation and bounded run polling
```

## Build modes

Copy `frontend/.env.example` to `frontend/.env` before starting Vite. These values
are public build configuration; **never put model keys or service secrets in a
`VITE_` variable**.

| Variable | Default | Behavior |
|---|---|---|
| `VITE_AGENT_ENABLED` | `false` | Basic mode opens `/search` with code search selected. `true` opens `/conversation`. |
| `VITE_RUNTIME_API_BASE` | `http://localhost:19090` | Contract runtime base URL; use the configured same-origin proxy for a deployed build. |
| `VITE_LEAN_API_BASE` | `http://localhost:19100` | Optional Lean runtime base URL; the service must be separately available. |

Basic mode still uses the real backend, database and sign-in flow. It does not
ship fabricated search results or Agent responses. Visiting `/conversation`
shows an explicit unavailable message and a search link without requesting Agent
sessions. AI-dependent actions elsewhere still require their backend providers.
Turning on the frontend flag does not configure or activate the backend Agent;
complete its server-side setup first. Restart Vite or rebuild after changing flags.

## Conversation lifecycle and evidence

Opening another history entry, starting a new conversation, or leaving the page
aborts the previous request and polling wait. Response guards also reject late
responses from transports that have already completed. This stops client updates;
it does **not** cancel a run already accepted by the server. Completed server runs
can subsequently be opened from history.

Evidence retains source references, file paths, line spans, chunk identities and
full excerpts. Only matching source content is grouped. Final-answer citations
are displayed first when the server supplies them. Five sources are initially
visible, and the user can expand the complete list. HTTP(S) references open in a
new tab; known contract references link to the local contract detail page.
Other source identifiers remain plain text.

## Validation

From `frontend/`:

```bash
npm ci
npm test
npm run build
```

Tests use Node's built-in runner and the Vue compiler/runtime already included
with Vue. They cover mode defaults, late-response cancellation, run termination
and timeout, evidence identity, source links, the actual source-list component's
expand/collapse behavior, runtime request contracts, and ABI argument parsing.
These checks do not contact model providers and are not a model-quality evaluation.

The Monaco editor remains a large, lazy-loaded chunk. Vite may report a chunk-size
warning for it; the main application does not eagerly load the editor.

For browser verification, use an isolated demo account and known sample contracts:

1. Sign in in basic mode and confirm that code search is the default.
2. Open a result, send its source to Contract Lab, compile, deploy to the sandbox,
   and call a read function.
3. Visit the disabled conversation page and confirm that no Agent request is made.
4. In a configured full-mode environment, run a real question, expand all sources,
   then test new conversation, history switching, and navigation during polling.

Capture screenshots from these real flows. Do not use production profile data,
private source, browser tokens, or fabricated Agent output in demonstration assets.

## Remaining boundaries

The workbench still coordinates the workspace, chat editing proposals, compilation
and sandbox execution in its route view. Further extraction should follow those
business boundaries with behavior checks; moving all state into one large
composable would only relocate the coupling. Profile and precheck views are also
candidates for later component extraction. This change preserves the existing
visual design.
