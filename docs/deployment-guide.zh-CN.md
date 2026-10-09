# 部署与演示说明

当前推荐入口是[基础演示运行指南](development/quickstart.md)。
它从公开源码、完整基础表结构与三份自写示例合约启动，不依赖旧的私有备份、
比赛压缩包或生产账号。

## 本机演示

```bash
python3 scripts/configure-demo.py
docker compose up --build -d
python3 scripts/smoke-demo.py
```

打开 http://localhost:8080。所有映射端口仅绑定本机回环地址。
完整 Agent 模式见[配置指南](development/agent-mode.md)。

## 公网部署前需要完成的工作

当前 Compose 与 Nginx 文件用于单人本地演示，不能直接通过修改监听地址
视为完成生产部署。对公网提供服务前，需要结合实际环境完成：

- TLS、应用访问控制、注册策略、请求频率和资源配额；
- 运行工具的用户隔离与资源限制；尤其不能公开未经隔离的 Lean 执行接口；
- 服务间访问控制和数据库网络隔离；
- 凭据管理、脱敏日志、数据保留与备份恢复；
- 实际模型调用成本和供应商配置；
- 发布镜像中第三方组件的许可证与声明核对。

导入回调仅允许可信离线采集服务直连主服务，需同时配置
`CONTRACT_IMPORT_TOKEN` 与 `CONTRACT_IMPORT_ROOT`，不能通过 Gateway 公开调用。
具体约定见[后端说明](development/backend.md)。

本文件替代旧的单机生产部署草稿。旧草稿依赖未公开的数据恢复点，
不再作为公开仓库的可复现启动说明。
