/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_opentelemetry.opentelemetry_common;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatNullPointerException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.opentelemetry.common.ComponentLoader;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.net.URLConnection;
import java.net.URLStreamHandler;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ServiceConfigurationError;
import org.junit.jupiter.api.Test;

public class Opentelemetry_commonTest {
    private static final String serviceResource = "META-INF/services/" + GreetingService.class.getName();

    @Test
    void componentLoaderUsesSuppliedClassLoaderToDiscoverAndInstantiateProviders() {
        ComponentLoader componentLoader = ComponentLoader.forClassLoader(new ServiceResourceClassLoader("""
                # Comments and blank lines are ignored by ServiceLoader.

                %s
                %s
                """.formatted(EnglishGreetingService.class.getName(), FrenchGreetingService.class.getName())));

        Iterable<GreetingService> services = componentLoader.load(GreetingService.class);

        assertThat(services)
                .extracting(service -> service.greet("OpenTelemetry"))
                .containsExactly("Hello, OpenTelemetry", "Bonjour, OpenTelemetry");
    }

    @Test
    void componentLoaderCombinesProvidersFromEveryServiceResource() {
        ComponentLoader componentLoader = ComponentLoader.forClassLoader(new MultiServiceResourceClassLoader(List.of(
                EnglishGreetingService.class.getName() + "\n",
                "%s\n%s\n".formatted(EnglishGreetingService.class.getName(), FrenchGreetingService.class.getName()))));

        Iterable<GreetingService> services = componentLoader.load(GreetingService.class);

        assertThat(services)
                .extracting(service -> service.greet("OpenTelemetry"))
                .containsExactly("Hello, OpenTelemetry", "Bonjour, OpenTelemetry");
    }

    @Test
    void componentLoaderLazilyInstantiatesAndCachesProvidersWithinOneLoadCall() {
        CountingGreetingService.createdInstances = 0;
        ComponentLoader componentLoader = ComponentLoader.forClassLoader(new ServiceResourceClassLoader(
                CountingGreetingService.class.getName() + "\n"));

        Iterable<GreetingService> services = componentLoader.load(GreetingService.class);
        assertThat(CountingGreetingService.createdInstances).isZero();

        Iterator<GreetingService> firstIterator = services.iterator();
        assertThat(CountingGreetingService.createdInstances).isZero();

        GreetingService firstService = firstIterator.next();
        GreetingService cachedService = services.iterator().next();

        assertThat(firstService).isInstanceOf(CountingGreetingService.class);
        assertThat(cachedService).isSameAs(firstService);
        assertThat(CountingGreetingService.createdInstances).isEqualTo(1);
        assertThat(firstService.greet("cached")).isEqualTo("instance-1:cached");
    }

    @Test
    void componentLoaderCreatesFreshServiceLoaderForEachLoadCall() {
        CountingGreetingService.createdInstances = 0;
        ComponentLoader componentLoader = ComponentLoader.forClassLoader(new ServiceResourceClassLoader(
                CountingGreetingService.class.getName() + "\n"));

        GreetingService firstIterationService = componentLoader.load(GreetingService.class).iterator().next();
        GreetingService secondIterationService = componentLoader.load(GreetingService.class).iterator().next();

        assertThat(firstIterationService).isInstanceOf(CountingGreetingService.class);
        assertThat(secondIterationService).isInstanceOf(CountingGreetingService.class);
        assertThat(firstIterationService).isNotSameAs(secondIterationService);
        assertThat(firstIterationService.greet("first")).isEqualTo("instance-1:first");
        assertThat(secondIterationService.greet("second")).isEqualTo("instance-2:second");
    }

    @Test
    void componentLoaderRespectsServiceResourceVisibilityOfTheClassLoader() {
        ComponentLoader emptyLoader = ComponentLoader.forClassLoader(new EmptyServiceResourceClassLoader());
        ComponentLoader configuredLoader = ComponentLoader.forClassLoader(new ServiceResourceClassLoader(
                EnglishGreetingService.class.getName() + "\n"));

        assertThat(emptyLoader.load(GreetingService.class)).isEmpty();
        assertThat(configuredLoader.load(GreetingService.class))
                .extracting(service -> service.greet("library"))
                .containsExactly("Hello, library");
    }

    @Test
    void componentLoaderDefersInvalidProviderFailuresUntilIteration() {
        ComponentLoader componentLoader = ComponentLoader.forClassLoader(new ServiceResourceClassLoader(
                "io_opentelemetry.opentelemetry_common.DoesNotExist\n"));

        Iterable<GreetingService> services = componentLoader.load(GreetingService.class);
        Iterator<GreetingService> iterator = services.iterator();

        assertThatThrownBy(iterator::next)
                .isInstanceOf(ServiceConfigurationError.class)
                .hasMessageContaining(GreetingService.class.getName())
                .hasMessageContaining("io_opentelemetry.opentelemetry_common.DoesNotExist");
    }

