/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_jboss_resteasy.resteasy_core_spi;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

import org.jboss.resteasy.spi.PriorityServiceLoader;
import org.junit.jupiter.api.Test;

public class PriorityServiceLoaderInnerHolderTest {
    @Test
    void firstCreatesServiceWithPublicNoArgumentConstructor() throws Exception {
        ClassLoader classLoader = new SingleServiceClassLoader(HolderService.class, PublicHolderService.class);

        PriorityServiceLoader<HolderService> loader = PriorityServiceLoader.load(HolderService.class, classLoader);

        HolderService service = loader.first().orElseThrow();
        assertThat(service).isInstanceOf(PublicHolderService.class);
        assertThat(service.name()).isEqualTo("constructed by Holder");
    }

    public interface HolderService {
        String name();
    }

    public static final class PublicHolderService implements HolderService {
        public PublicHolderService() {
        }

        @Override
        public String name() {
            return "constructed by Holder";
        }
    }

    private static final class SingleServiceClassLoader extends ClassLoader {
        private final Class<?> serviceType;
        private final Class<?> implementationType;

        private SingleServiceClassLoader(Class<?> serviceType, Class<?> implementationType) {
            super(PriorityServiceLoaderInnerHolderTest.class.getClassLoader());
            this.serviceType = serviceType;
            this.implementationType = implementationType;
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            String expectedName = "META-INF/services/" + serviceType.getName();
            if (!expectedName.equals(name)) {
                return Collections.emptyEnumeration();
            }
            return Collections.enumeration(List.of(serviceConfigurationUrl()));
        }

        private URL serviceConfigurationUrl() throws IOException {
            return new URL(null, "memory:priority-service-loader-holder", new URLStreamHandler() {
                @Override
                protected URLConnection openConnection(URL url) {
                    return new URLConnection(url) {
                        @Override
                        public void connect() {
                        }

                        @Override
                        public InputStream getInputStream() {
                            return new ByteArrayInputStream(
                                    implementationType.getName().getBytes(StandardCharsets.UTF_8));
                        }
                    };
                }
            });
        }
    }
}
