/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import javassist.util.proxy.Proxy;
import javassist.util.proxy.ProxyFactory;
import javassist.util.proxy.RuntimeSupport;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

public class RuntimeSupportDefaultMethodHandlerTest {
    private ProxyFactory.UniqueName originalNameGenerator;
    private boolean originalUseCache;
    private boolean originalUseWriteReplace;

    @BeforeEach
    void configureProxyFactoryDefaults() {
        originalNameGenerator = ProxyFactory.nameGenerator;
        originalUseCache = ProxyFactory.useCache;
        originalUseWriteReplace = ProxyFactory.useWriteReplace;
        ProxyFactory.nameGenerator = new DeterministicNameGenerator();
        ProxyFactory.useCache = true;
        ProxyFactory.useWriteReplace = true;
    }

    @AfterEach
    void restoreProxyFactoryDefaults() {
        ProxyFactory.nameGenerator = originalNameGenerator;
        ProxyFactory.useCache = originalUseCache;
        ProxyFactory.useWriteReplace = originalUseWriteReplace;
    }

    @Test
    void proxyWithoutCustomHandlerInvokesOriginalMethodThroughDefaultHandler() throws Exception {
        ProxyFactory factory = new ProxyFactory();
        factory.setSuperclass(DefaultHandledGreeting.class);
        factory.setFilter(method -> method.getName().equals("greet"));

        DefaultHandledGreeting proxy = (DefaultHandledGreeting) factory.create(
                new Class<?>[] {String.class},
                new Object[] {"Ada"});

        assertThat(ProxyFactory.getHandler((Proxy) proxy)).isSameAs(RuntimeSupport.default_interceptor);
        assertThat(proxy.greet("Grace")).isEqualTo("Ada greets Grace");
        assertThat(proxy.name()).isEqualTo("Ada");
    }

    private static class DeterministicNameGenerator implements ProxyFactory.UniqueName {
        private int counter;

        @Override
        public String get(String classname) {
            return classname + "$$RuntimeSupportDefaultMethodHandlerTest" + counter++;
        }
    }

    public static class DefaultHandledGreeting {
        private final String name;

        public DefaultHandledGreeting(String name) {
            this.name = name;
        }

        public String name() {
            return name;
        }

        public String greet(String recipient) {
            return name + " greets " + recipient;
        }
    }
}
