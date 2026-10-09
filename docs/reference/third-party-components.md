# Third-party component inventory

Checked against this repository's manifests, npm lockfiles and installed npm
package metadata on 2026-10-09. Python entries use the linked exact-version PyPI
metadata. A metadata label describes that component; it does not establish the
license of every bundled file or of ContractSearch as a whole.

## Frontend and Node services

Versions below are resolved lockfile versions, not the semver ranges in
`package.json`. For each npm entry, both `package.json` and the package's local
LICENSE file were checked. Sources link to the upstream project recorded in the
package metadata.

| Component | Version | Role | License evidence / upstream |
|---|---|---|---|
| Vue | 3.5.31 | Frontend UI | MIT; [Vue](https://github.com/vuejs/core) |
| Vue Router | 4.6.4 | Frontend routing | MIT; [Vue Router](https://github.com/vuejs/router) |
| Pinia | 2.3.1 | Frontend state | MIT; [Pinia](https://github.com/vuejs/pinia) |
| Axios | 1.14.0 | Frontend HTTP client | MIT; [Axios](https://github.com/axios/axios) |
| Monaco Editor | 0.55.1 | Code editor and worker | MIT, plus bundled third-party notices; [Monaco](https://github.com/microsoft/monaco-editor) |
| Vite | 5.4.21 | Build tool, dev dependency | MIT; [Vite](https://github.com/vitejs/vite) |
| @vitejs/plugin-vue | 5.2.4 | Vue build integration | MIT; [plugin](https://github.com/vitejs/vite-plugin-vue) |
| Express | 4.22.2 | Solidity/Lean HTTP services | MIT; [Express](https://github.com/expressjs/express) |
| cors | 2.8.6 | Runtime browser-origin handling | MIT; [cors](https://github.com/expressjs/cors) |
| ethers | 6.16.0 | Deployment, calls and ABI decoding | MIT; [ethers](https://github.com/ethers-io/ethers.js) |
| Ganache | 7.9.2 | Local in-memory EVM | MIT; [Ganache](https://github.com/trufflesuite/ganache) |
| solc-js wrapper (`solc`) | 0.8.35 | Compiler JS bindings; runtime dependency and frontend dev dependency | MIT wrapper; [solc-js](https://github.com/ethereum/solc-js) |
| Bundled Solidity compiler | 0.8.35+commit.47b9dedd.Emscripten.clang | Solidity compilation and AST extraction | Compiler's own output: mostly GPLv3, plus component-specific notices; [Solidity](https://github.com/argotorg/solidity) |

Manifest sources: [frontend](../../frontend/package.json),
[Contract Runtime](../../contract-runtime-service/package.json),
[Lean Runtime](../../lean-runtime-service/package.json).

### Compiler and editor details

`solc`'s npm `license: MIT` describes its JavaScript wrapper. The locally bundled
compiler's own `license()` output states that most of its code is GPLv3, with
separate notices for embedded dependencies. The exact output can be inspected
without compiling or downloading anything, after `npm ci` in Contract Runtime:

```bash
node -e 'const solc = require("solc"); console.log(solc.version()); console.log(solc.license())'
```

Do not infer the compiler's redistribution terms from the wrapper field. Remote
compiler versions loaded on demand have their own corresponding release files.
The frontend currently declares solc as a dev dependency; compilation in the
application is performed by the Node runtime rather than a browser solc import.

Monaco is imported as editor code and a worker. Its installed distribution
contains both `LICENSE` and `ThirdPartyNotices.txt`; the latter includes notices
for components such as TypeScript and other incorporated material. Preserve the
applicable original notices when producing a frontend bundle; an MIT table row
does not replace them.

## Offline discovery dependencies

Pinned versions come from [requirements.txt](../../scse-discover/requirements.txt).
The linked release pages provide the publisher's license metadata; no new
Python packages were installed during this inventory.

| Component | Version | Role | Verified metadata |
|---|---|---|---|
| FastAPI | 0.109.0 | Discovery HTTP API | [MIT](https://pypi.org/project/fastapi/0.109.0/) |
| Uvicorn | 0.27.0 | ASGI server | [BSD family](https://pypi.org/project/uvicorn/0.27.0/); precise variant still to verify in release LICENSE |
| requests | 2.31.0 | Provider HTTP and callback | [Apache-2.0](https://pypi.org/project/requests/2.31.0/) |
| pandas | 2.2.0 | Batch tabular processing | [BSD-3-Clause](https://pypi.org/project/pandas/2.2.0/) |
| pyarrow | 15.0.0 | Parquet output | [Apache-2.0](https://pypi.org/project/pyarrow/15.0.0/) |
| dune-client | 1.3.0 | Dune API client | [Apache-2.0](https://pypi.org/project/dune-client/1.3.0/) |
| pydantic | 2.5.3 | API data models | [MIT](https://pypi.org/project/pydantic/2.5.3/) |
| nacos-sdk-python | 0.1.11 | Nacos config client | Release-specific license not yet verified |
| APScheduler | 3.10.4 | Cron scheduling | [MIT](https://pypi.org/project/APScheduler/3.10.4/) |
| PyYAML | 6.0.1 | YAML config parsing | [MIT](https://pypi.org/project/PyYAML/6.0.1/) |
| python-dotenv | 1.0.1 | Local environment loading | [BSD-3-Clause](https://pypi.org/project/python-dotenv/1.0.1/) |

## Java backend and infrastructure

These are key directly declared/managing POM versions, not a complete resolved
Maven dependency tree. Release-specific license texts have **not** yet been
independently checked for this list; no license is inferred from a product name.

| Component | Declared version | Role |
|---|---|---|
| Spring Boot | 3.5.13 | Backend application framework |
| Spring Cloud | 2025.0.0 | Gateway and service integrations |
| Spring Cloud Alibaba | 2025.0.0.0 | Nacos integrations |
| MyBatis-Plus | 3.5.5 | Persistence mapper support |
| Flyway | 13.2.0 | Database migrations |
| PostgreSQL JDBC | 42.7.10 | Database driver |
| Lombok | 1.18.30 | Build-time Java code generation |
| springdoc | 2.3.0 | OpenAPI documentation |
| OpenAI Java SDK | 4.30.0 | Configured model-service client |
| Parquet Avro | 1.13.1 | Importing Parquet contract batches |
| Hadoop client | 3.3.4 | Parquet filesystem support |
| JJWT | 0.11.5 | JWT encoding and verification |

See [backend POM](../../backend/pom.xml) and
[main-service POM](../../backend/cloud-service/pom.xml). Node.js, Java, Python,
PostgreSQL/ParadeDB, Nacos and Nginx are separately installed or containerized
infrastructure. The chosen image/toolchain versions and their bundled notices
need to accompany an actual redistribution inventory. Lean is likewise an
optional external executable; its installed release is not vendored here.

## External services and knowledge sources

- Zhipu GLM supplies remote model responses; DashScope supplies embeddings.
  Service/model terms are distinct from HTTP client licenses. Model weights are
  not included in this repository.
- Dune and Etherscan provide data through authenticated APIs. Query access,
  quotas, source availability and use of retrieved data depend on the provider
  and individual source; a client-library license does not license all API data.
- The [knowledge manifest](../agentic-rag/knowledge-sources-v1.json) pins source
  URLs to commits and records declared source licenses. Its current labels are
  `CC0-1.0` for ERC/EIP material, `GPL-3.0` for Solidity docs, `MIT` for
  OpenZeppelin source, and `CC-BY-NC-SA-4.0` for OWASP material. These labels are
  manifest provenance, not an independent file-by-file licensing determination.
  Preserve original attribution and verify each exact upstream file before
  distributing a corpus, embedding source text in examples, or repackaging it.

## Authorship and maintenance

Package imports are dependency integrations. A copied or modified code snippet,
contract template, document or image instead needs a source/commit, original
notice and description of modifications. This inventory does not claim that all
third-party snippets or transitive dependencies have already been audited.

No project-wide license was chosen during this cleanup. The author still needs
to choose a license for project-owned code after distinguishing it from the
third-party components and data above. The root
[third-party notice](../../THIRD_PARTY_NOTICES.md) records that boundary.

When dependencies change, regenerate the resolved inventory, inspect the release
LICENSE/NOTICE texts, and update this page. When distributing generated frontend
assets or images, inventory the actual contents as well as declared dependencies.
