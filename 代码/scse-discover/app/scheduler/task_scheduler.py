from apscheduler.schedulers.background import BackgroundScheduler
from apscheduler.triggers.cron import CronTrigger
import logging

logger = logging.getLogger(__name__)

class DynamicScheduler:
    def __init__(self):
        self.scheduler = BackgroundScheduler()
        self.job_id = "contract_fetch_job"

    def start(self):
        """启动调度器"""
        self.scheduler.start()
        logger.info("Scheduler started")

    def add_or_update_job(self, func, cron_expr: str):
        """添加或更新定时任务"""
        try:
            if self.scheduler.get_job(self.job_id):
                self.scheduler.reschedule_job(
                    self.job_id,
                    trigger=CronTrigger.from_crontab(cron_expr)
                )
                logger.info(f"Job rescheduled: {cron_expr}")
            else:
                self.scheduler.add_job(
                    func,
                    trigger=CronTrigger.from_crontab(cron_expr),
                    id=self.job_id
                )
                logger.info(f"Job added: {cron_expr}")
        except Exception as e:
            logger.error(f"Failed to schedule job: {e}")

    def shutdown(self):
        """关闭调度器"""
        self.scheduler.shutdown()
        logger.info("Scheduler shutdown")
