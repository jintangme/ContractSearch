package org.scse.cloudagent;

import org.scse.cloudagent.config.AgentProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;
import org.springframework.cloud.openfeign.EnableFeignClients;

@SpringBootApplication
@EnableDiscoveryClient
@EnableFeignClients
@EnableConfigurationProperties(AgentProperties.class)
public class CloudAgentServiceApplication {
    public static void main(String[] args) {
        SpringApplication.run(CloudAgentServiceApplication.class, args);
    }
}
