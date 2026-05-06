/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package javax_persistence.persistence_api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import javax.persistence.EntityManager;
import javax.persistence.EntityManagerFactory;
import javax.persistence.Persistence;
import javax.persistence.spi.PersistenceProvider;
import javax.persistence.spi.PersistenceUnitInfo;

import org.junit.jupiter.api.Test;

public class PersistenceTest {

    @Test
    void createEntityManagerFactoryDiscoversProviderFromServiceResource() {
        DiscoveredPersistenceProvider.reset();
        Map<String, Object> properties = Map.of("javax.persistence.provider", "test-provider");

        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("service-unit", properties);

        assertThat(entityManagerFactory).isInstanceOf(StubEntityManagerFactory.class);
        assertThat(entityManagerFactory.isOpen()).isTrue();
        assertThat(DiscoveredPersistenceProvider.instanceCount()).isEqualTo(1);
        assertThat(DiscoveredPersistenceProvider.lastPersistenceUnitName()).isEqualTo("service-unit");
        assertThat(DiscoveredPersistenceProvider.lastProperties()).isSameAs(properties);
    }

    public static final class DiscoveredPersistenceProvider implements PersistenceProvider {
        private static final AtomicInteger INSTANCE_COUNT = new AtomicInteger();
        private static final AtomicReference<String> LAST_PERSISTENCE_UNIT_NAME = new AtomicReference<>();
        private static final AtomicReference<Map<?, ?>> LAST_PROPERTIES = new AtomicReference<>();

        public DiscoveredPersistenceProvider() {
            INSTANCE_COUNT.incrementAndGet();
        }

        static void reset() {
            INSTANCE_COUNT.set(0);
            LAST_PERSISTENCE_UNIT_NAME.set(null);
            LAST_PROPERTIES.set(null);
        }

        static int instanceCount() {
            return INSTANCE_COUNT.get();
        }

        static String lastPersistenceUnitName() {
            return LAST_PERSISTENCE_UNIT_NAME.get();
        }

        static Map<?, ?> lastProperties() {
            return LAST_PROPERTIES.get();
        }

        @Override
        public EntityManagerFactory createEntityManagerFactory(String emName, Map map) {
            LAST_PERSISTENCE_UNIT_NAME.set(emName);
            LAST_PROPERTIES.set(map);
            return new StubEntityManagerFactory();
        }

        @Override
        public EntityManagerFactory createContainerEntityManagerFactory(PersistenceUnitInfo info, Map map) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class StubEntityManagerFactory implements EntityManagerFactory {
        private boolean open = true;

        @Override
        public EntityManager createEntityManager() {
            throw new UnsupportedOperationException();
        }

        @Override
        public EntityManager createEntityManager(Map map) {
            throw new UnsupportedOperationException();
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
