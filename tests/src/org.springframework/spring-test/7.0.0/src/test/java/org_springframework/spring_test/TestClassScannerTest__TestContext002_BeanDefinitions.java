/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework.spring_test;

import org.springframework.aot.generate.Generated;
import org.springframework.beans.factory.config.BeanDefinition;
import org.springframework.beans.factory.support.RootBeanDefinition;

/**
 * Bean definitions for {@link TestClassScannerTest}.
 */
@Generated
public class TestClassScannerTest__TestContext002_BeanDefinitions {
    /**
     * Bean definitions for {@link TestClassScannerTest.Config}.
     */
    @Generated
    public static class Config {
        /**
         * Get the bean definition for `config`.
         */
        public static BeanDefinition getConfigBeanDefinition() {
            RootBeanDefinition beanDefinition = new RootBeanDefinition(TestClassScannerTest.Config.class);
            beanDefinition.setInstanceSupplier(TestClassScannerTest.Config::new);
            return beanDefinition;
        }
    }
}
