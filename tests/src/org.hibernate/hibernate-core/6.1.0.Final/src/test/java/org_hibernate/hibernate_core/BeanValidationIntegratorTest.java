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
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.cfg.Configuration;
import org.junit.jupiter.api.Test;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Validation;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BeanValidationIntegratorTest {

    @Test
    public void appliesTheSuppliedValidatorFactoryToEntityCallbacks() {
        try (ValidatorFactory validatorFactory = Validation.buildDefaultValidatorFactory()) {
            Configuration configuration = new Configuration()
                    .addAnnotatedClass(ValidatedRecord.class)
                    .setProperty(AvailableSettings.URL, "jdbc:h2:mem:bean-validation")
                    .setProperty(AvailableSettings.DRIVER, "org.h2.Driver")
                    .setProperty(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                    .setProperty(AvailableSettings.HBM2DDL_AUTO, "create-drop");
            configuration.getProperties().put(AvailableSettings.JAKARTA_VALIDATION_FACTORY, validatorFactory);

            try (SessionFactory sessionFactory = configuration.buildSessionFactory();
                    Session session = sessionFactory.openSession()) {
                Transaction transaction = session.beginTransaction();
                ValidatedRecord invalid = new ValidatedRecord();

                assertThatThrownBy(() -> {
                    session.persist(invalid);
                    session.flush();
                }).isInstanceOf(ConstraintViolationException.class);

                transaction.rollback();
            }
        }
    }

    @Entity(name = "ValidatedRecord")
    @Table(name = "VALIDATED_RECORD")
    public static class ValidatedRecord {
        @Id
        @GeneratedValue
        private Long id;

        @NotBlank
        private String name;

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
