/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.mapping.PersistentClass;
import org.hibernate.persister.entity.EntityPersister;
import org.hibernate.persister.internal.PersisterClassResolverInitiator;
import org.hibernate.persister.internal.StandardPersisterClassResolver;
import org_hibernate_orm.hibernate_core.entity.Person;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class PersisterClassResolverInitiatorTest {

    private static final List<String> RESOLVED = new CopyOnWriteArrayList<>();

    @Test
    void customPersisterClassResolverIsConsultedForEntities() {
        RESOLVED.clear();
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Person.class);
        configuration.setProperty(PersisterClassResolverInitiator.IMPL_NAME, RecordingPersisterClassResolver.class.getName());

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            Person.persistAliceAndBob(sessionFactory);
            Long count = sessionFactory.fromTransaction(session -> session.createSelectionQuery(
                    "select count(p) from Person p", Long.class).getSingleResult());

            assertThat(count).isEqualTo(2L);
        }
        assertThat(RESOLVED).contains(Person.class.getName());
    }

    public static class RecordingPersisterClassResolver extends StandardPersisterClassResolver {

        @Override
        public Class<? extends EntityPersister> getEntityPersisterClass(PersistentClass model) {
            RESOLVED.add(model.getEntityName());
            return super.getEntityPersisterClass(model);
        }
    }
}
