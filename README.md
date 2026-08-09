# ContractSearch 2.0

ContractSearch 是面向智能合约检索、理解、复用与验证的 AI 原生平台。系统以 Vue 3 和 Spring Cloud 为业务基础，以 PostgreSQL/ParadeDB 保存约 1887 条合约及版本化知识库，并通过 `cloud-agent-service` 提供基于 GLM 的多轮 Agentic RAG：Agent 会根据问题决定是否检索知识库、搜索合约、读取源码、编译或执行只读安全预审，最后生成带可追溯来源的回答。

本文档同时是本项目的云端部署手册。默认部署目标为一台 Ubuntu 22.04/24.04 云服务器；其他 Linux 发行版只需替换软件安装和服务管理命令。

## 1. 功能与架构

- 合约库：约 1887 条 Solidity 合约、ABI、描述和向量数据。
- 检索：语义搜索、代码片段搜索、ParadeDB/PGVector 数据能力。
- AI 对话：统一的 Agentic RAG 入口，支持多轮检索、工具调用、过程展示和证据来源。
- Contract Lab：Solidity 编译、本地 Ganache 部署和函数调用。
- 智能预审：独立安全预审，同时可作为 Agent 的只读工具。
- Lean4：独立形式化验证边界，不由 Agent 自动执行。
- 用户数据：收藏、草稿、提交记录和 Agent 对话历史。

```text
Browser
  │ HTTPS
  ▼
Nginx :80/:443
  ├── /                 Vue static files
  ├── /api/*            Spring Cloud Gateway :18080
  ├── /runtime/*        contract-runtime-service :19090
  └── /lean/*           lean-runtime-service :19100
                              │
Gateway :18080 ───────────────┤
  ├── /api/agent/* ── Agent Service :18082
  └── /api/* ──────── Main Service :18081
                              │
                 PostgreSQL/ParadeDB :5432
                              │
                         Nacos :8848
```

生产环境只应公开 `22`、`80` 和 `443`。`5432`、`8848`、`18080-18084`、`19090` 和 `19100` 应限制为本机或内网访问。

## 2. 项目目录

```text
backend/
  cloud-gateway/          API 网关、JWT 校验和 Agent 内部令牌注入
  cloud-service/          合约、搜索、用户、收藏、预审等主业务
  cloud-agent-service/    Agent Loop、RAG、Tools、Evidence 和历史持久化
  cloud-auth-service/     预留的独立认证微服务，当前最小部署不需要启动
  cloud-user-service/     预留的独立用户微服务，当前最小部署不需要启动
frontend/                 Vue 3 + Vite
contract-runtime-service/ solc 0.8.35、ethers 6、Ganache
lean-runtime-service/     Lean4 检查服务
scse-discover/            离线合约发现/导入，不是在线运行依赖
database/                 基础合约库 SQL
backups/                  已验证的数据库恢复点
docs/agentic-rag/         架构、迁移、知识库和评测文档
scripts/agentic-rag/      知识同步、探针和评测脚本
plan.md                   Agentic RAG 实施记录
```

## 3. 端口与在线依赖

| 组件 | 默认端口 | 云端是否必须 | 对公网开放 |
|---|---:|---|---|
| Nginx | 80/443 | 是 | 是 |
| Nacos | 8848 | 是 | 否 |
| PostgreSQL/ParadeDB | 5432 | 是 | 否 |
| Spring Cloud Gateway | 18080 | 是 | 否 |
| cloud-service | 18081 | 是 | 否 |
| cloud-agent-service | 18082 | 是 | 否 |
| contract-runtime-service | 19090 | 是 | 否 |
| lean-runtime-service | 19100 | 使用 Lean 功能时 | 否 |
| Vue 开发服务器 | 5173 | 仅本地开发 | 否 |

## 4. 服务器要求

建议最低配置：

- 4 vCPU、8 GB RAM、60 GB SSD；演示环境可使用 4 GB RAM，但不要并行执行大量 Maven 构建。
- Java 17。
- Maven 3.9+。
- Node.js 20 LTS 与 npm 10+。
- PostgreSQL/ParadeDB，必须提供 `vector` 和 `pg_search` 扩展；恢复现有完整备份时还需兼容备份内声明的扩展。
- Nacos 2.x。
- Nginx。
- Lean 4.31.0（仅 Lean 功能需要）。
- Python 3.10+（仅权威知识同步脚本需要）。

