/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.Session;
import org.hibernate.collection.spi.PersistentBag;
import org.hibernate.engine.internal.StatefulPersistenceContext;
import org.hibernate.engine.spi.CollectionKey;
import org.hibernate.engine.spi.EntityKey;
import org.hibernate.engine.spi.EntityUniqueKey;
import org.hibernate.engine.spi.SessionImplementor;
import org.hibernate.persister.collection.CollectionPersister;
import org.hibernate.persister.entity.EntityPersister;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org_hibernate.hibernate_core.entity.Gender;
import org_hibernate.hibernate_core.entity.Student;
import org_hibernate.hibernate_core.entity.Teacher;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class StatefulPersistenceContextTest {

    private EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;

    @BeforeEach
    public void init() {
        Map<String, String> properties = new HashMap<>();
        properties.put(
                "jakarta.persistence.jdbc.url",
                "jdbc:h2:mem:stateful-persistence-context-" + UUID.randomUUID() + ";DB_CLOSE_DELAY=-1");
        properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        entityManagerFactory = Persistence.createEntityManagerFactory("StudentPU", properties);
        entityManager = entityManagerFactory.createEntityManager();
    }

    @AfterEach
    public void close() {
        entityManager.close();
        entityManagerFactory.close();
    }

    @Test
    public void serializesPopulatedPersistenceContextSections() throws Exception {
        Long studentId = persistStudent();
        SessionImplementor session = unwrapSession();
        StatefulPersistenceContext persistenceContext = new StatefulPersistenceContext(session);
        EntityPersister studentPersister = entityPersister(session, Student.class);
        EntityKey studentKey = session.generateEntityKey(studentId, studentPersister);

        persistenceContext.getDatabaseSnapshot(studentId, studentPersister);
        persistenceContext.addEntity(studentKey, "entity-by-key");
        persistenceContext.addEntity(uniqueStudentKey(session, studentPersister, studentId), "entity-by-unique-key");
        persistenceContext.addProxy(session.generateEntityKey(studentId + 1, studentPersister), "pruned-proxy");

        CollectionPersister coursesPersister = collectionPersister(session, Teacher.class.getName() + ".courses");
        PersistentBag<String> courses = new PersistentBag<>(session, new ArrayList<>());
        Long collectionOwnerId = studentId + 2;
        persistenceContext.addInitializedCollection(coursesPersister, courses, collectionOwnerId);
        persistenceContext.addCollectionHolder(courses);

        byte[] serializedPersistenceContext = serialize(persistenceContext);
        StatefulPersistenceContext restoredPersistenceContext = deserialize(serializedPersistenceContext, session);

        assertThat(restoredPersistenceContext.getEntity(studentKey)).isEqualTo("entity-by-key");
        EntityUniqueKey restoredUniqueKey = deserializedUniqueStudentKey(session, studentPersister, studentId);
        assertThat(restoredPersistenceContext.getEntity(restoredUniqueKey)).isEqualTo("entity-by-unique-key");
        assertThat(restoredPersistenceContext.getProxy(session.generateEntityKey(studentId + 1, studentPersister)))
                .isNull();
        assertThat(restoredPersistenceContext.getCachedDatabaseSnapshot(studentKey)).isNotNull();
        assertThat(restoredPersistenceContext.getCollection(new CollectionKey(coursesPersister, collectionOwnerId)))
                .isNotNull();
    }

    private Long persistStudent() {
        Student student = new Student();
        student.setFirstName("Stateful");
        student.setLastName("Context");
        student.setAge(34);
        student.setBirthDate(new Date());
        student.setGender(Gender.MALE);

        entityManager.getTransaction().begin();
        entityManager.persist(student);
        entityManager.getTransaction().commit();
        entityManager.clear();
        return student.getId();
    }

    private SessionImplementor unwrapSession() {
        Session session = entityManager.unwrap(Session.class);
        return (SessionImplementor) session;
    }

    private EntityPersister entityPersister(SessionImplementor session, Class<?> entityClass) {
        return session.getFactory()
                .getRuntimeMetamodels()
                .getMappingMetamodel()
                .getEntityDescriptor(entityClass);
    }

    private CollectionPersister collectionPersister(SessionImplementor session, String role) {
        return session.getFactory()
                .getRuntimeMetamodels()
                .getMappingMetamodel()
                .getCollectionDescriptor(role);
    }

    private EntityUniqueKey uniqueStudentKey(
            SessionImplementor session,
            EntityPersister studentPersister,
            Long studentId) {
        return new EntityUniqueKey(
                studentPersister.getEntityName(),
                "id",
                studentId,
                studentPersister.getIdentifierType(),
                session.getFactory());
    }

    private EntityUniqueKey deserializedUniqueStudentKey(
            SessionImplementor session,
            EntityPersister studentPersister,
            Long studentId) {
        return new EntityUniqueKey(
                "id",
                studentPersister.getEntityName(),
                studentId,
                studentPersister.getIdentifierType(),
                session.getFactory());
    }

    private byte[] serialize(StatefulPersistenceContext persistenceContext) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream objectOutputStream = new ObjectOutputStream(bytes)) {
            persistenceContext.serialize(objectOutputStream);
        }
        return bytes.toByteArray();
    }

    private StatefulPersistenceContext deserialize(byte[] serializedPersistenceContext, SessionImplementor session)
            throws Exception {
        try (ObjectInputStream objectInputStream = new ObjectInputStream(
                new ByteArrayInputStream(serializedPersistenceContext))) {
            return StatefulPersistenceContext.deserialize(objectInputStream, session);
        }
    }
}
