/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis_spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Map;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.SqlSessionTemplate;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

public class SqlSessionTemplateInnerSqlSessionInterceptorTest {

    @Test
    void invokesSqlSessionOperationsInsideSpringTransaction() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:session_interceptor;DB_CLOSE_DELAY=-1");
        createSchema(dataSource);

        Configuration configuration = new Configuration();
        configuration.addMapper(EntryMapper.class);

        SqlSessionFactoryBean sessionFactoryBean = new SqlSessionFactoryBean();
        sessionFactoryBean.setDataSource(dataSource);
        sessionFactoryBean.setConfiguration(configuration);
        SqlSessionFactory sessionFactory = sessionFactoryBean.getObject();
        SqlSessionTemplate template = new SqlSessionTemplate(sessionFactory);

        MapperFactoryBean<EntryMapper> mapperFactoryBean = new MapperFactoryBean<>(EntryMapper.class);
        mapperFactoryBean.setSqlSessionTemplate(template);
        mapperFactoryBean.afterPropertiesSet();
        EntryMapper factoryMapper = mapperFactoryBean.getObject();

        TransactionTemplate transaction = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        String name = transaction.execute(status -> {
            assertThat(template.insert(statement("insert"), Map.of("id", 1, "name", "Ada"))).isEqualTo(1);
            assertThat(template.<String>selectOne(statement("findName"), 1)).isEqualTo("Ada");

            EntryMapper templateMapper = template.getMapper(EntryMapper.class);
            assertThat(templateMapper.findName(1)).isEqualTo("Ada");
            assertThat(factoryMapper.insert(2, "Grace")).isEqualTo(1);
            return factoryMapper.findName(2);
        });

        assertThat(name).isEqualTo("Grace");
        assertThat(template.<String>selectOne(statement("findName"), 2)).isEqualTo("Grace");
    }

    private static void createSchema(JdbcDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE entries (id INT PRIMARY KEY, name VARCHAR(255) NOT NULL)");
        }
    }

    private static String statement(String methodName) {
        return EntryMapper.class.getName() + "." + methodName;
    }

    public interface EntryMapper {

        @Insert("INSERT INTO entries (id, name) VALUES (#{id}, #{name})")
        int insert(@Param("id") int id, @Param("name") String name);

        @Select("SELECT name FROM entries WHERE id = #{id}")
        String findName(@Param("id") int id);
    }
}
