/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.LockMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.Immutable;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.internal.ImmutableEntityEntry;
import org.hibernate.engine.internal.StatefulPersistenceContext;
import org.hibernate.engine.spi.EntityEntry;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.Status;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import static org.assertj.core.api.Assertions.assertThat;

public class ImmutableEntityEntryTest {

    @Test
    public void serializeAndDeserializePersistenceContextWithImmutableEntityEntry() throws Exception {
        StandardServiceRegistry serviceRegistry = createServiceRegistry();
        SessionFactory sessionFactory = null;
        try {
            sessionFactory = new MetadataSources(serviceRegistry)
                    .addAnnotatedClass(ImmutableEntryEntity.class)
                    .buildMetadata()
                    .buildSessionFactory();

            SessionFactoryImplementor sessionFactoryImplementor = (SessionFactoryImplementor) sessionFactory;
            try (Session hibernateSession = sessionFactory.openSession()) {
                SessionImplementor session = (SessionImplementor) hibernateSession;
                StatefulPersistenceContext persistenceContext = createPersistenceContextWithImmutableEntry(
                        session,
                        sessionFactoryImplementor
                );

                byte[] serialized = serialize(persistenceContext);
                StatefulPersistenceContext deserialized = deserialize(serialized, session);

                EntityPersister entityPersister = sessionFactoryImplementor
                        .getRuntimeMetamodels()
                        .getMappingMetamodel()
                        .getEntityDescriptor(ImmutableEntryEntity.class);
                EntityKey entityKey = session.generateEntityKey(1L, entityPersister);
                ImmutableEntryEntity deserializedEntity = (ImmutableEntryEntity) deserialized.getEntity(entityKey);
                EntityEntry deserializedEntry = deserialized.getEntry(deserializedEntity);

                assertThat(serialized).isNotEmpty();
                assertThat(deserializedEntity.getName()).isEqualTo("immutable-one");
                assertThat(deserializedEntry).isInstanceOf(ImmutableEntityEntry.class);
                assertThat(deserializedEntry.getId()).isEqualTo(1L);
                assertThat(deserializedEntry.getStatus()).isEqualTo(Status.MANAGED);
                assertThat(deserializedEntry.getLoadedState()).containsExactly("immutable-one");
            }
        }
        finally {
            if (sessionFactory != null) {
                sessionFactory.close();
            }
            StandardServiceRegistryBuilder.destroy(serviceRegistry);
        }
    }

    private static StandardServiceRegistry createServiceRegistry() {
        return new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.url", "jdbc:h2:mem:immutable-entry;DB_CLOSE_DELAY=-1")
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
                .build();
    }

    private static StatefulPersistenceContext createPersistenceContextWithImmutableEntry(
            SessionImplementor session,
            SessionFactoryImplementor sessionFactory) {
        EntityPersister entityPersister = sessionFactory
                .getRuntimeMetamodels()
                .getMappingMetamodel()
                .getEntityDescriptor(ImmutableEntryEntity.class);
        StatefulPersistenceContext persistenceContext = new StatefulPersistenceContext(session);
        ImmutableEntryEntity entity = new ImmutableEntryEntity(1L, "immutable-one");
        EntityKey entityKey = session.generateEntityKey(entity.getId(), entityPersister);
        Object[] loadedState = entityPersister.getPropertyValues(entity);

        EntityEntry entry = persistenceContext.addEntity(
                entity,
                Status.MANAGED,
                loadedState,
                entityKey,
                null,
                LockMode.NONE,
                true,
                entityPersister,
                false
        );

        assertThat(entry).isInstanceOf(ImmutableEntityEntry.class);
        return persistenceContext;
    }

    private static byte[] serialize(StatefulPersistenceContext persistenceContext) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(bytes)) {
            persistenceContext.serialize(objectOutputStream);
        }
        return bytes.toByteArray();
    }

    private static StatefulPersistenceContext deserialize(byte[] bytes, SessionImplementor session)
            throws IOException, ClassNotFoundException {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(new ByteArrayInputStream(bytes))) {
            return StatefulPersistenceContext.deserialize(objectInputStream, session);
        }
    }

    @Entity(name = "ImmutableEntryEntity")
    @Immutable
    @Table(name = "IMMUTABLE_ENTRY_ENTITY")
    public static class ImmutableEntryEntity implements Serializable {
        private static final long serialVersionUID = 1L;

        @Id
        private Long id;

        private String name;

        public ImmutableEntryEntity() {
        }

        public ImmutableEntryEntity(Long id, String name) {
            this.id = id;
            this.name = name;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
