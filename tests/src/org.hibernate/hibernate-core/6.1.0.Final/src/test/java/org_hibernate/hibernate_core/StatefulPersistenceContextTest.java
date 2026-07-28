/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.junit.jupiter.api.Test;
import org_hibernate.hibernate_core.entity.SerializationChild;
import org_hibernate.hibernate_core.entity.SerializationParent;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class StatefulPersistenceContextTest {

    @Test
    public void serializesAndDeserializesSessionPersistenceContext() throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.url", "jdbc:h2:mem:stateful-persistence-context;DB_CLOSE_DELAY=-1");
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        properties.put("hibernate.bytecode.provider", "none");

        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("StudentPU", properties);
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        SerializationParent parentForUpdate = new SerializationParent(
                "update-parent", "before update", new SerializationChild[0]);
        SerializationParent parentWithArray = new SerializationParent(
                "array-parent", "array holder", new SerializationChild[0]);
        SerializationChild arrayChild = new SerializationChild("array child", parentWithArray);

        try {
            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();
                parentWithArray.setChildren(new SerializationChild[]{arrayChild});
                session.persist(parentForUpdate);
                session.persist(parentWithArray);
                session.getTransaction().commit();
            }

            byte[] serializedSession;
            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();
                parentForUpdate.setDescription("after update");
                session.update(parentForUpdate);
                session.flush();

                SerializationParent loadedParent = session.find(SerializationParent.class, parentWithArray.getId());
                assertThat(loadedParent.getChildren()).hasSize(1);
                assertThat(loadedParent.getChildren()[0].getLookupParent().getCode()).isEqualTo("array-parent");
                session.getTransaction().commit();

                serializedSession = serialize(session);
            }

            try (Session restoredSession = deserialize(serializedSession)) {
                SerializationParent restoredParent = restoredSession.find(
                        SerializationParent.class, parentWithArray.getId());
                assertThat(restoredParent.getDescription()).isEqualTo("array holder");
                assertThat(restoredParent.getChildren()).hasSize(1);
            }
        }
        finally {
            entityManagerFactory.close();
        }
    }

    private byte[] serialize(Session session) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(session);
        }
        return bytes.toByteArray();
    }

    private Session deserialize(byte[] serializedSession) throws Exception {
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(serializedSession))) {
            return (Session) input.readObject();
        }
    }
}
