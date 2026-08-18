/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_bean;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Proxy;

import org.junit.jupiter.api.Test;
import org.sonatype.guice.bean.containers.SisuGuice;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

public class SisuGuiceTest {
    @Test
    void enhanceWrapsInjectorInDynamicProxy() {
        Injector injector = Guice.createInjector(new TestModule());

        Injector enhancedInjector = SisuGuice.enhance(injector);

        Class<?> enhancedType = enhancedInjector.getClass();
        assertThat(Proxy.isProxyClass(enhancedType)).isTrue();
        assertThat(Injector.class.isAssignableFrom(enhancedType)).isTrue();
    }

    private static final class TestModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(Service.class).toInstance(new Service());
        }
    }

    private static final class Service {
    }
}
