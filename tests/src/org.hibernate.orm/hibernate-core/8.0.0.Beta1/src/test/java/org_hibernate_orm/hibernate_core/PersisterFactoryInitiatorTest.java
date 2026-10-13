/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.hibernate.cache.spi.access.CollectionDataAccess;
import org.hibernate.cache.spi.access.EntityDataAccess;
import org.hibernate.cache.spi.access.NaturalIdDataAccess;
import org.hibernate.cfg.Configuration;
import org.hibernate.mapping.Collection;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.metamodel.spi.RuntimeModelCreationContext;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.internal.PersisterFactoryImpl;
import org.hibernate.persister.internal.PersisterFactoryInitiator;
import org.hibernate.persister.spi.PersisterFactory;
import org.hibernate.service.spi.ServiceRegistryAwareService;
import org.hibernate.service.spi.ServiceRegistryImplementor;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PersisterFactoryInitiatorTest {

    private static final List<String> CREATED = new CopyOnWriteArrayList<>();

    @Test
    void customPersisterFactoryCreatesTheEntityPersisters() {
        CREATED.clear();
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Crate.class);
        configuration.setProperty(PersisterFactoryInitiator.IMPL_NAME, RecordingPersisterFactory.class.getName());

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            sessionFactory.inTransaction(session -> {
                session.persist(new Crate(1L, "first"));
                session.persist(new Crate(2L, "second"));
            });
            String name = sessionFactory.fromTransaction(session -> session.find(Crate.class, 2L).getLabel());

            assertThat(name).isEqualTo("second");
        }
        assertThat(CREATED).containsExactly(Crate.class.getName());
    }

    public static class RecordingPersisterFactory implements PersisterFactory, ServiceRegistryAwareService {

        private final PersisterFactoryImpl delegate = new PersisterFactoryImpl();

        @Override
        public void injectServices(ServiceRegistryImplementor serviceRegistry) {
            delegate.injectServices(serviceRegistry);
        }

        @Override
        public EntityPersister createEntityPersister(
                PersistentClass entityBinding,
                EntityDataAccess entityCacheAccessStrategy,
                NaturalIdDataAccess naturalIdCacheAccessStrategy,
                RuntimeModelCreationContext creationContext) {
            CREATED.add(entityBinding.getEntityName());
            return delegate.createEntityPersister(
                    entityBinding, entityCacheAccessStrategy, naturalIdCacheAccessStrategy, creationContext);
        }

        @Override
        public CollectionPersister createCollectionPersister(
                Collection collectionBinding,
                CollectionDataAccess cacheAccessStrategy,
                RuntimeModelCreationContext creationContext) {
            return delegate.createCollectionPersister(collectionBinding, cacheAccessStrategy, creationContext);
        }
    }

    @Entity(name = "Crate")
    public static class Crate {

        @Id
        private Long id;

        private String label;

        public Crate() {
        }

        public Crate(Long id, String label) {
            this.id = id;
            this.label = label;
        }

        public String getLabel() {
            return label;
        }
    }
}
