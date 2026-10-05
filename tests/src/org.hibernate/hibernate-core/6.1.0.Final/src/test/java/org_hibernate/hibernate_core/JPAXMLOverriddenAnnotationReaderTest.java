/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

public class JPAXMLOverriddenAnnotationReaderTest {
    private static final String ORM_MAPPING = """
            <?xml version="1.0" encoding="UTF-8"?>
            <entity-mappings xmlns="https://jakarta.ee/xml/ns/persistence/orm" version="3.0">
                <persistence-unit-metadata>
                    <xml-mapping-metadata-complete/>
                </persistence-unit-metadata>
                <entity class="org_hibernate.hibernate_core.JPAXMLOverriddenAnnotationReaderTest$FieldAccessRecord"
                        access="FIELD">
                    <table name="XML_FIELD_RECORD"/>
                    <attributes>
                        <id name="id"/>
                        <basic name="name"/>
                    </attributes>
                </entity>
                <entity class="org_hibernate.hibernate_core.JPAXMLOverriddenAnnotationReaderTest$PropertyAccessRecord"
                        access="PROPERTY">
                    <table name="XML_PROPERTY_RECORD"/>
                    <attributes>
                        <id name="id"/>
                        <basic name="name"/>
                    </attributes>
                </entity>
            </entity-mappings>
            """;

    @Test
    public void mapsFieldAndPropertyAccessEntitiesFromJpaXml() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder().build();
        try {
            Metadata metadata = new MetadataSources(registry)
                    .addInputStream(new ByteArrayInputStream(ORM_MAPPING.getBytes(StandardCharsets.UTF_8)))
                    .buildMetadata();

            assertThat(metadata.getEntityBinding(FieldAccessRecord.class.getName()).getProperty("name"))
                    .isNotNull();
            assertThat(metadata.getEntityBinding(PropertyAccessRecord.class.getName()).getProperty("name"))
                    .isNotNull();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    public static class FieldAccessRecord {
        public Long id;
        public String name;

        public Long getId() {
            return id;
        }

        public String getName() {
            return name;
        }
    }

    public static class PropertyAccessRecord {
        private Long id;
        private String name;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
