/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.io.StringReader;
import java.util.List;

import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.SchemaToolingSettings;
import org_hibernate_orm.hibernate_core.entity.Person;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class SqlScriptExtractorInitiatorTest {

    @Test
    void namedScriptExtractorParsesTheMultiLineLoadScript() {
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Person.class);
        configuration.getProperties().put(SchemaToolingSettings.JAKARTA_HBM2DDL_LOAD_SCRIPT_SOURCE, new StringReader("""
                insert into Person (id, name, age)
                    values (1, 'Carol', 45);
                insert into Person (id, name, age)
                    values (2, 'Dave', 38);
                """));
        configuration.setProperty(
                SchemaToolingSettings.HBM2DDL_IMPORT_FILES_SQL_EXTRACTOR,
                "org.hibernate.tool.schema.internal.script.MultiLineSqlScriptExtractor");

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            List<String> names = sessionFactory.fromTransaction(session -> session.createSelectionQuery(
                    "select p.name from Person p order by p.id", String.class).getResultList());

            assertThat(names).containsExactly("Carol", "Dave");
        }
    }
}
