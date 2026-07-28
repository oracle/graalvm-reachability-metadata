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
import org_hibernate.hibernate_core.entity.ImmutableSerializationEntity;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

public class ImmutableEntityEntryTest {

    @Test
    public void serializesSessionContainingImmutableEntity() throws Exception {
        Map<String, Object> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.url", "jdbc:h2:mem:immutable-entity-entry;DB_CLOSE_DELAY=-1");
        properties.put("hibernate.hbm2ddl.auto", "create-drop");
        properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        properties.put("hibernate.bytecode.provider", "none");

        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory("StudentPU", properties);
        SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);
        Long entityId;

        try {
            try (Session session = sessionFactory.openSession()) {
                session.beginTransaction();
                ImmutableSerializationEntity entity = new ImmutableSerializationEntity("immutable entity");
                session.persist(entity);
                session.getTransaction().commit();
                entityId = entity.getId();
            }

            byte[] serializedSession;
            try (Session session = sessionFactory.openSession()) {
                ImmutableSerializationEntity entity = session.find(ImmutableSerializationEntity.class, entityId);
                assertThat(entity.getName()).isEqualTo("immutable entity");
                serializedSession = serialize(session);
            }

            try (Session restoredSession = deserialize(serializedSession)) {
                ImmutableSerializationEntity restoredEntity = restoredSession.find(
                        ImmutableSerializationEntity.class, entityId);
                assertThat(restoredEntity.getName()).isEqualTo("immutable entity");
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
