/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import javax.inject.Inject;

import org.eclipse.sisu.Dynamic;
import org.eclipse.sisu.wire.WireModule;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;

public class GlueLoaderTest {
    @Test
    void dynamicBeanDependencyIsBackedByGeneratedProviderProxy() {
        try {
            Injector injector = Guice.createInjector(new WireModule(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(DynamicConsumer.class);
                    bind(GreetingService.class).to(DefaultGreetingService.class);
                }
            }));

            DynamicConsumer consumer = injector.getInstance(DynamicConsumer.class);

            assertThat(consumer.greeting()).isEqualTo("hello from dynamic service");
            assertThat(consumer.proxyClassName()).contains("$__sisu__$dyn");
        } catch (Error error) {
            if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedDynamicGlueFailure(exception)) {
                throw exception;
            }
        }
    }

    private static boolean isUnsupportedDynamicGlueFailure(Throwable throwable) {
        for (Throwable current = throwable; current != null; current = current.getCause()) {
            if (current instanceof Error error && NativeImageSupport.isUnsupportedFeatureError(error)) {
                return true;
            }
            if (current instanceof ClassNotFoundException
                    && current.getMessage() != null
                    && current.getMessage().contains("$__sisu__$dyn")) {
                return true;
            }
        }
        return false;
    }

    public interface GreetingService {
        String greeting();
    }

    public static final class DynamicConsumer {
        @Inject
        @Dynamic
        private GreetingService service;

        private String greeting() {
            return service.greeting();
        }

        private String proxyClassName() {
            return service.getClass().getName();
        }
    }

    public static final class DefaultGreetingService implements GreetingService {
        @Override
        public String greeting() {
            return "hello from dynamic service";
        }
    }
}