部署前检查：

```bash
java -version
mvn -version
node -v
npm -v
nginx -v
psql --version
```

## 5. 上传与安装目录

以下命令假设创建专用系统用户，并把项目放在 `/opt/contractsearch/app`：

```bash
sudo useradd --system --create-home --shell /bin/bash contractsearch
sudo mkdir -p /opt/contractsearch/{app,config,logs}
sudo chown -R contractsearch:contractsearch /opt/contractsearch
sudo -u contractsearch unzip ContractSearch_项目源码_20260809.zip -d /opt/contractsearch/app
```

压缩包内还有一层 `计算机设计大赛2.0`，后续可将其内容移动到 `/opt/contractsearch/app`，或将本文命令中的项目路径改成实际路径。确认：

```bash
cd /opt/contractsearch/app
test -f README.md && test -d backend && test -d frontend
```

## 6. 数据库部署

### 6.1 创建角色和数据库

先安装与本地兼容的 PostgreSQL/ParadeDB。不要直接用不包含 `vector`/`pg_search` 的普通 PostgreSQL 镜像恢复数据。

```bash
sudo -u postgres psql
```

在 `psql` 中执行，密码需自行替换：

```sql
CREATE ROLE myuser LOGIN PASSWORD 'replace-with-strong-db-password';
CREATE DATABASE mydatabase OWNER myuser;
\c mydatabase
CREATE EXTENSION IF NOT EXISTS vector;
```

确认所需扩展可用：

```sql
SELECT name, default_version, installed_version
FROM pg_available_extensions
WHERE name IN ('vector', 'pg_search');
```

### 6.2 推荐恢复路径：包含 Agent 知识分块的备份

推荐使用：

```text
backups/contractsearch_pre_stage25_20260809_1528.dump
SHA-256: 7c05e94cdc9968cc586ebbdc796916000dc35bc6e57943ddcf11286fb05694ec
```

它包含基础合约库和 Delivery Stage 2 已生成的知识分块，可避免云端重新处理全部 1887 条合约。

```bash
sha256sum backups/contractsearch_pre_stage25_20260809_1528.dump
sudo -u postgres pg_restore \
  --exit-on-error \
  --dbname=mydatabase \
  backups/contractsearch_pre_stage25_20260809_1528.dump
```

如果对象所有者不匹配，先确保数据库中存在 `myuser`，不要直接使用 `--no-owner` 掩盖权限问题。恢复后：

```bash
sudo -u postgres psql -d mydatabase -c "GRANT CONNECT ON DATABASE mydatabase TO myuser;"
sudo -u postgres psql -d mydatabase -c "GRANT USAGE ON SCHEMA public TO myuser;"
sudo -u postgres psql -d mydatabase -c "GRANT SELECT,INSERT,UPDATE,DELETE ON ALL TABLES IN SCHEMA public TO myuser;"
sudo -u postgres psql -d mydatabase -c "GRANT USAGE,SELECT,UPDATE ON ALL SEQUENCES IN SCHEMA public TO myuser;"
```

### 6.3 备选恢复路径：基础 SQL

`database/contractsearch_seed.sql` 约 111 MB，只适合恢复基础合约库。它不包含阶段 2 的 Knowledge Layer 数据，恢复后还需执行 V1–V6 迁移并重新导入知识库。

```bash
sudo -u postgres psql --set ON_ERROR_STOP=on \
  --dbname=mydatabase \
  --file=database/contractsearch_seed.sql
```

### 6.4 Agent 数据库迁移

Agent 的 V1–V6 位于：

```text
backend/cloud-agent-service/src/main/resources/db/migration/
```

首次云端部署只启动一个 Agent 实例，并临时设置：

```text
AGENT_MIGRATIONS_ENABLED=true
```

Agent 健康检查通过且 Flyway 已执行后，将其改回 `false` 并重启。不要让多个实例同时承担首次迁移。

## 7. 生产环境变量

生成两个不同的随机秘密：

```bash
openssl rand -hex 32
openssl rand -hex 32
```

创建 `/opt/contractsearch/config/contractsearch.env`，权限必须为 `600`：

```bash
sudo install -o contractsearch -g contractsearch -m 600 /dev/null /opt/contractsearch/config/contractsearch.env
```

写入以下配置并替换所有 `CHANGE_ME`：

