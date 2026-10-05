/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.model.relational.AbstractAuxiliaryDatabaseObject;
import org.hibernate.boot.model.relational.SqlStringGenerationContext;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class AuxiliaryDatabaseObjectBinderTest {
    private static final String AUXILIARY_MAPPING = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE hibernate-mapping PUBLIC
                    "-//Hibernate/Hibernate Mapping DTD 3.0//EN"
                    "http://www.hibernate.org/dtd/hibernate-mapping-3.0.dtd">
            <hibernate-mapping>
                <database-object>
                    <definition class="org_hibernate.hibernate_core.AuxiliaryDatabaseObjectBinderTest$MarkerAuxiliaryDatabaseObject"/>
                </database-object>
            </hibernate-mapping>
            """;

    @Test
    public void instantiatesACustomAuxiliaryObjectFromHbmXml() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.DIALECT, "org.hibernate.dialect.H2Dialect")
                .build();
        try {
            Metadata metadata = new MetadataSources(registry)
                    .addInputStream(new ByteArrayInputStream(AUXILIARY_MAPPING.getBytes(StandardCharsets.UTF_8)))
                    .buildMetadata();

            assertThat(metadata.getDatabase().getAuxiliaryDatabaseObjects())
                    .singleElement()
                    .isInstanceOf(MarkerAuxiliaryDatabaseObject.class);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    public static class MarkerAuxiliaryDatabaseObject extends AbstractAuxiliaryDatabaseObject {
        @Override
        public String[] sqlCreateStrings(SqlStringGenerationContext context) {
            return new String[]{"create table AUXILIARY_MARKER (id integer)"};
        }

        @Override
        public String[] sqlDropStrings(SqlStringGenerationContext context) {
            return new String[]{"drop table AUXILIARY_MARKER"};
        }
    }
}
