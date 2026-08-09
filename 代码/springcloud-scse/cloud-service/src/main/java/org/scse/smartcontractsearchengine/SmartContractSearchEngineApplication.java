package org.scse.smartcontractsearchengine;

import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@MapperScan("org.scse.smartcontractsearchengine.mapper")
@EnableScheduling
@EnableDiscoveryClient
@EnableFeignClients
public class SmartContractSearchEngineApplication {

    public static void main(String[] args) {
        SpringApplication.run(SmartContractSearchEngineApplication.class, args);
    }

}