```dotenv
# Shared security
JWT_SECRET=CHANGE_ME_RANDOM_64_HEX
JWT_EXPIRATION_MS=86400000
AGENT_INTERNAL_TOKEN=CHANGE_ME_DIFFERENT_RANDOM_64_HEX

# AI providers
ZHIPU_API_KEY=CHANGE_ME
DASHSCOPE_API_KEY=CHANGE_ME
ZHIPU_BASE_URL=https://open.bigmodel.cn/api/paas/v4

# Main database
SPRING_DATASOURCE_URL=jdbc:postgresql://127.0.0.1:5432/mydatabase
POSTGRESQL_DB_USERNAME=myuser
POSTGRESQL_DB_PASSWORD=CHANGE_ME

# Nacos
NACOS_SERVER_ADDR=127.0.0.1:8848
SPRING_CLOUD_NACOS_SERVER_ADDR=127.0.0.1:8848

# Agent activation
AGENT_ENABLED=true
AGENT_ROUTE_ENABLED=true
AGENT_MINI_LOOP_ENABLED=true
AGENT_MODEL=glm-4.5-air
AGENT_THINKING_ENABLED=false
AGENT_SERVICE_PORT=18082

# Agent database, migrations and history
AGENT_DB_URL=jdbc:postgresql://127.0.0.1:5432/mydatabase
AGENT_DB_USERNAME=myuser
AGENT_DB_PASSWORD=CHANGE_ME
AGENT_MIGRATIONS_ENABLED=false
AGENT_PERSISTENCE_ENABLED=true
AGENT_PERSISTENCE_DB_URL=jdbc:postgresql://127.0.0.1:5432/mydatabase
AGENT_PERSISTENCE_DB_USERNAME=myuser
AGENT_PERSISTENCE_DB_PASSWORD=CHANGE_ME

# Knowledge Layer
AGENT_KNOWLEDGE_ENABLED=true
AGENT_KNOWLEDGE_DB_URL=jdbc:postgresql://127.0.0.1:5432/mydatabase
AGENT_KNOWLEDGE_DB_USERNAME=myuser
AGENT_KNOWLEDGE_DB_PASSWORD=CHANGE_ME
AGENT_KNOWLEDGE_CORPUS_NAME=contractsearch
AGENT_KNOWLEDGE_CORPUS_VERSION=delivery-stage2-v1

# Agent dependencies and bounded loop
AGENT_CONTRACT_SERVICE_URL=http://127.0.0.1:18081
AGENT_RUNTIME_SERVICE_URL=http://127.0.0.1:19090
AGENT_PROVIDER_TIMEOUT_MS=60000
AGENT_CONTRACT_TIMEOUT_MS=15000
AGENT_COMPILE_TIMEOUT_MS=45000
AGENT_MAX_STEPS=6
AGENT_MAX_TOOL_CALLS=5
AGENT_MAX_RETRIEVAL_ROUNDS=2

# Runtime services
PORT=19090
LEAN_BIN=/home/contractsearch/.elan/bin/lean
```

注意：

- Gateway 和 Agent Service 必须使用完全相同的 `AGENT_INTERNAL_TOKEN`，且长度至少 24 个字符。
- 不要把真实 `.env`、API Key、数据库密码提交到 Git 或放入公开压缩包。
- `glm-4.5-air` 是当前经过最小验证的 Agent 模型。更换模型前应重新验证结构化工具调用。
- `DASHSCOPE_API_KEY` 用于原有语义向量检索；`ZHIPU_API_KEY` 用于 GLM 和智能预审。

## 8. 构建项目

### 8.1 Java 服务

```bash
cd /opt/contractsearch/app/backend
mvn -DskipTests clean package
```

生成：

```text
cloud-service/target/cloud-service-1.0-SNAPSHOT.jar
cloud-gateway/target/cloud-gateway-1.0-SNAPSHOT.jar
cloud-agent-service/target/cloud-agent-service-1.0-SNAPSHOT.jar
```

`cloud-auth-service` 和 `cloud-user-service` 当前不是最小在线部署依赖，无需启动。

### 8.2 Runtime 服务

```bash
cd /opt/contractsearch/app/contract-runtime-service
npm ci --omit=dev

cd /opt/contractsearch/app/lean-runtime-service
npm ci --omit=dev
```

安装 Lean 4.31.0 后确认：

```bash
/home/contractsearch/.elan/bin/lean --version
```

