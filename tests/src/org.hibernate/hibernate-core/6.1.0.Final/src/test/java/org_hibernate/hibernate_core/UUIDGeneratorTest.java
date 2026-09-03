/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Parameter;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.id.UUIDGenerationStrategy;
import org.hibernate.id.UUIDGenerator;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

public class UUIDGeneratorTest {

    @Test
    public void usesAConfiguredLegacyUuidGenerationStrategy() {
        Configuration configuration = new Configuration()
                .addAnnotatedClass(LegacyUuidEntity.class)
                .setProperty(AvailableSettings.URL, "jdbc:h2:mem:legacy-uuid-generator")
                .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop")
                .setProperty(AvailableSettings.JAKARTA_VALIDATION_MODE, "none");

        try (SessionFactory factory = configuration.buildSessionFactory();
                Session session = factory.openSession()) {
            Transaction transaction = session.beginTransaction();
            LegacyUuidEntity entity = new LegacyUuidEntity();
            entity.setName("legacy");
            session.persist(entity);
            transaction.commit();

            assertThat(entity.getId()).isEqualTo(FixedUuidStrategy.VALUE);
            assertThat(session.find(LegacyUuidEntity.class, entity.getId()).getName())
                    .isEqualTo("legacy");
        }
    }

    @Entity(name = "LegacyUuidEntity")
    @Table(name = "LEGACY_UUID_ENTITY")
    public static class LegacyUuidEntity {
        @Id
        @GeneratedValue(generator = "legacy-uuid")
        @GenericGenerator(
                name = "legacy-uuid",
                strategy = "org.hibernate.id.UUIDGenerator",
                parameters = @Parameter(
                        name = UUIDGenerator.UUID_GEN_STRATEGY_CLASS,
                        value = "org_hibernate.hibernate_core.UUIDGeneratorTest$FixedUuidStrategy"
                )
        )
        private UUID id;

        private String name;

        public UUID getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }

    public static class FixedUuidStrategy implements UUIDGenerationStrategy {
        private static final long serialVersionUID = 1L;
        private static final UUID VALUE = UUID.fromString("12345678-1234-4234-9234-123456789abc");

        @Override
        public int getGeneratedVersion() {
            return 4;
        }

        @Override
        public UUID generateUUID(SharedSessionContractImplementor session) {
            return VALUE;
        }
    }
}
