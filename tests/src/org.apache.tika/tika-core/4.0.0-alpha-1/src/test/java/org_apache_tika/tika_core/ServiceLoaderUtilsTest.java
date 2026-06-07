/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_tika.tika_core;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import org.apache.tika.config.ServiceLoader;
import org.apache.tika.utils.ServiceLoaderUtils;

public class ServiceLoaderUtilsTest {

    @Test
    public void newInstanceLoadsClassWithZeroArgumentConstructor() {
        ServiceLoader serviceLoader = new ServiceLoader();

        NameConstructedService service = ServiceLoaderUtils.newInstance(
                NameConstructedService.class, serviceLoader);

        assertThat(service.getSource()).isEqualTo("class-name");
    }

    @Test
    public void newInstanceUsesServiceLoaderConstructorWhenAvailable() {
        ServiceLoader serviceLoader = new ServiceLoader();

        ServiceAwareConstructedService service = ServiceLoaderUtils.newInstance(
                ServiceAwareConstructedService.class, serviceLoader);

        assertThat(service.getServiceLoader()).isSameAs(serviceLoader);
    }

    @Test
    public void newInstanceFallsBackToZeroArgumentConstructor() {
        ServiceLoader serviceLoader = new ServiceLoader();

        FallbackConstructedService service = ServiceLoaderUtils.newInstance(
                FallbackConstructedService.class, serviceLoader);

        assertThat(service.getSource()).isEqualTo("fallback");
    }

    public static class NameConstructedService {
        private final String source;

        public NameConstructedService() {
            source = "class-name";
        }

        public String getSource() {
            return source;
        }
    }

    public static class ServiceAwareConstructedService {
        private final ServiceLoader serviceLoader;

        public ServiceAwareConstructedService(ServiceLoader serviceLoader) {
            this.serviceLoader = serviceLoader;
        }

        public ServiceLoader getServiceLoader() {
            return serviceLoader;
        }
    }

    public static class FallbackConstructedService {
        private final String source;

        public FallbackConstructedService() {
            source = "fallback";
        }

        public String getSource() {
            return source;
        }
    }
}
