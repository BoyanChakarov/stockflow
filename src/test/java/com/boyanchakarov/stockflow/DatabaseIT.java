package com.boyanchakarov.stockflow;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest
@Import(DatabaseIT.DatabaseConfig.class)
class DatabaseIT {
  @TestConfiguration(proxyBeanMethods = false)
  static class DatabaseConfig {
    @Bean
    @ServiceConnection
    @ConditionalOnProperty(
        name = "stockflow.test.external",
        havingValue = "false",
        matchIfMissing = true)
    PostgreSQLContainer postgres() {
      return new PostgreSQLContainer(
          org.testcontainers.utility.DockerImageName.parse(
                  "postgres@sha256:41fc5342eefba6cc2ccda736aaf034bbbb7c3df0fdb81516eba1ba33f360162c")
              .asCompatibleSubstituteFor("postgres"));
    }
  }

  @DynamicPropertySource
  static void properties(DynamicPropertyRegistry r) {
    // No test ever inherits the application's normal database URL or password.
    r.add("spring.datasource.url", () -> "jdbc:postgresql://127.0.0.1:1/invalid_test_database");
    r.add("spring.datasource.username", () -> "test");
    r.add("spring.datasource.password", () -> "test");
    if (Boolean.getBoolean("stockflow.test.external")) {
      String url = System.getenv("STOCKFLOW_TEST_URL");
      if (url == null || !url.endsWith("/stockflow_test"))
        throw new IllegalStateException(
            "External tests require a dedicated /stockflow_test database");
      r.add("spring.datasource.url", () -> url);
      r.add("spring.datasource.username", () -> System.getenv("STOCKFLOW_TEST_USER"));
      r.add("spring.datasource.password", () -> System.getenv("STOCKFLOW_TEST_PASSWORD"));
    }
  }

  @Autowired JdbcTemplate jdbc;

  @Test
  void freshSchemaMigratesAndHibernateValidates() {
    assertThat(
            jdbc.queryForObject(
                "select count(*) from flyway_schema_history where success", Integer.class))
        .isEqualTo(2);
    assertThat(
            jdbc.queryForObject("select current_setting('server_version_num')::int", Integer.class))
        .isGreaterThanOrEqualTo(180000);
  }
}
