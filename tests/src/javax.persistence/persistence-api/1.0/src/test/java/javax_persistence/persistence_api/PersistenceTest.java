/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_persistence.persistence_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URL;
import java.net.URLClassLoader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

public class PersistenceTest {

    private final ClassLoader originalContextClassLoader = Thread.currentThread().getContextClassLoader();

    @AfterEach
    void restoreContextClassLoader() {
        Thread.currentThread().setContextClassLoader(originalContextClassLoader);
    }

    @Test
    void createEntityManagerFactoryDiscoversProvidersFromContextClassLoaderServices() throws Exception {
        Path servicesRoot = Files.createTempDirectory("jpa-services");
        Path serviceFile = servicesRoot.resolve("META-INF/services/" + PersistenceProvider.class.getName());
        Files.createDirectories(serviceFile.getParent());
        Files.writeString(serviceFile, ServiceLoadedPersistenceProvider.class.getName() + System.lineSeparator(), StandardCharsets.UTF_8);

        try (URLClassLoader serviceClassLoader = new URLClassLoader(new URL[]{servicesRoot.toUri().toURL()}, getClass().getClassLoader())) {
            Thread.currentThread().setContextClassLoader(serviceClassLoader);
            Map<String, String> properties = Map.of("javax.persistence.provider", "service-loaded");

            EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("service-discovered-unit", properties);

            assertThat(entityManagerFactory).isInstanceOf(SimpleEntityManagerFactory.class);
            assertThat(ServiceLoadedPersistenceProvider.constructorCalls).hasValue(1);
            assertThat(ServiceLoadedPersistenceProvider.entityManagerFactoryCalls).hasValue(1);
            assertThat(ServiceLoadedPersistenceProvider.lastUnitName).isEqualTo("service-discovered-unit");
            assertThat(ServiceLoadedPersistenceProvider.lastProperties).isSameAs(properties);
        }
    }

    public static final class ServiceLoadedPersistenceProvider implements PersistenceProvider {
        static final AtomicInteger constructorCalls = new AtomicInteger();
        static final AtomicInteger entityManagerFactoryCalls = new AtomicInteger();
        static String lastUnitName;
        static Map<?, ?> lastProperties;

        public ServiceLoadedPersistenceProvider() {
            constructorCalls.incrementAndGet();
        }

        @Override
        public EntityManagerFactory createEntityManagerFactory(String emName, Map map) {
            entityManagerFactoryCalls.incrementAndGet();
            lastUnitName = emName;
            lastProperties = map;
            return new SimpleEntityManagerFactory();
        }

        @Override
        public EntityManagerFactory createContainerEntityManagerFactory(PersistenceUnitInfo info, Map map) {
            return new SimpleEntityManagerFactory();
        }
    }

    private static final class SimpleEntityManagerFactory implements EntityManagerFactory {
        private boolean open = true;

        @Override
        public EntityManager createEntityManager() {
            throw new UnsupportedOperationException("EntityManager creation is not needed by this test");
        }

        @Override
        public EntityManager createEntityManager(Map map) {
            throw new UnsupportedOperationException("EntityManager creation is not needed by this test");
        }

        @Override
        public void close() {
            open = false;
        }

        @Override
        public boolean isOpen() {
            return open;
        }
    }
}
