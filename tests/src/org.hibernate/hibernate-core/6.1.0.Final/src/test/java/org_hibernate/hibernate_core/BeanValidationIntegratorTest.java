/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.HibernateException;
import org.hibernate.cfg.beanvalidation.BeanValidationIntegrator;
import org.junit.jupiter.api.Test;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

public class BeanValidationIntegratorTest {

    @Test
    public void validateFactoryRejectsObjectsThatAreNotValidatorFactories() {
        assertThatThrownBy(() -> BeanValidationIntegrator.validateFactory(new Object()))
                .isInstanceOf(HibernateException.class)
                .hasMessageContaining("Given object was not an instance of jakarta.validation.ValidatorFactory");
    }

    @Test
    public void persistenceBootstrapActivatesBeanValidationIntegrationWhenValidationApiIsPresent() {
        Map<String, String> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.url", "jdbc:h2:mem:bean-validation-integrator;DB_CLOSE_DELAY=-1");
        properties.put("hibernate.dialect", "org.hibernate.dialect.H2Dialect");
        properties.put("jakarta.persistence.validation.mode", "AUTO");

        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory(
                "StudentPU",
                properties);
        try {
            assertThat(entityManagerFactory.isOpen()).isTrue();
        }
        finally {
            entityManagerFactory.close();
        }
    }
}
