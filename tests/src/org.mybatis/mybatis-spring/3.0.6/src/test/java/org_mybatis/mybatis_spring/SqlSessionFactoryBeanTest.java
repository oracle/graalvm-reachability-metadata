/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_mybatis.mybatis_spring;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.Statement;

import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.xml.XmlBeanDefinitionReader;
import org.springframework.context.support.GenericApplicationContext;
import org.springframework.core.io.ByteArrayResource;

public class SqlSessionFactoryBeanTest {

    @Test
    void createsUsableSessionFactoryFromSpringXml() throws Exception {
        JdbcDataSource dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:spring_factory");
        String xml = """
                <beans xmlns="http://www.springframework.org/schema/beans">
                    <bean id="sessionFactory" class="org.mybatis.spring.SqlSessionFactoryBean">
                        <property name="dataSource" ref="dataSource"/>
                    </bean>
                </beans>
                """;
        try (GenericApplicationContext context = new GenericApplicationContext()) {
            context.getBeanFactory().registerSingleton("dataSource", dataSource);
            XmlBeanDefinitionReader reader = new XmlBeanDefinitionReader(context);
            reader.setValidationMode(XmlBeanDefinitionReader.VALIDATION_NONE);
            reader.loadBeanDefinitions(new ByteArrayResource(xml.getBytes(StandardCharsets.UTF_8)));
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
