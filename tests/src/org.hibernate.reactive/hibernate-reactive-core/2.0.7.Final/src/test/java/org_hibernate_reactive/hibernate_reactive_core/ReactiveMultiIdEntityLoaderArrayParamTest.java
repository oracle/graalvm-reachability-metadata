/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core;

import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.metamodel.mapping.EntityMappingType;
import org.hibernate.reactive.loader.ast.internal.ReactiveMultiIdEntityLoaderArrayParam;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org_hibernate_reactive.hibernate_reactive_core.entity.BatchAuthor;

import java.util.HashMap;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
public class ReactiveMultiIdEntityLoaderArrayParamTest {

    private EntityManagerFactory factory;

    @BeforeAll
    public void init() {
        Map<String, String> properties = new HashMap<>();
        properties.put("jakarta.persistence.jdbc.url", "jdbc:postgresql://localhost:5434/test");
        properties.put("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect");
        properties.put("hibernate.boot.allow_jdbc_metadata_access", "false");
        properties.put("hibernate.temp.use_jdbc_metadata_defaults", "false");
        factory = Persistence.createEntityManagerFactory("postgres-entity-array-param-batch", properties);
    }

    @AfterAll
    public void close() {
        if (factory != null) {
            factory.close();
        }
    }

    @Test
    void preparesSqlArrayMultiIdLoaderForEntityIdentifierType() {
        SessionFactoryImplementor sessionFactory = factory.unwrap(SessionFactoryImplementor.class);
        EntityMappingType entityMapping = sessionFactory.getRuntimeMetamodels()
                .getMappingMetamodel()
                .getEntityDescriptor(BatchAuthor.class);

        ReactiveMultiIdEntityLoaderArrayParam<BatchAuthor> loader =
                new ReactiveMultiIdEntityLoaderArrayParam<>(entityMapping, sessionFactory);
        loader.prepare();

        Class<?> identifierType = loader.getIdentifierMapping().getJavaType().getJavaTypeClass();
        assertThat(identifierType).isEqualTo(Long.class);
    }
}
