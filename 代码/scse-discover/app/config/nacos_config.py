import nacos
import yaml
import logging
from typing import Callable, Dict, Any

logger = logging.getLogger(__name__)

class NacosConfigManager:
    def __init__(self, server_address: str, namespace: str = "public"):
        self.client = nacos.NacosClient(server_address, namespace=namespace)
        self.config_cache: Dict[str, Any] = {}
        self.callbacks: Dict[str, Callable] = {}

    def get_config(self, data_id: str, group: str = "DEFAULT_GROUP") -> Dict[str, Any]:
        """获取配置"""
        try:
            content = self.client.get_config(data_id, group)
            config = yaml.safe_load(content)
            self.config_cache[data_id] = config
            return config
        except Exception as e:
            logger.error(f"Failed to get config: {e}")
            return self.config_cache.get(data_id, {})

    def add_config_listener(self, data_id: str, group: str, callback: Callable):
        """监听配置变更"""
        def config_callback(args):
            try:
                content = args.get("content", "")
                config = yaml.safe_load(content)
                self.config_cache[data_id] = config
                logger.info(f"Config updated: {data_id}")
                callback(config)
            except Exception as e:
                logger.error(f"Config callback error: {e}")

        self.client.add_config_watcher(data_id, group, config_callback)
        logger.info(f"Listening to config: {data_id}")
