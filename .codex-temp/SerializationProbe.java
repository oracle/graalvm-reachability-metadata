import jakarta.persistence.CascadeType;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import org.hibernate.LockMode;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.annotations.Immutable;
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
import org.hibernate.internal.util.collections.BoundedConcurrentHashMap;
import org.hibernate.internal.util.collections.ConcurrentReferenceHashMap;
import org.hibernate.internal.util.collections.ConcurrentReferenceHashMap.ReferenceType;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.type.Type;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

public final class SerializationProbe {

    public static void main(String[] args) throws Exception {
        dump("boundedConcurrentHashMap", SerializationProbe::serializeBoundedConcurrentHashMap);
        dump("concurrentReferenceHashMap", SerializationProbe::serializeConcurrentReferenceHashMap);
        dump("immutableEntityEntry", SerializationProbe::serializeImmutableEntityEntry);
        dump("statefulPersistenceContext", SerializationProbe::serializeStatefulPersistenceContext);
    }

    private static void dump(String label, ThrowingRunnable scenario) throws Exception {
        ClassTrackingObjectOutputStream.resetSeenClasses();
        scenario.run();
        System.out.println("=== " + label + " ===");
        for (String className : ClassTrackingObjectOutputStream.snapshot()) {
            System.out.println(className);
        }
    }

    private static void serializeBoundedConcurrentHashMap() throws Exception {
        BoundedConcurrentHashMap<String, String> map = new BoundedConcurrentHashMap<>(4, 1);
        map.put("hibernate", "orm");
        map.put("cache", "bounded");
        writeObject(map);
    }

    private static void serializeConcurrentReferenceHashMap() throws Exception {
        ConcurrentReferenceHashMap<String, String> map = new ConcurrentReferenceHashMap<>(
                8,
                ReferenceType.STRONG,
                ReferenceType.STRONG
        );
        map.put("hibernate", "orm");
        map.put("metadata", "reachability");
        writeObject(map);
    }

    private static void serializeImmutableEntityEntry() throws Exception {
        StandardServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.url", "jdbc:h2:mem:immutable-entry;DB_CLOSE_DELAY=-1")
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
                .build();
        SessionFactory sessionFactory = null;
        try {
            sessionFactory = new MetadataSources(serviceRegistry)
                    .addAnnotatedClass(ImmutableEntryEntity.class)
                    .buildMetadata()
                    .buildSessionFactory();

            SessionFactoryImplementor sessionFactoryImplementor = (SessionFactoryImplementor) sessionFactory;
            try (Session hibernateSession = sessionFactory.openSession()) {
                SessionImplementor session = (SessionImplementor) hibernateSession;
                EntityPersister entityPersister = sessionFactoryImplementor
                        .getRuntimeMetamodels()
                        .getMappingMetamodel()
                        .getEntityDescriptor(ImmutableEntryEntity.class);
                StatefulPersistenceContext persistenceContext = new StatefulPersistenceContext(session);
                ImmutableEntryEntity entity = new ImmutableEntryEntity(1L, "immutable-one");
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

                writePersistenceContext(persistenceContext);
            }
        }
        finally {
            if (sessionFactory != null) {
                sessionFactory.close();
            }
            StandardServiceRegistryBuilder.destroy(serviceRegistry);
        }
    }

    private static void serializeStatefulPersistenceContext() throws Exception {
        StandardServiceRegistry serviceRegistry = new StandardServiceRegistryBuilder()
                .applySetting("hibernate.connection.driver_class", "org.h2.Driver")
                .applySetting("hibernate.connection.url", "jdbc:h2:mem:stateful-context;DB_CLOSE_DELAY=-1")
                .applySetting("hibernate.connection.username", "sa")
                .applySetting("hibernate.connection.password", "")
                .applySetting("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                .applySetting("hibernate.hbm2ddl.auto", "create-drop")
                .applySetting("hibernate.temp.use_jdbc_metadata_defaults", "false")
                .build();
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
                EntityPersister entityPersister = sessionFactoryImplementor
                        .getRuntimeMetamodels()
                        .getMappingMetamodel()
                        .getEntityDescriptor(StatefulContextEntity.class);
                CollectionPersister collectionPersister = sessionFactoryImplementor
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
                        sessionFactoryImplementor
                );
                persistenceContext.addEntity(uniqueKey, entity);
                persistenceContext.addProxy(entityKey, "serialized pruned proxy marker");

                PersistentBag<StatefulContextChild> children = new PersistentBag<>(session, entity.getChildren());
                persistenceContext.addInitializedCollection(collectionPersister, children, entity.getId());

                String[] labels = new String[] {"first", "second"};
                PersistentArrayHolder<String> arrayHolder = new PersistentArrayHolder<>(session, labels);
                persistenceContext.addCollectionHolder(arrayHolder);

                writePersistenceContext(persistenceContext);
            }
        }
        finally {
            if (sessionFactory != null) {
                sessionFactory.close();
            }
            StandardServiceRegistryBuilder.destroy(serviceRegistry);
        }
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

    private static void writePersistenceContext(StatefulPersistenceContext persistenceContext) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ClassTrackingObjectOutputStream output = new ClassTrackingObjectOutputStream(bytes)) {
            persistenceContext.serialize(output);
        }
    }

    private static void writeObject(Object value) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ClassTrackingObjectOutputStream output = new ClassTrackingObjectOutputStream(bytes)) {
            output.writeObject(value);
        }
    }

    private interface ThrowingRunnable {
        void run() throws Exception;
    }

    private static final class ClassTrackingObjectOutputStream extends ObjectOutputStream {
        private static final LinkedHashSet<String> SEEN_CLASSES = new LinkedHashSet<>();

        private ClassTrackingObjectOutputStream(OutputStream outputStream) throws IOException {
            super(outputStream);
            enableReplaceObject(true);
        }

        private static void resetSeenClasses() {
            SEEN_CLASSES.clear();
        }

        private static Set<String> snapshot() {
            return new LinkedHashSet<>(SEEN_CLASSES);
        }

        @Override
        protected Object replaceObject(Object object) throws IOException {
            if (object != null) {
                SEEN_CLASSES.add(object.getClass().getName());
            }
            return super.replaceObject(object);
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
    }
}