### 8.3 Vue 前端

Vite 变量在构建时写入产物。创建 `frontend/.env.production`：

```dotenv
VITE_RUNTIME_API_BASE=/runtime
VITE_LEAN_API_BASE=/lean
```

构建：

```bash
cd /opt/contractsearch/app/frontend
npm ci
npm run build
```

静态文件位于 `frontend/dist`。

## 9. systemd 服务

下面给出推荐模板。所有服务都以 `contractsearch` 用户运行，并读取同一个受保护的环境文件。

### 9.1 cloud-service

`/etc/systemd/system/contractsearch-main.service`：

```ini
[Unit]
Description=ContractSearch Main Service
After=network-online.target nacos.service postgresql.service

[Service]
User=contractsearch
WorkingDirectory=/opt/contractsearch/app/backend/cloud-service
EnvironmentFile=/opt/contractsearch/config/contractsearch.env
ExecStart=/usr/bin/java -jar target/cloud-service-1.0-SNAPSHOT.jar
Restart=on-failure
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

### 9.2 contract runtime

`/etc/systemd/system/contractsearch-runtime.service`：

```ini
[Unit]
Description=ContractSearch Solidity Runtime
After=network-online.target

[Service]
User=contractsearch
WorkingDirectory=/opt/contractsearch/app/contract-runtime-service
Environment=PORT=19090
ExecStart=/usr/bin/node src/index.js
Restart=on-failure
RestartSec=3

[Install]
WantedBy=multi-user.target
```

### 9.3 Lean runtime

`/etc/systemd/system/contractsearch-lean.service`：

```ini
[Unit]
Description=ContractSearch Lean Runtime
After=network-online.target

[Service]
User=contractsearch
WorkingDirectory=/opt/contractsearch/app/lean-runtime-service
Environment=PORT=19100
Environment=LEAN_BIN=/home/contractsearch/.elan/bin/lean
ExecStart=/usr/bin/node src/index.js
Restart=on-failure
RestartSec=3

[Install]
WantedBy=multi-user.target
```

### 9.4 Agent Service

`/etc/systemd/system/contractsearch-agent.service`：

```ini
[Unit]
Description=ContractSearch Agentic RAG Service
After=network-online.target contractsearch-main.service contractsearch-runtime.service
Requires=contractsearch-main.service contractsearch-runtime.service

[Service]
User=contractsearch
WorkingDirectory=/opt/contractsearch/app/backend/cloud-agent-service
EnvironmentFile=/opt/contractsearch/config/contractsearch.env
ExecStart=/usr/bin/java -jar target/cloud-agent-service-1.0-SNAPSHOT.jar
Restart=on-failure
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

### 9.5 Gateway

`/etc/systemd/system/contractsearch-gateway.service`：

```ini
[Unit]
Description=ContractSearch API Gateway
After=network-online.target contractsearch-main.service contractsearch-agent.service
Requires=contractsearch-main.service contractsearch-agent.service

[Service]
User=contractsearch
WorkingDirectory=/opt/contractsearch/app/backend/cloud-gateway
EnvironmentFile=/opt/contractsearch/config/contractsearch.env
ExecStart=/usr/bin/java -jar target/cloud-gateway-1.0-SNAPSHOT.jar
Restart=on-failure
RestartSec=5
SuccessExitStatus=143

[Install]
WantedBy=multi-user.target
```

加载并启动：

```bash
sudo systemctl daemon-reload
sudo systemctl enable --now contractsearch-main
sudo systemctl enable --now contractsearch-runtime
sudo systemctl enable --now contractsearch-lean
sudo systemctl enable --now contractsearch-agent
sudo systemctl enable --now contractsearch-gateway
```

查看日志：

```bash
sudo journalctl -u contractsearch-main -f
sudo journalctl -u contractsearch-agent -f
sudo journalctl -u contractsearch-gateway -f
```

## 10. Nginx 配置

将域名 `contract.example.com` 替换为真实域名。创建 `/etc/nginx/sites-available/contractsearch`：

