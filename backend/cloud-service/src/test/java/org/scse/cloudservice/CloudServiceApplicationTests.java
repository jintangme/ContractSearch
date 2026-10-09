package org.scse.cloudservice;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@EnabledIfEnvironmentVariable(named = "RUN_BUSINESS_DB_INTEGRATION", matches = "true")
class CloudServiceApplicationTests {
    @Autowired
    private ApplicationContext applicationContext;

    @Test
    void contextLoads() {
        assertThat(applicationContext.containsBean("contractCallbackController")).isTrue();
        assertThat(applicationContext.containsBean("smartContractMapper")).isTrue();
    }

}
