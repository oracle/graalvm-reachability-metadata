/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_baomidou.mybatis_plus_boot_starter;

import static org.assertj.core.api.Assertions.assertThat;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.toolkit.Wrappers;
import java.sql.Connection;
import java.sql.Statement;
import javax.sql.DataSource;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;

/** Exercises starter auto-configuration and mapper CRUD through its public API. §FS-test-contract */
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

    private void createSchema(DataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE people (id BIGINT AUTO_INCREMENT PRIMARY KEY, name VARCHAR(64) NOT NULL)");
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

    public interface PersonMapper extends BaseMapper<Person> {
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
}