```nginx
server {
    listen 80;
    server_name contract.example.com;

    root /opt/contractsearch/app/frontend/dist;
    index index.html;

    client_max_body_size 300m;

    location /api/agent/ {
        proxy_pass http://127.0.0.1:18080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_buffering off;
        proxy_read_timeout 180s;
        proxy_send_timeout 180s;
    }

    location /api/ {
        proxy_pass http://127.0.0.1:18080;
        proxy_http_version 1.1;
        proxy_set_header Host $host;
        proxy_set_header X-Real-IP $remote_addr;
        proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
        proxy_set_header X-Forwarded-Proto $scheme;
        proxy_read_timeout 90s;
    }

    location /runtime/ {
        proxy_pass http://127.0.0.1:19090/;
        proxy_http_version 1.1;
        proxy_read_timeout 90s;
    }

    location /lean/ {
        proxy_pass http://127.0.0.1:19100/;
        proxy_http_version 1.1;
        proxy_read_timeout 90s;
    }

    location / {
        try_files $uri $uri/ /index.html;
    }
}
```

启用并验证：

```bash
sudo ln -s /etc/nginx/sites-available/contractsearch /etc/nginx/sites-enabled/contractsearch
sudo nginx -t
sudo systemctl reload nginx
```

配置 HTTPS 后，应将 HTTP 重定向到 HTTPS。证书可使用云厂商证书或 Certbot。不要在未启用 HTTPS 的公网环境长期传输登录令牌。

## 11. 首次迁移与知识库启用

### 11.1 执行 V1–V6

1. 确认数据库已有备份。
2. 将环境文件中的 `AGENT_MIGRATIONS_ENABLED` 临时改为 `true`。
3. 只启动一个 `contractsearch-agent` 实例。
4. 确认 `/actuator/health` 为 `UP`，检查日志无 Flyway 错误。
5. 将 `AGENT_MIGRATIONS_ENABLED` 改回 `false` 并重启 Agent。

### 11.2 权威知识包

推荐备份包含社区合约知识分块，但权威知识包仍应按固定 manifest 同步。同步需要服务器能够访问 manifest 中允许的 GitHub Raw 地址：

```bash
cd /opt/contractsearch/app
python3 scripts/agentic-rag/sync-authoritative-knowledge.py --dry-run

set -a
source /opt/contractsearch/config/contractsearch.env
set +a
python3 scripts/agentic-rag/sync-authoritative-knowledge.py \
  --endpoint http://127.0.0.1:18082/internal/agent/v1/knowledge/references \
  --batch-size 8
```

该操作对相同来源、commit 和内容哈希是幂等的。详细约束见 `docs/agentic-rag/authoritative-knowledge-runbook.md`。

## 12. 启动顺序

首次部署建议严格按以下顺序：

1. PostgreSQL/ParadeDB。
2. Nacos。
3. `cloud-service`。
4. `contract-runtime-service`。
5. `lean-runtime-service`。
6. `cloud-agent-service`。
7. `cloud-gateway`。
8. Nginx。

更新前端只需重新执行 `npm run build` 并刷新 Nginx 静态文件；不需要重启 Java 服务。

## 13. 验收检查

### 13.1 内部服务

```bash
curl http://127.0.0.1:18082/actuator/health
curl http://127.0.0.1:18081/api/contracts
curl http://127.0.0.1:18080/api/contracts
```

Agent 健康检查预期：

```json
{"status":"UP","groups":["liveness","readiness"]}
```

检查进程和监听端口：

```bash
systemctl --no-pager --full status contractsearch-main contractsearch-runtime contractsearch-lean contractsearch-agent contractsearch-gateway
ss -lntp | grep -E ':(18080|18081|18082|19090|19100)'
```

### 13.2 Web 验收

1. 注册或登录。
2. 打开搜索页并执行一次语义搜索。
3. 打开一个合约详情，确认源码和 ABI 可读取。
4. 点击“AI 合约解读”，确认进入 AI 对话并自动引用当前合约。
5. 发送问题，确认过程逐行显示且最终答案可展开来源。
6. 刷新页面，确认 Agent 历史仍存在。
7. 在 Contract Lab 编译一个简单 Solidity 合约。
8. 如部署 Lean，执行一个最小 Lean 检查。

只做一次真实 Agent 调用即可完成部署验收，避免不必要的模型消耗。

## 14. 安全要求

