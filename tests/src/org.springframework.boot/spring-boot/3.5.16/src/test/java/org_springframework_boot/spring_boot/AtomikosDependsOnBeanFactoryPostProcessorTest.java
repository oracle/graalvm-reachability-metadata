/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot;

import com.atomikos.icatch.jta.UserTransactionManager;
import com.atomikos.jdbc.AtomikosDataSourceBean;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.boot.jta.atomikos.AtomikosDependsOnBeanFactoryPostProcessor;

import static org.assertj.core.api.Assertions.assertThat;

public class AtomikosDependsOnBeanFactoryPostProcessorTest {

    @Test
    void makesTransactionManagerDependOnXaDataSources() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("transactionManager", new RootBeanDefinition(UserTransactionManager.class));
        beanFactory.registerBeanDefinition("ordersDataSource", new RootBeanDefinition(AtomikosDataSourceBean.class));
        beanFactory.registerBeanDefinition("billingDataSource", new RootBeanDefinition(AtomikosDataSourceBean.class));

        new AtomikosDependsOnBeanFactoryPostProcessor().postProcessBeanFactory(beanFactory);

        assertThat(beanFactory.getBeanDefinition("transactionManager").getDependsOn())
                .containsExactlyInAnyOrder("ordersDataSource", "billingDataSource");
        assertThat(beanFactory.getBeanDefinition("ordersDataSource").getDependsOn()).isNull();
    }

    @Test
    void leavesTransactionManagerUnchangedWithoutXaResources() {
        DefaultListableBeanFactory beanFactory = new DefaultListableBeanFactory();
        beanFactory.registerBeanDefinition("transactionManager", new RootBeanDefinition(UserTransactionManager.class));

        new AtomikosDependsOnBeanFactoryPostProcessor().postProcessBeanFactory(beanFactory);

        assertThat(beanFactory.getBeanDefinition("transactionManager").getDependsOn()).isNull();
    }
}
