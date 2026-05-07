/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_baomidou.mybatis_plus_boot_starter;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.autoconfigure.ConfigurationCustomizer;
import com.baomidou.mybatisplus.autoconfigure.DdlApplicationRunner;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusProperties;
import com.baomidou.mybatisplus.autoconfigure.MybatisPlusPropertiesCustomizer;
import com.baomidou.mybatisplus.autoconfigure.SafetyEncryptProcessor;
import com.baomidou.mybatisplus.autoconfigure.SqlSessionFactoryBeanCustomizer;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.AES;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.ddl.IDdl;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.reflection.factory.ObjectFactory;
import org.apache.ibatis.session.ExecutorType;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.builder.SpringApplicationBuilder;
import org.springframework.boot.env.OriginTrackedMapPropertySource;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.SimpleCommandLinePropertySource;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;

public class Mybatis_plus_boot_starterTest {

    @Test
    void autoConfigurationCreatesTemplateAndMybatisPlusMapper() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(TestApplication.class)
                .web(WebApplicationType.NONE)
                .properties(Map.of(
                        "spring.datasource.url", "jdbc:h2:mem:mybatisPlusBootStarter;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "spring.datasource.driver-class-name", "org.h2.Driver",
                        "spring.datasource.hikari.maximum-pool-size", "2",
                        "spring.datasource.hikari.connection-timeout", "2000",
                        "spring.main.banner-mode", "off",
                        "logging.level.root", "WARN"))
                .run()) {
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            jdbcTemplate.execute("""
                    CREATE TABLE account (
                        id BIGINT PRIMARY KEY,
                        user_name VARCHAR(64) NOT NULL,
                        age INTEGER NOT NULL
                    )
                    """);

            SqlSessionTemplate sqlSessionTemplate = context.getBean(SqlSessionTemplate.class);
            SqlSessionFactory sqlSessionFactory = context.getBean(SqlSessionFactory.class);
            AccountMapper mapper = context.getBean(AccountMapper.class);

            assertThat(sqlSessionTemplate.getExecutorType()).isEqualTo(ExecutorType.REUSE);
            assertThat(sqlSessionFactory.getConfiguration().isMapUnderscoreToCamelCase()).isTrue();
            assertThat(sqlSessionFactory.getConfiguration().isCallSettersOnNulls()).isTrue();

            Account account = new Account();
            account.setId(1L);
            account.setUserName("Ada Lovelace");
            account.setAge(36);

            assertThat(mapper.insert(account)).isEqualTo(1);
            Account selected = mapper.selectById(1L);
            assertThat(selected.getUserName()).isEqualTo("Ada Lovelace");
            assertThat(selected.getAge()).isEqualTo(36);

            selected.setAge(37);
            assertThat(mapper.updateById(selected)).isEqualTo(1);
            assertThat(mapper.selectList(Wrappers.<Account>query().ge("age", 37)))
                    .extracting(Account::getUserName)
                    .containsExactly("Ada Lovelace");
        }
    }

    @Test
    void interceptorBeanIsAppliedToPaginatedMapperQueries() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(PaginationInterceptorApplication.class)
                .web(WebApplicationType.NONE)
                .properties(Map.of(
                        "spring.datasource.url", "jdbc:h2:mem:mybatisPlusBootStarterPagination;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "spring.datasource.driver-class-name", "org.h2.Driver",
                        "spring.datasource.hikari.maximum-pool-size", "2",
                        "spring.datasource.hikari.connection-timeout", "2000",
                        "spring.main.banner-mode", "off",
                        "logging.level.root", "WARN"))
                .run()) {
            JdbcTemplate jdbcTemplate = context.getBean(JdbcTemplate.class);
            jdbcTemplate.execute("""
                    CREATE TABLE account (
                        id BIGINT PRIMARY KEY,
                        user_name VARCHAR(64) NOT NULL,
                        age INTEGER NOT NULL
                    )
                    """);
            jdbcTemplate.update("INSERT INTO account (id, user_name, age) VALUES (?, ?, ?)", 1L, "Ada Lovelace", 36);
            jdbcTemplate.update("INSERT INTO account (id, user_name, age) VALUES (?, ?, ?)", 2L, "Grace Hopper", 85);
            jdbcTemplate.update("INSERT INTO account (id, user_name, age) VALUES (?, ?, ?)", 3L, "Katherine Johnson", 101);

            AccountMapper mapper = context.getBean(AccountMapper.class);
            Page<Account> page = mapper.selectPage(new Page<>(2, 1), Wrappers.<Account>query().orderByAsc("id"));

            assertThat(page.getTotal()).isEqualTo(3);
            assertThat(page.getPages()).isEqualTo(3);
            assertThat(page.getRecords())
                    .extracting(Account::getUserName)
                    .containsExactly("Grace Hopper");
        }
    }

    @Test
    void propertiesResolveMapperLocationsAndCanBeCustomized() {
        MybatisPlusProperties properties = new MybatisPlusProperties();
        properties.setMapperLocations(new String[] {
                "classpath*:com/baomidou/mybatisplus/autoconfigure/*.class",
                "classpath*:missing-mappers/**/*.xml"
        });
        properties.setExecutorType(ExecutorType.SIMPLE);

        MybatisPlusPropertiesCustomizer customizer = customized -> customized.setExecutorType(ExecutorType.BATCH);
        customizer.customize(properties);

        assertThat(properties.getExecutorType()).isEqualTo(ExecutorType.BATCH);
        assertThat(properties.getGlobalConfig()).isNotNull();
        assertThat(properties.resolveMapperLocations())
                .extracting(resource -> resource.getFilename())
                .contains("MybatisPlusAutoConfiguration.class", "MybatisPlusProperties.class");
    }

    @Test
    void sqlSessionFactoryBeanCustomizerInstallsCustomObjectFactory() {
        try (ConfigurableApplicationContext context = new SpringApplicationBuilder(FactoryBeanCustomizerApplication.class)
                .web(WebApplicationType.NONE)
                .properties(Map.of(
                        "spring.datasource.url", "jdbc:h2:mem:mybatisPlusBootStarterFactoryCustomizer;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                        "spring.datasource.driver-class-name", "org.h2.Driver",
                        "spring.datasource.hikari.maximum-pool-size", "1",
                        "spring.datasource.hikari.connection-timeout", "2000",
                        "spring.main.banner-mode", "off",
                        "logging.level.root", "WARN"))
                .run()) {
            ObjectFactory objectFactory = context.getBean(SqlSessionFactory.class).getConfiguration().getObjectFactory();

            assertThat(objectFactory).isInstanceOf(AccountDefaultsObjectFactory.class);
            assertThat(objectFactory.create(Account.class).getUserName()).isEqualTo("created by custom object factory");
        }
    }

    @Test
    void safetyEncryptProcessorDecryptsOriginTrackedPropertiesWhenKeyIsPresent() {
        String key = "1234567890abcdef";
        String encryptedPassword = "mpw:" + AES.encrypt("native-friendly-secret", key);
        Map<String, Object> encryptedProperties = new HashMap<>();
        encryptedProperties.put("app.datasource.password", encryptedPassword);
        encryptedProperties.put("app.datasource.username", "plain-user");

        ConfigurableEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().addFirst(new OriginTrackedMapPropertySource("encrypted", encryptedProperties));
        environment.getPropertySources().addFirst(new SimpleCommandLinePropertySource("commandLine", "--mpw.key=" + key));

        EnvironmentPostProcessor processor = new SafetyEncryptProcessor();
        processor.postProcessEnvironment(environment, new SpringApplication(TestApplication.class));

        assertThat(environment.getProperty("app.datasource.password")).isEqualTo("native-friendly-secret");
        assertThat(environment.getProperty("app.datasource.username")).isEqualTo("plain-user");
    }

    @Test
    void ddlApplicationRunnerDelegatesToConfiguredDdlBeans() throws Exception {
        RecordingDdl ddl = new RecordingDdl();
        DdlApplicationRunner runner = new DdlApplicationRunner(List.of(ddl));

        runner.run(new DefaultApplicationArguments(new String[] {"--ddl=true"}));

        assertThat(ddl.invocations()).isEqualTo(1);
        assertThat(ddl.observedSqlFiles()).containsExactly("schema/account.sql", "schema/audit.sql");
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    public static class PaginationInterceptorApplication {

        @Bean
        MapperFactoryBean<AccountMapper> accountMapper(SqlSessionFactory sqlSessionFactory) {
            MapperFactoryBean<AccountMapper> mapperFactoryBean = new MapperFactoryBean<>(AccountMapper.class);
            mapperFactoryBean.setSqlSessionFactory(sqlSessionFactory);
            return mapperFactoryBean;
        }

        @Bean
        MybatisPlusInterceptor mybatisPlusInterceptor() {
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.H2));
            return interceptor;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    public static class FactoryBeanCustomizerApplication {

        @Bean
        MapperFactoryBean<AccountMapper> accountMapper(SqlSessionFactory sqlSessionFactory) {
            MapperFactoryBean<AccountMapper> mapperFactoryBean = new MapperFactoryBean<>(AccountMapper.class);
            mapperFactoryBean.setSqlSessionFactory(sqlSessionFactory);
            return mapperFactoryBean;
        }

        @Bean
        SqlSessionFactoryBeanCustomizer sqlSessionFactoryBeanCustomizer() {
            return factory -> factory.setObjectFactory(new AccountDefaultsObjectFactory());
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    public static class TestApplication {

        @Bean
        MapperFactoryBean<AccountMapper> accountMapper(SqlSessionFactory sqlSessionFactory) {
            MapperFactoryBean<AccountMapper> mapperFactoryBean = new MapperFactoryBean<>(AccountMapper.class);
            mapperFactoryBean.setSqlSessionFactory(sqlSessionFactory);
            return mapperFactoryBean;
        }

        @Bean
        ConfigurationCustomizer configurationCustomizer() {
            return configuration -> {
                configuration.setMapUnderscoreToCamelCase(true);
                configuration.setCallSettersOnNulls(true);
            };
        }

        @Bean
        MybatisPlusPropertiesCustomizer mybatisPlusPropertiesCustomizer() {
            return properties -> properties.setExecutorType(ExecutorType.REUSE);
        }
    }

    @Mapper
    public interface AccountMapper extends BaseMapper<Account> {
    }

    @TableName("account")
    public static class Account {
        @TableId(type = IdType.INPUT)
        private Long id;
        private String userName;
        private Integer age;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getUserName() {
            return userName;
        }

        public void setUserName(String userName) {
            this.userName = userName;
        }

        public Integer getAge() {
            return age;
        }

        public void setAge(Integer age) {
            this.age = age;
        }
    }

    private static final class AccountDefaultsObjectFactory extends DefaultObjectFactory {
        private static final long serialVersionUID = 1L;

        @Override
        public <T> T create(Class<T> type) {
            return initialize(super.create(type));
        }

        @Override
        public <T> T create(Class<T> type, List<Class<?>> constructorArgTypes, List<Object> constructorArgs) {
            return initialize(super.create(type, constructorArgTypes, constructorArgs));
        }

        private <T> T initialize(T instance) {
            if (instance instanceof Account account) {
                account.setUserName("created by custom object factory");
            }
            return instance;
        }
    }

    private static final class RecordingDdl implements IDdl {
        private final AtomicInteger invocations = new AtomicInteger();
        private final AtomicReference<List<String>> observedSqlFiles = new AtomicReference<>(List.of());

        @Override
        public void runScript(java.util.function.Consumer<DataSource> consumer) {
            invocations.incrementAndGet();
            observedSqlFiles.set(getSqlFiles());
        }

        @Override
        public List<String> getSqlFiles() {
            return List.of("schema/account.sql", "schema/audit.sql");
        }

        int invocations() {
            return invocations.get();
        }

        List<String> observedSqlFiles() {
            return observedSqlFiles.get();
        }
    }
}