- 只让 Nginx 对公网开放，禁止公网直接访问 Gateway、Agent、runtime、Lean、Nacos 和数据库端口。
- Gateway 会移除客户端伪造的 `X-Agent-Internal-Token`，再注入受信任令牌；用户不得直接访问 Agent 端口。
- `AGENT_INTERNAL_TOKEN` 与 `JWT_SECRET` 必须不同，并定期轮换。
- 数据库账号不要使用 PostgreSQL 超级用户。
- `contract-runtime-service` 使用本地 Ganache 沙箱，但仍应限制 CPU、内存、请求体和网络访问。
- Lean 只验证提交的 Lean 源码；它不能证明 Solidity 业务语义与 Lean 模型天然一致。
- 不要把用户上传内容自动提升为权威知识；社区合约维持 `COMMUNITY` trust level。
- 生产环境关闭调试日志，不记录 API Key、JWT、源码全文或模型原始响应。
- 定期备份数据库，并在独立环境验证恢复过程。

## 15. 更新与回滚

### 更新

```bash
cd /opt/contractsearch/app
git pull
cd backend && mvn -DskipTests clean package
cd ../frontend && npm ci && npm run build
cd ../contract-runtime-service && npm ci --omit=dev
cd ../lean-runtime-service && npm ci --omit=dev
sudo systemctl restart contractsearch-main contractsearch-runtime contractsearch-lean contractsearch-agent contractsearch-gateway
sudo nginx -t && sudo systemctl reload nginx
```

### Agent 快速回滚

如果 Agent 出现异常：

1. 设置 `AGENT_ROUTE_ENABLED=false` 并重启 Gateway，隐藏 Agent 公共路由。
2. 设置 `AGENT_ENABLED=false` 并重启 Agent。
3. 保留 V1–V6 和 Knowledge 表，不要自动删除。
4. 回滚到上一版 JAR 后再逐项启用。

如果只是知识检索异常，设置 `AGENT_KNOWLEDGE_ENABLED=false` 并仅重启 Agent；原有合约搜索不受影响。

## 16. 常见问题

### Gateway 返回 503

- 检查 Nacos 是否启动。
- 检查 `cloud-service` 和 `cloud-agent-service` 是否已注册。
- 检查服务是否使用相同的 Nacos 地址和 namespace。

### Agent 返回 `Trusted gateway token required`

- 用户请求必须经过 Gateway，不要直接访问 `18082`。
- 检查 Gateway 和 Agent 的 `AGENT_INTERNAL_TOKEN` 是否完全相同。
- 修改令牌后需要同时重启 Gateway 和 Agent。

### AI 对话无法生成回答

- 检查 `ZHIPU_API_KEY` 是否存在于 Agent 的 systemd 环境中。
- 检查 `AGENT_ENABLED=true`、`AGENT_MINI_LOOP_ENABLED=true`。
- 检查 Agent 能访问 `18081` 和 `19090`。
- 查看 `journalctl -u contractsearch-agent -n 200 --no-pager`。

### 语义搜索失败

- 检查 `DASHSCOPE_API_KEY`。
- 检查数据库 `vector` 扩展与 embedding 列。
- 检查云服务器是否能访问 DashScope。

### 对话刷新后消失

- 检查 `AGENT_PERSISTENCE_ENABLED=true`。
- 检查 V6 已迁移。
- 检查 `AGENT_PERSISTENCE_DB_*` 账号权限。

### Contract Lab 在浏览器访问 localhost

- 必须在构建前设置 `VITE_RUNTIME_API_BASE=/runtime` 和 `VITE_LEAN_API_BASE=/lean`。
- Vite 环境变量是构建时变量，修改后必须重新执行 `npm run build`。

### Lean 检查失败

- 检查 `LEAN_BIN` 是绝对路径。
- 使用运行 systemd 服务的 `contractsearch` 用户执行 `$LEAN_BIN --version`。
- 当前目标版本为 Lean 4.31.0。

## 17. 本地开发端口

| Component | Port |
|---|---:|
| Frontend | 5173 |
| Nacos | 8848 |
| Gateway | 18080 |
| Main service | 18081 |
| Agent service | 18082 |
| Contract runtime | 19090 |
| Lean runtime | 19100 |

本地开发可分别使用 `mvn spring-boot:run`、`npm run dev` 启动。Agent 与知识库的详细说明见：

- `backend/cloud-agent-service/README.md`
- `docs/agentic-rag/migration-runbook.md`
- `docs/agentic-rag/knowledge-ingestion-runbook.md`
- `docs/agentic-rag/authoritative-knowledge-runbook.md`
- `docs/agentic-rag/openapi/agent-api-v1.yaml`