    @Test
    void componentLoaderRejectsNullServiceClassLikeServiceLoader() {
        ComponentLoader componentLoader = ComponentLoader.forClassLoader(
                Opentelemetry_commonTest.class.getClassLoader());

        assertThatNullPointerException().isThrownBy(() -> componentLoader.load(null));
    }

    @Test
    void componentLoaderDescriptionIncludesTheConfiguredClassLoader() {
        ClassLoader classLoader = new EmptyServiceResourceClassLoader();
        ComponentLoader componentLoader = ComponentLoader.forClassLoader(classLoader);

        assertThat(componentLoader.toString())
                .startsWith("ServiceLoaderComponentLoader{classLoader=")
                .contains(classLoader.toString())
                .endsWith("}");
    }

    public interface GreetingService {
        String greet(String name);
    }

    public static final class EnglishGreetingService implements GreetingService {
        @Override
        public String greet(String name) {
            return "Hello, " + name;
        }
    }

    public static final class FrenchGreetingService implements GreetingService {
        @Override
        public String greet(String name) {
            return "Bonjour, " + name;
        }
    }

    public static final class CountingGreetingService implements GreetingService {
        private static int createdInstances;

        private final int instanceNumber;

        public CountingGreetingService() {
            createdInstances++;
            this.instanceNumber = createdInstances;
        }

        @Override
        public String greet(String name) {
            return "instance-" + instanceNumber + ":" + name;
        }
    }

    private static class EmptyServiceResourceClassLoader extends ClassLoader {
        EmptyServiceResourceClassLoader() {
            super(Opentelemetry_commonTest.class.getClassLoader());
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (serviceResource.equals(name)) {
                return Collections.emptyEnumeration();
            }
            return super.getResources(name);
        }

        @Override
        public String toString() {
            return "empty-service-resource-class-loader";
        }
    }

    private static final class ServiceResourceClassLoader extends EmptyServiceResourceClassLoader {
        private final String serviceFileContent;

        private ServiceResourceClassLoader(String serviceFileContent) {
            this.serviceFileContent = serviceFileContent;
        }

        @Override
        public URL getResource(String name) {
            if (serviceResource.equals(name)) {
                return serviceResourceUrl(serviceFileContent, 0);
            }
            return super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (serviceResource.equals(name)) {
                return Collections.enumeration(List.of(serviceResourceUrl(serviceFileContent, 0)));
            }
            return super.getResources(name);
        }

        @Override
        public String toString() {
            return "service-resource-class-loader";
        }
    }

    private static final class MultiServiceResourceClassLoader extends EmptyServiceResourceClassLoader {
        private final List<String> serviceFileContents;

        private MultiServiceResourceClassLoader(List<String> serviceFileContents) {
            this.serviceFileContents = serviceFileContents;
        }

        @Override
        public URL getResource(String name) {
            if (serviceResource.equals(name)) {
                return serviceResourceUrl(serviceFileContents.get(0), 0);
            }
            return super.getResource(name);
        }

        @Override
        public Enumeration<URL> getResources(String name) throws IOException {
            if (!serviceResource.equals(name)) {
                return super.getResources(name);
            }

            List<URL> serviceResourceUrls = new ArrayList<>();
            for (int i = 0; i < serviceFileContents.size(); i++) {
                serviceResourceUrls.add(serviceResourceUrl(serviceFileContents.get(i), i));
            }
            return Collections.enumeration(serviceResourceUrls);
        }

        @Override
        public String toString() {
            return "multi-service-resource-class-loader";
        }
    }

    private static URL serviceResourceUrl(String serviceFileContent, int index) {
        try {
            return new URL(null, "memory:" + serviceResource + "?" + index,
                    new InMemoryUrlStreamHandler(serviceFileContent));
        } catch (IOException e) {
            throw new IllegalStateException("Unable to create in-memory service resource URL", e);
        }
    }

    private static final class InMemoryUrlStreamHandler extends URLStreamHandler {
        private final String content;

        private InMemoryUrlStreamHandler(String content) {
            this.content = content;
        }

        @Override
        protected URLConnection openConnection(URL url) {
            return new URLConnection(url) {
                @Override
                public void connect() {
                }

                @Override
                public InputStream getInputStream() {
                    return new ByteArrayInputStream(content.getBytes(StandardCharsets.UTF_8));
                }
            };
        }
    }
}
