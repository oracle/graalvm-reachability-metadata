/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_beans;

import java.util.Properties;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.config.ServiceLocatorFactoryBean;
import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.beans.factory.support.RootBeanDefinition;

import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertSame;

public class ServiceLocatorFactoryBeanInnerServiceLocatorInvocationHandlerTest {
    @Test
    void resolvesMappedIdentifiersAndPreservesPrototypeScope() {
        DefaultListableBeanFactory factory = new DefaultListableBeanFactory();
        RootBeanDefinition definition = new RootBeanDefinition(Job.class);
        definition.setScope("prototype");
        factory.registerBeanDefinition("job", definition);
        Properties mappings = new Properties();
        mappings.setProperty("daily", "job");
        ServiceLocatorFactoryBean locatorFactory = new ServiceLocatorFactoryBean();
        locatorFactory.setServiceLocatorInterface(JobLocator.class);
        locatorFactory.setServiceMappings(mappings);
        locatorFactory.setBeanFactory(factory);
        locatorFactory.afterPropertiesSet();
        JobLocator locator = (JobLocator) locatorFactory.getObject();
        Job first = locator.find("daily");
        assertNotSame(first, locator.find("daily"));
        assertNotSame(first, locator.current());
        assertSame(locator, locatorFactory.getObject());
    }

    public interface JobLocator {
        Job find(String id);

        Job current();
    }

    public static class Job {
    }
}
