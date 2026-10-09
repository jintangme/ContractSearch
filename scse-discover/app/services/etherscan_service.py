import os
import requests
import time
import logging
import threading
import random

logger = logging.getLogger(__name__)

# Global retry statistics
retry_stats = {"total_requests": 0, "failed_requests": 0, "total_retries": 0}

# Global session with connection pool
session = requests.Session()
adapter = requests.adapters.HTTPAdapter(
    pool_connections=10,
    pool_maxsize=20,
    max_retries=0
)
session.mount('https://', adapter)
session.mount('http://', adapter)



class RateLimiter:
    """简单限流器：保证同一 key 的两次请求至少间隔 min_interval 秒"""
    def __init__(self, min_interval=0.35):
        self.min_interval = min_interval  # minimum seconds between requests
        self.last_request = time.time()
        self.lock = threading.Lock()

    def acquire(self):
        """Wait if necessary to maintain minimum interval"""
        with self.lock:
            now = time.time()
            elapsed = now - self.last_request
            if elapsed < self.min_interval:
                time.sleep(self.min_interval - elapsed)
            self.last_request = time.time()


class APIKeyPool:
    """API key 轮询池：按 key 轮询并执行单 key 限流"""
    def __init__(self, keys: list[str] = None):
        # 优先使用 Nacos 下发的 keys；为空时兼容旧环境变量
        if keys:
            self.keys = [k.strip() for k in keys if k.strip()]
            logger.info(f"Using API keys from config, count={len(self.keys)}")
        else:
            # 兼容旧的环境变量方式（ETHERSCAN_API_KEY=key1,key2）
            keys_str = os.getenv("ETHERSCAN_API_KEY", "")
            self.keys = [k.strip() for k in keys_str.split(",") if k.strip()]
            logger.info(f"Using API keys from env, count={len(self.keys)}")

        if not self.keys:
            raise ValueError("No Etherscan API keys provided")

        self.limiters = {key: RateLimiter(min_interval=0.35) for key in self.keys}
        self.invalid_keys = set()
        self.index = 0
        self.lock = threading.Lock()
        logger.info(f"Initialized with {len(self.keys)} API key(s)")

    def get_key(self):
        """Get next API key in round-robin fashion"""
        with self.lock:
            valid_keys = [k for k in self.keys if k not in self.invalid_keys]
            if not valid_keys:
                raise ValueError("No valid Etherscan API keys available")

            if self.index >= len(valid_keys):
                self.index = 0
            key = valid_keys[self.index]
            self.index = (self.index + 1) % len(valid_keys)
            return key

    def acquire(self, key):
        """Acquire rate limit for specific key"""
        self.limiters[key].acquire()

    def mark_invalid(self, key: str):
        """将无效 key 从轮询池中熔断移除"""
        with self.lock:
            self.invalid_keys.add(key)
            logger.error(
                "API key marked invalid and removed from rotation; "
                f"invalid={len(self.invalid_keys)}, valid={len(self.keys) - len(self.invalid_keys)}"
            )


# Global API key pool (lazy initialization)
api_key_pool = None

def init_api_key_pool(keys: list[str]):
    """从外部配置初始化 API key pool"""
    global api_key_pool
    api_key_pool = APIKeyPool(keys)

def _get_api_key_pool():
    """懒加载 API key 池：允许模块被导入时不立即依赖外部配置"""
    global api_key_pool
    if api_key_pool is None:
        logger.info("API key pool not initialized, fallback to lazy init from env")
        api_key_pool = APIKeyPool()
    return api_key_pool

def reset_stats():
    """重置统计数据"""
    global retry_stats
    retry_stats = {"total_requests": 0, "failed_requests": 0, "total_retries": 0}

