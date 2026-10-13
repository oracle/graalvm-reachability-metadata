/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.uuid.UuidValueGenerator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class UuidGeneratorTest {

    @Test
    void customUuidAlgorithmGeneratesIdentifiers() {
        try (SessionFactory sessionFactory = SessionFactoryTestSupport
                .h2Configuration(Device.class)
                .buildSessionFactory()) {
            Device device = new Device("sensor");
            sessionFactory.inTransaction(session -> session.persist(device));
            String name = sessionFactory.fromTransaction(session -> session.find(Device.class, device.getId()).getName());

            assertThat(device.getId().getMostSignificantBits()).isEqualTo(0x5EED_0000_0000_0000L);
            assertThat(name).isEqualTo("sensor");
        }
    }

    public static class SequentialUuidValueGenerator implements UuidValueGenerator {

        private static final AtomicLong COUNTER = new AtomicLong();

        @Override
        public UUID generateUuid(SharedSessionContractImplementor session) {
            return new UUID(0x5EED_0000_0000_0000L, COUNTER.incrementAndGet());
        }
    }

    @Entity(name = "Device")
    public static class Device {

        @Id
        @UuidGenerator(algorithm = SequentialUuidValueGenerator.class)
        private UUID id;

        private String name;

        public Device() {
        }

        public Device(String name) {
            this.name = name;
        }

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }
}
