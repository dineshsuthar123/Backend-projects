package com.naresh.urlshortener.config;

import com.zaxxer.hikari.HikariDataSource;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.jdbc.DataSourceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Configuration
@EnableConfigurationProperties(DataSourceProperties.class)
class DataSourceConfig {

    @Bean
    @Primary
    DataSource dataSource(
            DataSourceProperties properties,
            @Value("${spring.datasource.hikari.maximum-pool-size:20}") int maximumPoolSize,
            @Value("${spring.datasource.hikari.minimum-idle:5}") int minimumIdle,
            @Value("${spring.datasource.hikari.connection-timeout:500}") long connectionTimeout,
            @Value("${spring.datasource.hikari.validation-timeout:300}") long validationTimeout,
            @Value("${app.datasource.replica-urls:}") List<String> replicaUrls
    ) {
        Map<Object, Object> targets = new LinkedHashMap<>();
        HikariDataSource primary = hikari(properties.getUrl(), properties.getUsername(), properties.getPassword(),
                maximumPoolSize, minimumIdle, connectionTimeout, validationTimeout, "primary");
        targets.put("primary", primary);

        for (int i = 0; i < replicaUrls.size(); i++) {
            String replicaUrl = replicaUrls.get(i);
            if (replicaUrl == null || replicaUrl.isBlank()) {
                continue;
            }
            String key = "replica-" + i;
            targets.put(key, hikari(replicaUrl, properties.getUsername(), properties.getPassword(),
                    maximumPoolSize, minimumIdle, connectionTimeout, validationTimeout, key));
        }

        List<String> replicaKeys = targets.keySet().stream()
                .map(Object::toString)
                .filter(key -> key.startsWith("replica-"))
                .toList();

        ReadReplicaRoutingDataSource routing = new ReadReplicaRoutingDataSource(replicaKeys);
        routing.setDefaultTargetDataSource(primary);
        routing.setTargetDataSources(targets);
        routing.afterPropertiesSet();
        return routing;
    }

    private static HikariDataSource hikari(String url, String username, String password, int maximumPoolSize,
                                           int minimumIdle, long connectionTimeout, long validationTimeout,
                                           String poolName) {
        HikariDataSource dataSource = new HikariDataSource();
        dataSource.setJdbcUrl(url);
        dataSource.setUsername(username);
        dataSource.setPassword(password);
        dataSource.setMaximumPoolSize(maximumPoolSize);
        dataSource.setMinimumIdle(minimumIdle);
        dataSource.setConnectionTimeout(connectionTimeout);
        dataSource.setValidationTimeout(validationTimeout);
        dataSource.setPoolName("url-shortener-" + poolName);
        return dataSource;
    }
}