def log_batch_stats(total_addresses: int, success_count: int, duration: float):
    """记录批次统计信息"""
    total_requests = retry_stats["total_requests"]
    failed_requests = retry_stats["failed_requests"]
    total_retries = retry_stats["total_retries"]

    retry_rate = (failed_requests / total_requests * 100) if total_requests > 0 else 0
    loss_rate = ((total_addresses - success_count) / total_addresses * 100) if total_addresses > 0 else 0

    logger.info("=" * 60)
    logger.info(f"Etherscan 批次统计:")
    logger.info(f"  总地址数: {total_addresses}")
    logger.info(f"  成功获取: {success_count}")
    logger.info(f"  数据丢失率: {loss_rate:.2f}%")
    logger.info(f"  总请求数: {total_requests}")
    logger.info(f"  需要重试: {failed_requests}")
    logger.info(f"  重试率: {retry_rate:.2f}%")
    logger.info(f"  总重试次数: {total_retries}")
    logger.info(f"  总耗时: {duration:.2f}s ({duration/60:.2f}min)")
    logger.info("=" * 60)

def get_contract_info(address, max_retries=3, index=None):
    """查询单个地址合约信息，内置限流、错误分类和重试策略"""
    url = "https://api.etherscan.io/v2/api"
    retry_stats["total_requests"] += 1
    index_str = f"#{index} " if index is not None else ""

    for attempt in range(max_retries):
        # 获取 API key 并按 key 限流
        pool = _get_api_key_pool()
        try:
            api_key = pool.get_key()
        except ValueError as e:
            logger.error(f"{index_str}No usable API key for {address}: {e}")
            return None
        pool.acquire(api_key)

        params = {
            "chainid": "1",
            "module": "contract",
            "action": "getsourcecode",
            "address": address,
            "apikey": api_key
        }

        try:
            response = session.get(url, params=params, timeout=10)
            data = response.json()

            if data["status"] == "1":
                result = data["result"][0]
                contract_name = result.get("ContractName", "")
                logger.info(f"✓ {index_str}[{address}] {contract_name}")
                return {
                    "address": address,
                    "contract_name": contract_name,
                    "source_code": result.get("SourceCode", ""),
                    "abi": result.get("ABI", ""),
                    "bytecode": result.get("Bytecode", "")
                }

            # 错误分类：不同错误采用不同策略（是否重试/是否熔断 key）
            msg = data.get('message', '')
            result_msg = data.get('result', '')
            result_msg_lower = str(result_msg).lower()

            # Invalid key errors should not be retried immediately.
            if "invalid api key" in result_msg_lower:
                pool.mark_invalid(api_key)
                logger.error(f"{index_str}Invalid API key for {address}, skip retries")
                return None

            if "too many invalid api key attempts" in result_msg_lower:
                logger.warning(
                    f"{index_str}Etherscan temporarily throttled invalid-key attempts for {address}, skip retries"
                )
                return None

            # Retry on rate limit or server errors
            if 'rate limit' in result_msg_lower:
                if attempt < max_retries - 1:
                    if attempt == 0:
                        retry_stats["failed_requests"] += 1
                    retry_stats["total_retries"] += 1
                    wait_time = (2 ** attempt) + random.uniform(0, 0.5)
                    logger.warning(
                        f"{index_str}Rate limit for {address}: {result_msg}, "
                        f"retrying in {wait_time:.2f}s (attempt {attempt+1}/{max_retries})"
                    )
                    time.sleep(wait_time)
                    continue

            logger.warning(f"API error for {address}: {msg} - {result_msg}")
            return None

        except Exception as e:
            # 网络抖动/超时等异常：指数退避 + 抖动，避免并发重试风暴
            if attempt < max_retries - 1:
                if attempt == 0:
                    retry_stats["failed_requests"] += 1
                retry_stats["total_retries"] += 1
                wait_time = (2 ** attempt) + random.uniform(0, 0.5)
                logger.warning(f"{index_str}Request failed for {address} ({type(e).__name__}), retrying in {wait_time:.2f}s")
                time.sleep(wait_time)
                continue
            logger.error(f"Request failed after retries for {address} ({type(e).__name__})")
            return None

    logger.error(f"Max retries reached for {address}")
    return None
