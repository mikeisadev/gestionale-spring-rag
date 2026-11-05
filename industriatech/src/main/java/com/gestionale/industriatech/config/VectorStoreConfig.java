package com.gestionale.industriatech.config;

import javax.sql.DataSource;

import com.zaxxer.hikari.HikariDataSource;

import org.springframework.ai.vectorstore.VectorStore;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

@Configuration(proxyBeanMethods = false)
public class VectorStoreConfig {

    // Proprietà del datasource PostgreSQL
    @Bean(defaultCandidate = false)
    @ConfigurationProperties("app.datasource.pgvector")
    public DataSourceProperties pgVectorDataSourceProperties() {
        return new DataSourceProperties();
    }


    // Datasource PostgreSQL secondario
    @Bean(defaultCandidate = false)
    public HikariDataSource pgVectorDataSource(
            @Qualifier("pgVectorDataSourceProperties")
            DataSourceProperties properties) {

        return properties.initializeDataSourceBuilder()
                .type(HikariDataSource.class)
                .build();
    }


    // JdbcTemplate principale per Spring AI:
    // punta a PostgreSQL, NON a MariaDB.
    @Bean
    @Primary
    public JdbcTemplate pgVectorJdbcTemplate(
            @Qualifier("pgVectorDataSource")
            DataSource dataSource) {

        return new JdbcTemplate(dataSource);
    }
}
