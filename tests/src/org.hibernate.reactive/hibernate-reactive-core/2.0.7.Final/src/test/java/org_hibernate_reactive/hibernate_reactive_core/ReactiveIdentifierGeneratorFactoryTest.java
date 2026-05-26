/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core;

import java.util.HashMap;
import java.util.Map;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.Test;
import org_hibernate_reactive.hibernate_reactive_core.entity.ReactiveGeneratedIdentifierEntity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hibernate.reactive.stage.Stage.SessionFactory;

public class ReactiveIdentifierGeneratorFactoryTest {

    @Test
    void buildsSessionFactoryWithReactiveIdentifierGeneratorStrategy() {
        Map<String, String> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.url", "jdbc:mysql://localhost:3306/test");
        properties.put("jakarta.persistence.jdbc.user", "fred");
        properties.put("jakarta.persistence.jdbc.password", "secret");
        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        properties.put("hibernate.hbm2ddl.auto", "none");

        EntityManagerFactory entityManagerFactory = Persistence.createEntityManagerFactory(
                "reactive-identifier-generator-factory",
                properties
        );
        try {
            SessionFactory sessionFactory = entityManagerFactory.unwrap(SessionFactory.class);

            assertThat(sessionFactory.getMetamodel().entity(ReactiveGeneratedIdentifierEntity.class).getName())
                    .isEqualTo("ReactiveGeneratedIdentifierEntity");
        } finally {
            entityManagerFactory.close();
        }
    }
}
