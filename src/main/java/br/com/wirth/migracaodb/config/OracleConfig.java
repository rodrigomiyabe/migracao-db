package br.com.wirth.migracaodb.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

//Pega as configurações do application.properties onde o prefixo é oracle.datasource
@Configuration
public class OracleConfig {

    @Primary
    @Bean
    @ConfigurationProperties(prefix = "oracle.datasource")
    public DataSource oracleDataSource() {
        //cria a o DB
       return DataSourceBuilder.create().build();
    }

    @Bean(name = "oracleJdbcTemplate")
    public JdbcTemplate oracleJdbcTemplate(@Qualifier("oracleDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }
}
