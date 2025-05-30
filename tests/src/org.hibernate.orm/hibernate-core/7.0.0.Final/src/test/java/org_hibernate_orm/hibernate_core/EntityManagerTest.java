/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class EntityManagerTest {

    private EntityManagerFactory entityManagerFactory;
    private EntityManager entityManager;

    @AfterAll
    public void close() {
        if (entityManager != null) {
            entityManager.close();
            entityManager = null;
        }
        if (entityManagerFactory != null) {
            entityManagerFactory.close();
            entityManagerFactory = null;
        }
    }

//    @Disabled("com.oracle.svm.core.jdk.UnsupportedFeatureError: Class.getNestMembers is not supported yet - via DialectOverridesAnnotationHelper:L32")
    @ParameterizedTest
    @ValueSource(strings = {
            "single",
            "legacy",
            "standard"})
    public void testLoadDbStructureNamingStrategy(String namingStrategy) {
        Map<String, String> properties = new HashMap<>();
        properties.put(AvailableSettings.ID_DB_STRUCTURE_NAMING_STRATEGY, namingStrategy);
        createEntityManager(properties);
        assertThat(entityManager).isNotNull();
    }

//    @Disabled("com.oracle.svm.core.jdk.UnsupportedFeatureError: Class.getNestMembers is not supported yet - via DialectOverridesAnnotationHelper:L32")
    @ParameterizedTest
    @ValueSource(strings = {
            "org.hibernate.boot.model.naming.PhysicalNamingStrategyStandardImpl",
            "org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"})
    public void testLoadPhysicalNamingStrategy(String physicalNamingStrategy) {
        Map<String, String> properties = new HashMap<>();
        properties.put(AvailableSettings.PHYSICAL_NAMING_STRATEGY, physicalNamingStrategy);
        createEntityManager(properties);
        assertThat(entityManager).isNotNull();
    }

    private void createEntityManager(Map<String, String> properties) {
        properties.put("jakarta.persistence.jdbc.url", "jdbc:h2:mem:test;MODE=MYSQL");
        properties.put("hibernate.dialect", "org.hibernate.dialect.MySQLDialect");
        entityManagerFactory = Persistence.createEntityManagerFactory("StudentPU", properties);
        entityManager = entityManagerFactory.createEntityManager();
    }

}
