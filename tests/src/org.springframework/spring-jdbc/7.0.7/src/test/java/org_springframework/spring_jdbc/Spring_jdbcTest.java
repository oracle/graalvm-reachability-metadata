/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_jdbc;

import java.sql.Connection;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.AbstractBeanDefinition;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.springframework.jdbc.support.JdbcAccessor;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.assertj.core.api.Assertions.assertThat;

public class Spring_jdbcTest {
    @Test
    void wiresJdbcClientAndTransactionalInfrastructureAsSpringBeans() throws Exception {
        DefaultListableBeanFactory beans = new DefaultListableBeanFactory();
        beans.registerSingleton("physicalDataSource", JdbcTestSupport.dataSource());
        beans.registerBeanDefinition("dataSource", BeanDefinitionBuilder
                .genericBeanDefinition("org.springframework.jdbc.datasource.DelegatingDataSource")
                .addPropertyReference("targetDataSource", "physicalDataSource")
                .getBeanDefinition());
        beans.registerBeanDefinition("jdbcTemplate", BeanDefinitionBuilder
                .genericBeanDefinition("org.springframework.jdbc.core.JdbcTemplate")
                .addPropertyReference("dataSource", "dataSource")
                .addPropertyValue("queryTimeout", 15)
                .addPropertyValue("lazyInit", false)
                .getBeanDefinition());
        beans.registerBeanDefinition("transactionManager", BeanDefinitionBuilder
                .genericBeanDefinition("org.springframework.jdbc.support.JdbcTransactionManager")
                .addPropertyReference("dataSource", "dataSource")
                .addPropertyValue("lazyInit", false)
                .getBeanDefinition());
        AbstractBeanDefinition clientDefinition = BeanDefinitionBuilder
                .rootBeanDefinition(JdbcClient.class, "create")
                .addConstructorArgReference("jdbcTemplate")
                .getBeanDefinition();
        clientDefinition.setDestroyMethodName(AbstractBeanDefinition.INFER_METHOD);
        beans.registerBeanDefinition("jdbcClient", clientDefinition);

        try {
            DelegatingDataSource source = beans.getBean("dataSource", DelegatingDataSource.class);
            JdbcAccessor accessor = beans.getBean(JdbcAccessor.class);
            assertThat(accessor.getDataSource()).isSameAs(source);
            try (Connection keeper = source.getConnection()) {
                JdbcTemplate template = beans.getBean(JdbcTemplate.class);
                template.execute("CREATE TABLE settings (setting_key VARCHAR(40) PRIMARY KEY, setting_value VARCHAR(80))");
                JdbcClient client = beans.getBean(JdbcClient.class);
                TransactionTemplate transaction = new TransactionTemplate(beans.getBean(JdbcTransactionManager.class));
                transaction.setTimeout(30);

                Integer inserted = transaction.execute(status -> client.sql("""
                        INSERT INTO settings (setting_key, setting_value) VALUES (:key, :value)
                        """)
                        .param("key", "region")
                        .param("value", "west")
                        .update());
                assertThat(inserted).isEqualTo(1);
                assertThat(client.sql("SELECT setting_value FROM settings WHERE setting_key = :key")
                        .param("key", "region").query(String.class).single()).isEqualTo("west");

                transaction.executeWithoutResult(status -> {
                    assertThat(client.sql("UPDATE settings SET setting_value = ? WHERE setting_key = ?")
                            .params("east", "region").update()).isEqualTo(1);
                    status.setRollbackOnly();
                });
                assertThat(client.sql("SELECT setting_value FROM settings WHERE setting_key = ?")
                        .param("region").query(String.class).single()).isEqualTo("west");
                assertThat(keeper.isClosed()).isFalse();
            }
        } finally {
            beans.destroySingletons();
        }
    }
}
