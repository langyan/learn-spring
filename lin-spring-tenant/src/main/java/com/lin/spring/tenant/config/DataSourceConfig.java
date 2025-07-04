package com.lin.spring.tenant.config;

import com.lin.spring.tenant.datasource.MultiTenantDataSource;
import com.zaxxer.hikari.HikariDataSource;
import org.hibernate.boot.model.source.spi.MultiTenancySource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class DataSourceConfig {

    @Bean
    public DataSource dataSource() {
        Map<Object, Object> dataSources = new HashMap<>();



        // Example: register tenants manually
        dataSources.put("tenant1", createDataSource("jdbc:postgresql://localhost:5432/tenant1"));
        dataSources.put("tenant2", createDataSource("jdbc:postgresql://localhost:5432/tenant2"));

        MultiTenantDataSource dataSource = new MultiTenantDataSource();
        dataSource.setTargetDataSources(dataSources);
        dataSource.setDefaultTargetDataSource(dataSources.get("tenant1"));
//
        return dataSource;
    }

    private DataSource createDataSource(String url) {
        HikariDataSource ds = new HikariDataSource();
        ds.setJdbcUrl(url);
        ds.setUsername("dbuser");
        ds.setPassword("dbpass");
        return ds;
    }
}
