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
import org.hibernate.Transaction;
import org.hibernate.annotations.NaturalId;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.collection.spi.PersistentArrayHolder;
import org.hibernate.collection.spi.PersistentBag;
import org.hibernate.engine.internal.StatefulPersistenceContext;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.EntityUniqueKey;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.engine.spi.Status;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.Type;
import org.junit.jupiter.api.Test;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class StatefulPersistenceContextTest {

    @Test
    public void serializeAndDeserializePersistenceContextWithTrackedState() throws Exception {
        StandardServiceRegistry serviceRegistry = createServiceRegistry();
        SessionFactory sessionFactory = null;
        try {
            sessionFactory = new MetadataSources(serviceRegistry)
                    .addAnnotatedClass(StatefulContextEntity.class)
                    .addAnnotatedClass(StatefulContextChild.class)
                    .buildMetadata()
                    .buildSessionFactory();

            persistTestData(sessionFactory);

            SessionFactoryImplementor sessionFactoryImplementor = (SessionFactoryImplementor) sessionFactory;
            try (Session hibernateSession = sessionFactory.openSession()) {
                SessionImplementor session = (SessionImplementor) hibernateSession;
                StatefulPersistenceContext persistenceContext = createPopulatedPersistenceContext(
                        session,
                        sessionFactoryImplementor
                );

                byte[] serialized = serialize(persistenceContext);
                StatefulPersistenceContext deserialized = deserialize(serialized, session);

                EntityPersister entityPersister = sessionFactoryImplementor
                        .getRuntimeMetamodels()
                        .getMappingMetamodel()
                        .getEntityDescriptor(StatefulContextEntity.class);
                EntityKey entityKey = session.generateEntityKey(1L, entityPersister);

                StatefulContextEntity deserializedEntity = (StatefulContextEntity) deserialized.getEntity(entityKey);
                assertThat(serialized).isNotEmpty();
                assertThat(deserializedEntity.getCode()).isEqualTo("entity-one");
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
                .applySetting("hibernate.connection.url", "jdbc:h2:mem:stateful-context;DB_CLOSE_DELAY=-1")
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
                .build();
    }

    private static void persistTestData(SessionFactory sessionFactory) {
        try (Session session = sessionFactory.openSession()) {
            Transaction transaction = session.beginTransaction();
            StatefulContextEntity entity = new StatefulContextEntity(1L, "entity-one");
            entity.addChild(new StatefulContextChild(10L, "child-one", entity));
            session.persist(entity);
            transaction.commit();
        }
    }

    private static StatefulPersistenceContext createPopulatedPersistenceContext(
            SessionImplementor session,
            SessionFactoryImplementor sessionFactory) {
        EntityPersister entityPersister = sessionFactory
                .getRuntimeMetamodels()
                .getMappingMetamodel()
                .getEntityDescriptor(StatefulContextEntity.class);
        CollectionPersister collectionPersister = sessionFactory
                .getRuntimeMetamodels()
                .getMappingMetamodel()
                .streamCollectionDescriptors()
                .filter(persister -> persister.getRole().endsWith(".children"))
                .findFirst()
                .orElseThrow();

        StatefulPersistenceContext persistenceContext = new StatefulPersistenceContext(session);
        StatefulContextEntity entity = new StatefulContextEntity(1L, "entity-one");
        StatefulContextChild child = new StatefulContextChild(10L, "child-one", entity);
        entity.addChild(child);

        EntityKey entityKey = session.generateEntityKey(entity.getId(), entityPersister);
        Object[] loadedState = entityPersister.getPropertyValues(entity);
        persistenceContext.addEntity(
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
        persistenceContext.getDatabaseSnapshot(entity.getId(), entityPersister);

        Type naturalIdType = entityPersister.getPropertyType("code");
        EntityUniqueKey uniqueKey = new EntityUniqueKey(
                entityPersister.getEntityName(),
                "code",
                entity.getCode(),
                naturalIdType,
                sessionFactory
        );
        persistenceContext.addEntity(uniqueKey, entity);
        persistenceContext.addProxy(entityKey, "serialized pruned proxy marker");

        PersistentBag<StatefulContextChild> children = new PersistentBag<>(session, entity.getChildren());
        persistenceContext.addInitializedCollection(collectionPersister, children, entity.getId());

        String[] labels = new String[] {"first", "second"};
        PersistentArrayHolder<String> arrayHolder = new PersistentArrayHolder<>(session, labels);
        persistenceContext.addCollectionHolder(arrayHolder);
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

    @Entity(name = "StatefulContextEntity")
    @Table(name = "STATEFUL_CONTEXT_ENTITY")
    public static class StatefulContextEntity implements Serializable {
        private static final long serialVersionUID = 1L;

        @Id
        private Long id;

        @NaturalId(mutable = true)
        private String code;

        @OneToMany(mappedBy = "parent", cascade = CascadeType.ALL)
        private List<StatefulContextChild> children = new ArrayList<>();

        public StatefulContextEntity() {
        }

        public StatefulContextEntity(Long id, String code) {
            this.id = id;
            this.code = code;
        }

        public Long getId() {
            return id;
        }

        public String getCode() {
            return code;
        }

        public List<StatefulContextChild> getChildren() {
            return children;
        }

        public void addChild(StatefulContextChild child) {
            children.add(child);
        }
    }

    @Entity(name = "StatefulContextChild")
    @Table(name = "STATEFUL_CONTEXT_CHILD")
    public static class StatefulContextChild implements Serializable {
        private static final long serialVersionUID = 1L;

        @Id
        private Long id;

        private String name;

        @ManyToOne
        private StatefulContextEntity parent;

        public StatefulContextChild() {
        }

        public StatefulContextChild(Long id, String name, StatefulContextEntity parent) {
            this.id = id;
            this.name = name;
            this.parent = parent;
        }

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public StatefulContextEntity getParent() {
            return parent;
        }
    }
}
