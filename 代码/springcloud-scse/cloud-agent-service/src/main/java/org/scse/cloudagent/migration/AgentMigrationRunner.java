package org.scse.cloudagent.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.scse.cloudagent.config.AgentProperties;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "agent.migrations", name = "enabled", havingValue = "true")
public class AgentMigrationRunner implements ApplicationRunner {
    private final AgentProperties properties;

    public AgentMigrationRunner(AgentProperties properties) {
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        AgentProperties.Migrations migration = properties.migrations();
        Flyway.configure()
                .dataSource(migration.url(), migration.username(), migration.password())
                .baselineOnMigrate(true)
                .baselineVersion(MigrationVersion.fromVersion("0"))
                .locations("classpath:db/migration")
                .load()
                .migrate();
    }
}
