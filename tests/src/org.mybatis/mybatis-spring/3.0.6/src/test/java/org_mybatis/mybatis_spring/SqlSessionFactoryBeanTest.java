/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis_spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.ResultSet;
import java.sql.Statement;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.context.support.GenericApplicationContext;

public class SqlSessionFactoryBeanTest {

    @Test
    void createsUsableSessionFactoryInSpringContext() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:spring_factory");
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.registerBean("sessionFactory", SqlSessionFactoryBean.class, () -> {
                SqlSessionFactoryBean factoryBean = new SqlSessionFactoryBean();
                factoryBean.setDataSource(dataSource);
                return factoryBean;
            });
            context.refresh();

            SqlSessionFactory factory = context.getBean("sessionFactory", SqlSessionFactory.class);
            assertThat(context.getBean("sessionFactory")).isSameAs(factory);
            assertThat(factory.getConfiguration().getEnvironment().getDataSource()).isSameAs(dataSource);
            try (SqlSession session = factory.openSession();
                    Statement statement = session.getConnection().createStatement();
                    ResultSet result = statement.executeQuery("SELECT 17")) {
                assertThat(result.next()).isTrue();
                assertThat(result.getInt(1)).isEqualTo(17);
                assertThat(result.next()).isFalse();
            }
        }
    }
}
