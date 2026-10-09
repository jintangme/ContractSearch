# Third-party notices and acknowledgements

ContractSearch integrates open-source software and external services. Credit for
those components belongs to their authors. The project's application logic,
retrieval pipeline, Agent orchestration, evidence model and integration code are
separate from the underlying compiler, framework, editor and model services.

The [component inventory](docs/reference/third-party-components.md) records
versions, roles, sources and the license evidence checked for this repository.
It covers declared direct components; the package lockfiles describe the wider
dependency graph. It is not a replacement for licenses bundled with distributions.

## Software acknowledgements

- **Vue, Vue Router, Pinia, Axios, Vite and the Vue Vite plugin** provide the web
  application's framework, state, routing, transport and build tools.
- **Monaco Editor** provides the code editor. Its MIT license and bundled
  `ThirdPartyNotices.txt` include upstream notices that remain relevant when
  distributing the editor and workers in a frontend build.
- **Express and cors** provide the runtime HTTP interfaces.
- **ethers and Ganache** provide contract interaction and the in-memory EVM.
- **solc-js and Solidity** provide compiler bindings and the actual compiler.
  The npm wrapper is MIT; the bundled compiler reports that most of its code is
  GPLv3 and lists additional component notices. These are separate entries in
  the inventory; the npm metadata is not a blanket MIT label for the compiler.
- **FastAPI, Uvicorn and the Python libraries listed in the inventory** provide
  offline discovery, provider access, scheduling and Parquet processing.
- **Spring and the Java dependencies** provide the backend framework, persistence,
  HTTP clients and authentication infrastructure. Version declarations are in
  the Maven POM files; remaining release-specific license checks are identified
  in the inventory.

## External tools, models and source material

Lean is an optional, separately installed executable. GLM and DashScope are remote
model/embedding services; this repository does not include their model weights.
Dune and Etherscan are external data providers. API access and retrieved data have
their own terms and provenance, separate from client-library licenses.

Knowledge-source references record a source URL, commit and declared license in
[`knowledge-sources-v1.json`](docs/agentic-rag/knowledge-sources-v1.json). Fetched
standards, documentation and contract source retain their original notices.
In particular, a public repository URL or a verified on-chain source does not
turn that material into project-owned code. Preserve each source's attribution,
license and modification history when redistributing it.

## Distribution and project license

The repository keeps dependency manifests and lockfiles rather than vendoring
`node_modules`, compiler downloads or live contract datasets. When preparing a
frontend build, container image or other bundle, retain the LICENSE/NOTICE files
applicable to the actual included software, including transitive and bundled
components. A direct-dependency table alone is not a complete bundle inventory.

A repository-wide license for ContractSearch has not been selected in this cleanup.
Existing template and snippet SPDX markers, including MIT markers, are preserved;
they do not establish a license for the entire repository and their provenance
still needs review. The new database demo examples use `UNLICENSED`.
This notice does not grant or replace a project-wide license, and it does not
change any upstream component's terms.
