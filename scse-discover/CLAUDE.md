这是一个用于获取智能合约数据的数据服务，使用fastAPI构建

服务从nacos读取配置文件，配置文件的内容为：

# Dune API
dune:
  queries:
    - query_name: top100
      query_id: 6922033
    - query_name: get_contract_addresses
      query_id: 6915930

# 定时任务配置（Cron 表达式）
schedule:
  cron: "0 */6 * * *"  # 每6小时执行一次

# 回调配置
callback:
  url: "http://localhost:18080/api/contracts/process"

密钥仅通过环境变量提供，不写入 Nacos 或文档：

```dotenv
DUNE_API_KEY=replace_with_dune_api_key
ETHERSCAN_API_KEY=replace_with_key_1,replace_with_key_2
```

该服务设置了定时任务，也可以手动触发任务
当任务触发时按顺序执行以下步骤：
1.访问Dune，执行多个查询语句，并将两次查询结果得到的合约地址分别输出为query_name_timestamp.txt，查询配置读取配置文件，txt应当每行一个合约地址，过滤None
2.访问etherscan v2 api，读取刚刚生成的查询结果文本文件，通过地址获取合约的详细信息，包括address、abi、source_code、contract_name、bytecode，其中bytecode可能为空，但这不影响业务系统，所以没有关系。重点在于访问etherscan的方式，etherscan对免费用户限制qps为3，因此准备多个由环境变量注入的令牌，访问采用多令牌轮询的方式，每个令牌限制3qps，并采用线程池和连接池访问，将最终得到的数据以parquet文件存储，文件名query_name_info_timestamp.parquet
3.callback通知spring处理这些数据，不过目前还处于调试本项目的阶段，这里不用管
