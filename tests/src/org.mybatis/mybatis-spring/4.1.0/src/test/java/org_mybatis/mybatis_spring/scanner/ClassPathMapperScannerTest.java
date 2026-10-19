/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis_spring.scanner;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.Connection;
import java.sql.Statement;
import java.util.Set;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.mybatis.spring.mapper.ClassPathMapperScanner;
import org.mybatis.spring.mapper.MapperFactoryBean;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.config.BeanDefinitionHolder;
import org.springframework.context.support.GenericApplicationContext;

public class ClassPathMapperScannerTest {

    @Test
    void scansAndRunsMapperThroughRegisteredFactoryBean() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:mapper_scanner;DB_CLOSE_DELAY=-1");
        createSchema(dataSource);

        SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
        sqlSessionFactoryBean.setDataSource(dataSource);
        SqlSessionFactory sqlSessionFactory = sqlSessionFactoryBean.getObject();

        try (GenericApplicationContext context = new GenericApplicationContext()) {
            ClassPathMapperScanner scanner = new ClassPathMapperScanner(context, context.getEnvironment());
            scanner.setSqlSessionFactory(sqlSessionFactory);
            scanner.registerFilters();

            Set<BeanDefinitionHolder> definitions = scanner.doScan(ClassPathMapperScannerTest.class.getPackageName());

            assertThat(definitions).singleElement().satisfies(holder -> {
                BeanDefinition definition = holder.getBeanDefinition();
                assertThat(definition.getBeanClassName()).isEqualTo(MapperFactoryBean.class.getName());
                assertThat(definition.getConstructorArgumentValues().getGenericArgumentValues()).singleElement()
                        .extracting(value -> value.getValue()).isSameAs(ScannerMapper.class);
            });

            context.refresh();
            ScannerMapper mapper = context.getBean(ScannerMapper.class);
            assertThat(mapper.insert(1, "scanner-value")).isEqualTo(1);
            assertThat(mapper.findValue(1)).isEqualTo("scanner-value");
        }
    }

    private static void createSchema(JdbcDataSource dataSource) throws Exception {
        try (Connection connection = dataSource.getConnection(); Statement statement = connection.createStatement()) {
            statement.execute("CREATE TABLE scanner_entries (id INT PRIMARY KEY, payload VARCHAR(255) NOT NULL)");
        }
    }

    public interface ScannerMapper {

        @Insert("INSERT INTO scanner_entries (id, payload) VALUES (#{id}, #{value})")
        int insert(@Param("id") int id, @Param("value") String value);

        @Select("SELECT payload FROM scanner_entries WHERE id = #{id}")
        String findValue(@Param("id") int id);
    }
}
