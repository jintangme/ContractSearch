import pandas as pd
import os
import logging
import time
from concurrent.futures import ThreadPoolExecutor, as_completed

from app.services.dune_service import fetch_contracts_by_query
from app.services.etherscan_service import get_contract_info, init_api_key_pool, reset_stats, log_batch_stats
from app.services.categorizer import categorize_contract
from app.services.callback_service import CallbackNotifier

logger = logging.getLogger(__name__)

class ContractFetchTask:
    def __init__(self, config: dict):
        self.config = config
        self.dune_api_key = self._required_env("DUNE_API_KEY")
        self.notifier = CallbackNotifier(config["callback"]["url"])
        self._init_etherscan()

    @staticmethod
    def _required_env(name: str) -> str:
        value = os.getenv(name, "").strip()
        if not value:
            raise ValueError(f"Required environment variable is missing: {name}")
        return value

    def _init_etherscan(self):
        """从环境变量初始化 Etherscan API key pool，不接受 Nacos 明文密钥。"""
        keys = [
            key.strip()
            for key in self._required_env("ETHERSCAN_API_KEY").split(",")
            if key.strip()
        ]
        if not keys:
            raise ValueError("ETHERSCAN_API_KEY does not contain a usable key")
        init_api_key_pool(keys)

    def update_config(self, config: dict):
        """更新配置"""
        self.config = config
        self.notifier = CallbackNotifier(config["callback"]["url"])
        self.dune_api_key = self._required_env("DUNE_API_KEY")
        self._init_etherscan()
        logger.info("Config updated")

    def execute(self):
        """执行采集任务，遍历所有 dune 查询配置"""
        logger.info("Starting contract fetch task")
        start_time = time.time()

        queries = self.config.get("dune", {}).get("queries", [])
        if not queries:
            logger.warning("No dune queries configured")
            return

        logger.info(f"Found {len(queries)} dune queries to execute")

        for query_conf in queries:
            self._execute_single_query(query_conf)

        logger.info(f"All queries completed in {time.time() - start_time:.2f}s")

    def _execute_single_query(self, query_conf: dict):
        """执行单个 Dune 查询并处理结果"""
        query_name = query_conf.get("query_name")
        query_id = query_conf.get("query_id")
        api_key = self.dune_api_key

        logger.info(f"Executing query: {query_name} (id={query_id})")
        start_time = time.time()

        try:
            timestamp = time.strftime("%Y%m%d%H%M%S")

            # 1. 获取合约地址并保存为 txt
            addresses = fetch_contracts_by_query(api_key, int(query_id))
            logger.info(f"[{query_name}] Fetched {len(addresses)} addresses")

            if not addresses:
                logger.warning(f"[{query_name}] No valid addresses, skipping")
                return

            os.makedirs("data", exist_ok=True)
            address_file = f"data/{query_name}_{timestamp}.txt"
            with open(address_file, "w") as f:
                for addr in addresses:
                    f.write(f"{addr}\n")
            logger.info(f"[{query_name}] Saved addresses to {address_file}")

            # 2. 重置统计并获取合约详细信息
            reset_stats()
            contracts_data = []
            with ThreadPoolExecutor(max_workers=10) as executor:
                futures = {executor.submit(self._fetch_contract, addr, i+1): addr for i, addr in enumerate(addresses)}
                for future in as_completed(futures):
                    result = future.result()
                    if result:
                        contracts_data.append(result)

            # 3. 记录统计
            duration = time.time() - start_time
            log_batch_stats(len(addresses), len(contracts_data), duration)

            # 4. 保存为 parquet
            df = pd.DataFrame(contracts_data)
            output_path = f"data/{query_name}_info_{timestamp}.parquet"
            df.to_parquet(output_path, engine="pyarrow", index=False)
            logger.info(f"[{query_name}] Saved {len(contracts_data)} contracts to {output_path}")

            # 5. 通知 Spring 服务（使用绝对路径）
            abs_output_path = os.path.abspath(output_path)
            if query_name == "top100":
                group_id = 1
            else:
                group_id = 0
            self.notifier.notify(abs_output_path, group_id)

        except Exception as e:
            logger.error(f"[{query_name}] Query failed: {e}")

    def _fetch_contract(self, addr: str, index: int = None):
        """获取单个合约信息"""
        info = get_contract_info(addr, index=index)
        if info:
            info["category"] = categorize_contract(
                info["contract_name"],
                info["source_code"],
                info["abi"]
            )
        return info
