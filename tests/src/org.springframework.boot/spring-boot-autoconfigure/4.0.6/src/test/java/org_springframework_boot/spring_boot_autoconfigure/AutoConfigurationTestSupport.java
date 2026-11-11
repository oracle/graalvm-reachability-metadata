/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import org.springframework.beans.factory.support.DefaultListableBeanFactory;
import org.springframework.boot.autoconfigure.AutoConfigurationImportSelector;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.core.io.DefaultResourceLoader;

final class AutoConfigurationTestSupport {
    private AutoConfigurationTestSupport() {
    }

    static AutoConfigurationImportSelector selector(ClassLoader classLoader) {
        AutoConfigurationImportSelector selector = new AutoConfigurationImportSelector();
        selector.setBeanClassLoader(classLoader);
        selector.setBeanFactory(new DefaultListableBeanFactory());
        selector.setEnvironment(new StandardEnvironment());
        selector.setResourceLoader(new DefaultResourceLoader(classLoader));
        return selector;
    }
}
