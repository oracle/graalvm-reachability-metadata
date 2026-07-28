/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;
import org_hibernate.hibernate_core.entity.ArrayOwner;
import org_hibernate.hibernate_core.entity.Course;
import org_hibernate.hibernate_core.entity.Student;
import org_hibernate.hibernate_core.entity.Teacher;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class StatefulPersistenceContextTest {

    private static final String DYNAMIC_PERSON_MAPPING = """
            <?xml version="1.0"?>
            <!DOCTYPE hibernate-mapping PUBLIC
                    "-//Hibernate/Hibernate Mapping DTD 3.0//EN"
                    "http://www.hibernate.org/dtd/hibernate-mapping-3.0.dtd">
            <hibernate-mapping>
                <class entity-name="DynamicPerson" table="DYNAMIC_PERSON" lazy="true">
                    <id name="id" type="long">
                        <generator class="native"/>
                    </id>
                    <property name="name" type="string"/>
                </class>
            </hibernate-mapping>
            """;

    @Test
    public void serializesAndDeserializesManagedSessionState() throws Exception {
        EntityManagerFactory entityManagerFactory = createEntityManagerFactory();
        try {
            TestData testData = persistTestData(entityManagerFactory);
            Session deserializedSession = serializeSessionWithManagedState(entityManagerFactory, testData);
            try {
                Student student = deserializedSession.find(Student.class, testData.studentId);
                assertThat(student.getEmail()).isEqualTo(testData.email);
            }
            finally {
                deserializedSession.close();
            }
        }
        finally {
            entityManagerFactory.close();
        }
    }

    @Test
    public void serializesAndDeserializesDynamicMapProxy() throws Exception {
        SessionFactory sessionFactory = new Configuration()
                .addInputStream(new ByteArrayInputStream(DYNAMIC_PERSON_MAPPING.getBytes(StandardCharsets.UTF_8)))
                .setProperty("hibernate.connection.driver_class", "org.h2.Driver")
                .setProperty(
                        "hibernate.connection.url",
                        "jdbc:h2:mem:stateful-proxy;DB_CLOSE_DELAY=-1"
                )
                .setProperty("hibernate.dialect", "org.hibernate.dialect.H2Dialect")
                .setProperty("hibernate.hbm2ddl.auto", "create-drop")
                .buildSessionFactory();
        try {
            Session session = sessionFactory.openSession();
            try {
                Map<String, Object> person = new HashMap<>();
                person.put("name", "Ada");

                session.getTransaction().begin();
                session.persist("DynamicPerson", person);
                session.getTransaction().commit();

                session.clear();
                Object id = person.get("id");
                Object proxy = session.getReference("DynamicPerson", id);
                assertThat(proxy).isInstanceOf(Map.class);

                Session deserializedSession = deserialize(serialize(session));
                try {
                    Map<?, ?> deserializedPerson = (Map<?, ?>) deserializedSession.get("DynamicPerson", id);
                    assertThat(deserializedPerson.get("name")).isEqualTo("Ada");
                }
                finally {
                    deserializedSession.close();
                }
            }
            finally {
                if (session.getTransaction().isActive()) {
                    session.getTransaction().rollback();
                }
                session.close();
            }
        }
        finally {
            sessionFactory.close();
        }
    }

    private EntityManagerFactory createEntityManagerFactory() {
        Map<String, String> properties = new HashMap<>();
        properties.put(
                "jakarta.persistence.jdbc.url",
                "jdbc:h2:mem:stateful-persistence-context;DB_CLOSE_DELAY=-1"
        );
        properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        return Persistence.createEntityManagerFactory("StudentPU", properties);
    }

    private TestData persistTestData(EntityManagerFactory entityManagerFactory) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            entityManager.getTransaction().begin();

            Student student = new Student();
            student.setFirstName("Ada");
            student.setLastName("Lovelace");
            student.setEmail("ada@example.test");
            entityManager.persist(student);

            Teacher teacher = new Teacher();
            teacher.setFirstName("Grace");
            teacher.setLastName("Hopper");
            entityManager.persist(teacher);

            Course course = new Course();
            course.setTitle("Compilers");
            course.setTeacher(teacher);
            course.setStudents(List.of(student));
            entityManager.persist(course);

            ArrayOwner arrayOwner = new ArrayOwner();
            arrayOwner.setTags(new String[]{"hibernate", "serialization"});
            entityManager.persist(arrayOwner);

            entityManager.getTransaction().commit();
            return new TestData(student.getId(), teacher.getId(), arrayOwner.getId(), student.getEmail());
        }
        finally {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            entityManager.close();
        }
    }

    private Session serializeSessionWithManagedState(EntityManagerFactory entityManagerFactory, TestData testData)
            throws IOException, ClassNotFoundException {
        Student detachedStudent = loadStudent(entityManagerFactory, testData.studentId);
        detachedStudent.setLastName("Byron");

        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            Session session = entityManager.unwrap(Session.class);
            entityManager.getTransaction().begin();
            session.update(detachedStudent);
            session.flush();

            Student naturalIdStudent = session.bySimpleNaturalId(Student.class).load(testData.email);
            Teacher teacher = session.find(Teacher.class, testData.teacherId);
            ArrayOwner arrayOwner = session.find(ArrayOwner.class, testData.arrayOwnerId);

            assertThat(naturalIdStudent).isSameAs(detachedStudent);
            assertThat(teacher.getCourses()).hasSize(1);
            assertThat(arrayOwner.getTags()).containsExactly("hibernate", "serialization");

            entityManager.getTransaction().commit();
            return deserialize(serialize(session));
        }
        finally {
            if (entityManager.getTransaction().isActive()) {
                entityManager.getTransaction().rollback();
            }
            entityManager.close();
        }
    }

    private Student loadStudent(EntityManagerFactory entityManagerFactory, Long studentId) {
        EntityManager entityManager = entityManagerFactory.createEntityManager();
        try {
            return entityManager.find(Student.class, studentId);
        }
        finally {
            entityManager.close();
        }
    }

    private byte[] serialize(Session session) throws IOException {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(session);
        }
        return bytes.toByteArray();
    }

    private Session deserialize(byte[] serializedSession) throws IOException, ClassNotFoundException {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializedSession))) {
            return (Session) input.readObject();
        }
    }

    private static final class TestData {

        private final Long studentId;
        private final Long teacherId;
        private final Long arrayOwnerId;
        private final String email;

        private TestData(Long studentId, Long teacherId, Long arrayOwnerId, String email) {
            this.studentId = studentId;
            this.teacherId = teacherId;
            this.arrayOwnerId = arrayOwnerId;
            this.email = email;
        }
    }
}
