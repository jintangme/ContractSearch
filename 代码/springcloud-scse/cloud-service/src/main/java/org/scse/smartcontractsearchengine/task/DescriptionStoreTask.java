package org.scse.smartcontractsearchengine.task;

import lombok.extern.slf4j.Slf4j;
import org.scse.smartcontractsearchengine.service.ContractProcessService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
public class DescriptionStoreTask {

    @Autowired
    private ContractProcessService contractProcessService;

    @Scheduled(cron = "0 0 4 * * ?") // 每天 4:00 执行
    public void refreshInvalidData() {
        log.info("开始刷新无效数据");
        contractProcessService.updateInEffectiveContractsDesc(1000);
        contractProcessService.updateInEffectiveContractsEmbedding(1000);
        log.info("刷新无效数据完成");
    }


}
