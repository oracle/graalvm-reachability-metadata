/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.spi.SessionFactoryImplementor;
import org.hibernate.query.hql.HqlTranslator;
import org.hibernate.query.sqm.tree.SqmStatement;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SessionFactoryOptionsBuilderAnonymous1Test {

    @Test
    public void selectsAConfiguredHqlTranslator() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.URL, "jdbc:h2:mem:custom-hql-translator")
                .applySetting(AvailableSettings.DRIVER, "org.h2.Driver")
                .applySetting(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .applySetting(AvailableSettings.SEMANTIC_QUERY_PRODUCER, RecordingHqlTranslator.class.getName())
                .build();
        try {
            Metadata metadata = new MetadataSources(registry).buildMetadata();
            try (SessionFactory factory = metadata.buildSessionFactory()) {
                HqlTranslator translator = ((SessionFactoryImplementor) factory)
                        .getQueryEngine()
                        .getHqlTranslator();

                assertThat(translator).isInstanceOf(RecordingHqlTranslator.class);
            }
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    public static class RecordingHqlTranslator implements HqlTranslator {
        @Override
        public <R> SqmStatement<R> translate(String hql, Class<R> expectedResultType) {
            throw new UnsupportedOperationException("No query translation is needed for configuration selection");
        }
    }
}
