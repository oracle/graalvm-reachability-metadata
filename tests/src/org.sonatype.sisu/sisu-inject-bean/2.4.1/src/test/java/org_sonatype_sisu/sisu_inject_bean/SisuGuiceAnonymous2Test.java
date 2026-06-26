/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_sonatype_sisu.sisu_inject_bean;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.sonatype.guice.bean.containers.SisuGuice;

import com.google.inject.AbstractModule;
import com.google.inject.Binding;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;

public class SisuGuiceAnonymous2Test {
    @Test
    void enhancedInjectorDelegatesRegularInjectorMethods() {
        Injector injector = Guice.createInjector(new ServiceModule());
        Injector enhancedInjector = SisuGuice.enhance(injector);

        Map<Key<?>, Binding<?>> bindings = enhancedInjector.getBindings();

        assertThat(bindings).containsKey(Key.get(Service.class));
    }

    private static final class ServiceModule extends AbstractModule {
        @Override
        protected void configure() {
            bind(Service.class).toInstance(new Service());
        }
    }

    private static final class Service {
    }
}
