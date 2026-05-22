/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_jetty.jetty_servlet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import javax.el.BeanELResolver;

import org.eclipse.jetty.servlet.listener.ELContextCleaner;
import org.junit.jupiter.api.Test;

public class ELContextCleanerTest {
    @Test
    public void contextDestroyedInspectsBeanELResolverPropertiesCache() {
        ClassLoader originalClassLoader = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(ELContextCleanerTest.class.getClassLoader());
        try {
            BeanELResolver resolver = new BeanELResolver();
            ELContextCleaner cleaner = new ELContextCleaner();

            assertThat(resolver).isNotNull();
            assertThatCode(() -> cleaner.contextDestroyed(null)).doesNotThrowAnyException();
        } finally {
            Thread.currentThread().setContextClassLoader(originalClassLoader);
        }
    }
}
