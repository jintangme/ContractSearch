import requests
import logging
import os

logger = logging.getLogger(__name__)

class CallbackNotifier:
    def __init__(self, callback_url: str):
        self.callback_url = callback_url
        self.token = os.getenv("CONTRACT_IMPORT_TOKEN", "").strip()
        if len(self.token) < 24:
            raise ValueError("CONTRACT_IMPORT_TOKEN must contain at least 24 characters")

    def notify(self, file_path: str, group_id: int) -> bool:
        """通知 Spring 服务处理文件"""
        try:
            from datetime import datetime
            payload = {
                "filePath": file_path,
                "groupId": group_id,
                "timestamp": datetime.now().isoformat()
            }

            logger.info("Sending contract import callback")
            response = requests.post(
                self.callback_url,
                json=payload,
                headers={"X-Contract-Import-Token": self.token},
                allow_redirects=False,
                timeout=10
            )

            if response.status_code == 200:
                logger.info(f"Callback success: {file_path}")
                return True
            else:
                logger.error("Callback failed with HTTP %s", response.status_code)
                return False
        except Exception as e:
            logger.error("Callback request failed (%s)", type(e).__name__)
            return False
