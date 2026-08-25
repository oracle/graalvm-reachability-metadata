/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_baomidou.mybatis_plus_boot_starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.handlers.MetaObjectHandler;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import java.sql.Connection;
import java.sql.Statement;
import java.time.LocalDateTime;
import javax.sql.DataSource;
import org.apache.ibatis.reflection.MetaObject;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/** Exercises starter auto-configuration, mapper CRUD, and field filling through public APIs. FS-test-contract */
public class Mybatis_plus_boot_starterTest {
    @Test
    void configuresMapperAndPerformsCrudOperations() throws Exception {
        try (ConfigurableApplicationContext context = SpringApplication.run(TestApplication.class,
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=jdbc:h2:mem:test;MODE=MYSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=")) {
            createSchema(context.getBean(DataSource.class));
            PersonMapper mapper = context.getBean(PersonMapper.class);

            Person person = new Person();
            person.setName("Ada");
            assertThat(mapper.insert(person)).isEqualTo(1);
            assertThat(person.getId()).isNotNull();

            LambdaQueryWrapper<Person> namedAda = Wrappers.<Person>lambdaQuery()
                    .eq(Person::getName, "Ada");
            assertThat(mapper.selectOne(namedAda).getName()).isEqualTo("Ada");

            person.setName("Grace");
            assertThat(mapper.updateById(person)).isEqualTo(1);
            assertThat(mapper.selectById(person.getId()).getName()).isEqualTo("Grace");

            assertThat(mapper.deleteById(person.getId())).isEqualTo(1);
            assertThat(mapper.selectCount(Wrappers.<Person>query())).isZero();
        }
    }

    @Test
    void paginatesMapperResultsWithPaginationInterceptor() throws Exception {
        try (ConfigurableApplicationContext context = SpringApplication.run(PaginationTestApplication.class,
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=jdbc:h2:mem:pagination;MODE=MYSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=")) {
            createSchema(context.getBean(DataSource.class));
            insertPeople(context.getBean(DataSource.class));

            Page<Person> page = context.getBean(PersonMapper.class).selectPage(new Page<>(2, 2),
                    Wrappers.<Person>query().orderByAsc("id"));

            assertThat(page.getTotal()).isEqualTo(3);
            assertThat(page.getPages()).isEqualTo(2);
            assertThat(page.getRecords()).extracting(Person::getName).containsExactly("Grace");
        }
    }

    @Test
    void fillsAuditFieldsThroughMetaObjectHandler() throws Exception {
        try (ConfigurableApplicationContext context = SpringApplication.run(AuditedTestApplication.class,
                "--spring.main.web-application-type=none",
                "--spring.datasource.url=jdbc:h2:mem:test;MODE=MYSQL;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE",
                "--spring.datasource.username=sa",
                "--spring.datasource.password=")) {
            createAuditedSchema(context.getBean(DataSource.class));
            AuditedPersonMapper mapper = context.getBean(AuditedPersonMapper.class);

            AuditedPerson person = new AuditedPerson();
            person.setName("Ada");
            assertThat(mapper.insert(person)).isEqualTo(1);
            assertThat(person.getCreatedAt()).isNotNull();
            assertThat(person.getUpdatedAt()).isNotNull();

            person.setName("Grace");
            assertThat(mapper.updateById(person)).isEqualTo(1);
            AuditedPerson updatedPerson = mapper.selectById(person.getId());
            assertThat(updatedPerson.getName()).isEqualTo("Grace");
            assertThat(updatedPerson.getCreatedAt()).isNotNull();
            assertThat(updatedPerson.getUpdatedAt()).isNotNull();
        }
    }

    private void createSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE people (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(64) NOT NULL)");
        }
    }

    private void insertPeople(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("INSERT INTO people (name) VALUES ('Ada'), ('Linus'), ('Grace')");
        }
    }

    private void createAuditedSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE IF NOT EXISTS audited_people ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                    + "name VARCHAR(64) NOT NULL, "
                    + "created_at TIMESTAMP NOT NULL, "
                    + "updated_at TIMESTAMP NOT NULL)");
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class TestApplication {
        @Bean
        MapperFactoryBean<PersonMapper> personMapper(SqlSessionFactory sqlSessionFactory) {
            MapperFactoryBean<PersonMapper> mapperFactoryBean = new MapperFactoryBean<>(PersonMapper.class);
            mapperFactoryBean.setSqlSessionFactory(sqlSessionFactory);
            return mapperFactoryBean;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class PaginationTestApplication {
        @Bean
        MapperFactoryBean<PersonMapper> personMapper(SqlSessionFactory sqlSessionFactory) {
            MapperFactoryBean<PersonMapper> mapperFactoryBean = new MapperFactoryBean<>(PersonMapper.class);
            mapperFactoryBean.setSqlSessionFactory(sqlSessionFactory);
            return mapperFactoryBean;
        }

        @Bean
        MybatisPlusInterceptor paginationInterceptor() {
            MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
            interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
            return interceptor;
        }
    }

    @SpringBootConfiguration
    @EnableAutoConfiguration
    static class AuditedTestApplication {
        @Bean
        MapperFactoryBean<AuditedPersonMapper> auditedPersonMapper(SqlSessionFactory sqlSessionFactory) {
            MapperFactoryBean<AuditedPersonMapper> mapperFactoryBean = new MapperFactoryBean<>(AuditedPersonMapper.class);
            mapperFactoryBean.setSqlSessionFactory(sqlSessionFactory);
            return mapperFactoryBean;
        }

        @Bean
        MetaObjectHandler auditFieldHandler() {
            return new MetaObjectHandler() {
                @Override
                public void insertFill(MetaObject metaObject) {
                    LocalDateTime now = LocalDateTime.now();
                    strictInsertFill(metaObject, "createdAt", LocalDateTime.class, now);
                    strictInsertFill(metaObject, "updatedAt", LocalDateTime.class, now);
                }

                @Override
                public void updateFill(MetaObject metaObject) {
                    strictUpdateFill(metaObject, "updatedAt", LocalDateTime.class, LocalDateTime.now());
                }
            };
        }
    }

    public interface PersonMapper extends BaseMapper<Person> {
    }

    public interface AuditedPersonMapper extends BaseMapper<AuditedPerson> {
    }

    @TableName("people")
    public static class Person {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    @TableName("audited_people")
    public static class AuditedPerson {
        @TableId(type = IdType.AUTO)
        private Long id;
        private String name;
        @TableField(fill = FieldFill.INSERT)
        private LocalDateTime createdAt;
        @TableField(fill = FieldFill.INSERT_UPDATE)
        private LocalDateTime updatedAt;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public LocalDateTime getCreatedAt() {
            return createdAt;
        }

        public void setCreatedAt(LocalDateTime createdAt) {
            this.createdAt = createdAt;
        }

        public LocalDateTime getUpdatedAt() {
            return updatedAt;
        }

        public void setUpdatedAt(LocalDateTime updatedAt) {
            this.updatedAt = updatedAt;
        }
    }
}
