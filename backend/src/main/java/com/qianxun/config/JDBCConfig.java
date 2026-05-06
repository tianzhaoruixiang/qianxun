package com.qianxun.config;

import com.alibaba.druid.pool.DruidDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;

import javax.sql.DataSource;

@Configuration
@EnableTransactionManagement // 启用事务管理
@Slf4j
public class JDBCConfig {

    // TiDB 配置
    @Value("${tidb.datasource.DbUrl}")
    private String tidbUrl;
    @Value("${tidb.datasource.username:root}")
    private String tidbUsername;
    @Value("${tidb.datasource.password:}")
    private String tidbPassword;

    // 公共/自定义配置 (假设 Doris 和 TiDB 共用部分配置，或者你需要为 Doris 单独配置)
    @Value("${custom.datasource.driverClassName}")
    private String driverClassName;
    @Value("${custom.datasource.maxActive}")
    private int maxActive; // 直接定义为 int，减少解析代码
    @Value("${custom.datasource.initialSize}")
    private int initialSize;
    @Value("${custom.datasource.maxWait}")
    private long maxWait;
    @Value("${custom.datasource.minIdle}")
    private int minIdle;
    @Value("${custom.datasource.validationQuery}")
    private String validationQuery;
    @Value("${custom.datasource.connectionProperties}")
    private String connectionProperties;

    // Doris 配置
    @Value("${doris.datasource.enable:false}")
    private boolean dorisEnabled;
    @Value("${doris.datasource.host:doris-cluster}")
    private String dorisHost;
    @Value("${doris.datasource.port:9030}")
    private int dorisPort;
    @Value("${doris.datasource.database:test}")
    private String dorisDatabase;
    @Value("${doris.datasource.username:root}")
    private String dorisUsername;
    @Value("${doris.datasource.password:841_sjzc}")
    private String dorisPassword;

    /**
     * TiDB 数据源 (主数据源)
     */
    @Primary
    @Bean(name = "tidbDataSource")
    public DataSource tidbDataSource() {
        try {
            DruidDataSource druidDataSource = new DruidDataSource();
            druidDataSource.setName("tidbDataSource");
            druidDataSource.setUrl(tidbUrl);
            druidDataSource.setDriverClassName(driverClassName);
            druidDataSource.setUsername(tidbUsername);
            druidDataSource.setPassword(tidbPassword);

            druidDataSource.setMaxActive(maxActive);
            druidDataSource.setInitialSize(initialSize);
            druidDataSource.setMaxWait(maxWait);
            druidDataSource.setMinIdle(minIdle);
            druidDataSource.setValidationQuery(validationQuery);
            druidDataSource.setConnectionProperties(connectionProperties);

            druidDataSource.setFilters("stat,slf4j");
            druidDataSource.setTestOnBorrow(true);
            druidDataSource.setTestWhileIdle(true);
            druidDataSource.setPoolPreparedStatements(true);

            log.info("TiDB DataSource initialized successfully at {}", tidbUrl);
            return druidDataSource;
        } catch (Exception e) {
            log.error("Failed to initialize TiDB DataSource", e);
            throw new RuntimeException("Failed to initialize TiDB DataSource", e);
        }
    }

    /**
     * Doris 数据源
     * 通过 doris.datasource.enabled=false 可禁用，禁用后服务仍可正常启动
     */
    @Bean(name = "dorisDataSource")
    @ConditionalOnProperty(name = "doris.datasource.enable", havingValue = "true", matchIfMissing = false)
    public DataSource dorisDataSource() {
        String url = String.format(
            "jdbc:mysql://%s:%d/%s?createDatabaseIfNotExist=true&useUnicode=true&characterEncoding=utf8&serverTimezone=Hongkong&cachePrepStmt=true&userServerPrepStmt=true&rewriteBatchedStatements=true",
            dorisHost, dorisPort, dorisDatabase
        );
        try {
            DruidDataSource druidDataSource = new DruidDataSource();
            druidDataSource.setName("dorisDataSource");
            druidDataSource.setUrl(url);
            druidDataSource.setDriverClassName(driverClassName);
            druidDataSource.setUsername(dorisUsername);
            druidDataSource.setPassword(dorisPassword);

            druidDataSource.setMaxActive(maxActive);
            druidDataSource.setInitialSize(initialSize);
            druidDataSource.setMaxWait(maxWait);
            druidDataSource.setMinIdle(minIdle);
            druidDataSource.setValidationQuery(validationQuery);
            druidDataSource.setConnectionProperties(connectionProperties);

            druidDataSource.setFilters("stat,slf4j");
            druidDataSource.setTestOnBorrow(true);
            druidDataSource.setTestWhileIdle(true);
            druidDataSource.setPoolPreparedStatements(true);

            log.info("Doris DataSource initialized successfully at {}:{}", dorisHost, dorisPort);
            return druidDataSource;
        } catch (Exception e) {
            log.warn("Doris DataSource failed to initialize (Doris disabled): {}", e.getMessage());
            throw new RuntimeException("Failed to initialize Doris DataSource", e);
        }
    }

    /**
     * TiDB JdbcTemplate
     */
    @Bean
    public JdbcTemplate tidbJdbcTemplate(@Qualifier("tidbDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * Doris JdbcTemplate
     */
    @Bean
    @ConditionalOnBean(name = "dorisDataSource")
    public JdbcTemplate dorisJdbcTemplate(@Qualifier("dorisDataSource") DataSource dataSource) {
        return new JdbcTemplate(dataSource);
    }

    /**
     * TiDB 事务管理器 (主事务管理器)
     */
    @Primary
    @Bean
    public DataSourceTransactionManager tidbTransactionManager(@Qualifier("tidbDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }

    /**
     * Doris 事务管理器
     * 使用时需在 @Transactional 中指定：@Transactional(transactionManager = "dorisTransactionManager")
     */
    @Bean
    @ConditionalOnBean(name = "dorisDataSource")
    public DataSourceTransactionManager dorisTransactionManager(@Qualifier("dorisDataSource") DataSource dataSource) {
        return new DataSourceTransactionManager(dataSource);
    }
}