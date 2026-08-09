import requests
import logging
from typing import Optional

logger = logging.getLogger(__name__)

class CallbackNotifier:
    def __init__(self, callback_url: str):
        self.callback_url = callback_url

    def notify(self, file_path: str, group_id: int) -> bool:
        """通知 Spring 服务处理文件"""
        try:
            from datetime import datetime
            payload = {
                "filePath": file_path,
                "groupId": group_id,
                "timestamp": datetime.now().isoformat()
            }

            logger.info(f"Sending callback: {payload}")
            response = requests.post(
                self.callback_url,
                json=payload,
                timeout=10
            )

            if response.status_code == 200:
                logger.info(f"Callback success: {file_path}")
                return True
            else:
                logger.error(f"Callback failed: {response.status_code}, response: {response.text}")
                return False
        except Exception as e:
            logger.error(f"Callback error: {e}")
            return False
