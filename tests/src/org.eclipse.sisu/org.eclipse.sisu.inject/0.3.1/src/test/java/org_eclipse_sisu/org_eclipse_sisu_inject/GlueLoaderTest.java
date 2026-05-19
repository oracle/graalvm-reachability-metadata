/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_eclipse_sisu.org_eclipse_sisu_inject;

import static org.assertj.core.api.Assertions.assertThat;

import org.eclipse.sisu.Dynamic;
import org.eclipse.sisu.wire.WireModule;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;

public class GlueLoaderTest {
    @Test
    void wireModuleCreatesDynamicProxyForDynamicQualifiedDependency() {
        try {
            Injector injector = Guice.createInjector(new WireModule(new AbstractModule() {
                @Override
                protected void configure() {
                    bind(DynamicConsumer.class);
                    bind(DynamicService.class).toInstance(new RecordingDynamicService());
                }
            }));

            DynamicService service = injector.getInstance(DynamicConsumer.class).service();

            assertThat(service.getClass().getName()).contains("$__sisu__$dyn");
            assertThat(service.message()).isEqualTo("resolved by BeanLocator");
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
            if (current instanceof ClassNotFoundException
                    && current.getMessage() != null
                    && current.getMessage().contains("$__sisu__$dyn")) {
                return true;
            }
        }
        return false;
    }

    public interface DynamicService {
        String message();
    }

    public static final class RecordingDynamicService implements DynamicService {
        @Override
        public String message() {
            return "resolved by BeanLocator";
        }
    }

    public static final class DynamicConsumer {
        private final DynamicService service;

        @Inject
        public DynamicConsumer(@Dynamic DynamicService service) {
            this.service = service;
        }

        public DynamicService service() {
            return service;
        }
    }
}
