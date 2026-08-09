# Nacos 配置说明

## 配置项：scse-discover.yaml

在 Nacos 控制台创建配置：
- Data ID: `scse-discover.yaml`
- Group: `DEFAULT_GROUP`
- 配置格式: `YAML`

## 配置内容

```yaml
# Dune 查询配置，每一项对应一次查询任务
# 查询结果地址列表保存为 data/{query_name}.txt
# 合约详情保存为 data/{query_name}.parquet
dune:
  queries:
    - query_name: top100
      query_id: 6921530
    - query_name: defi_contracts
      query_id: 1234567

# 定时任务配置（Cron 表达式）
schedule:
  cron: "0 */6 * * *"  # 每6小时执行一次

# 回调配置
callback:
  url: "http://localhost:18080/api/contracts/process"
```

## queries 字段说明

| 字段 | 说明 |
|------|------|
| `query_name` | 查询名称，用作输出文件名（`data/{query_name}.txt` / `.parquet`） |
| `query_id` | Dune 查询 ID |

## 密钥配置

密钥不得写入 Nacos。通过进程环境变量注入：

```dotenv
DUNE_API_KEY=replace_with_dune_api_key
ETHERSCAN_API_KEY=replace_with_key_1,replace_with_key_2
```

本地开发可复制 `.env.example` 为 `.env`；生产环境应由容器或部署平台的 Secret 机制注入。

## Cron 表达式说明

格式: `分 时 日 月 周`

示例：
- `0 */6 * * *` - 每6小时执行
- `0 0 * * *` - 每天凌晨执行
- `0 9 * * 1-5` - 工作日上午9点执行
- `*/30 * * * *` - 每30分钟执行

## 动态修改配置

在 Nacos 控制台修改配置后，Python 服务会自动：
1. 接收配置变更通知
2. 更新 SQL 查询语句
3. 重新调度定时任务
4. 无需重启服务
