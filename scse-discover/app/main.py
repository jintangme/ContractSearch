from fastapi import FastAPI
from contextlib import asynccontextmanager
import os
import logging
from dotenv import load_dotenv

from app.config.nacos_config import NacosConfigManager
from app.scheduler.task_scheduler import DynamicScheduler
from app.services.task_service import ContractFetchTask


logging.basicConfig(level=logging.INFO)
logging.getLogger().setLevel(logging.INFO)
logger = logging.getLogger(__name__)

# Local development only. Production should inject the same variables through
# the process/container environment. The .env file is excluded from Git.
load_dotenv()

# 全局变量
nacos_manager = None
scheduler = None
fetch_task = None

@asynccontextmanager
async def lifespan(app: FastAPI):
    global nacos_manager, scheduler, fetch_task

    # 启动
    # nacos 配置管理
    nacos_server = os.getenv("NACOS_SERVER", "localhost:8848")
    nacos_manager = NacosConfigManager(nacos_server)
    config = nacos_manager.get_config("scse-discover.yaml")

    # 初始化任务调度
    fetch_task = ContractFetchTask(config)
    scheduler = DynamicScheduler()
    scheduler.start()
    scheduler.add_or_update_job(fetch_task.execute, config["schedule"]["cron"])

    def on_config_change(new_config):
        fetch_task.update_config(new_config)
        scheduler.add_or_update_job(fetch_task.execute, new_config["schedule"]["cron"])

    nacos_manager.add_config_listener("scse-discover.yaml", "DEFAULT_GROUP", on_config_change)
    logger.info("Service started")

    yield

    # 关闭
    if scheduler:
        scheduler.shutdown()
    logger.info("Service stopped")

app = FastAPI(title="Contract Discovery Service", lifespan=lifespan)

@app.get("/health")
async def health():
    return {"status": "ok"}

@app.post("/trigger")
async def trigger():
    if fetch_task is None:
        return {"status": "error", "message": "Service not initialized"}
    import asyncio
    asyncio.get_event_loop().run_in_executor(None, fetch_task.execute)
    return {"status": "triggered"}
