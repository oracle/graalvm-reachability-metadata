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
import java.util.Comparator;
import java.util.SortedSet;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;

public class CollectionTest {
    private static final String SORTED_COLLECTION_MAPPING = """
            <?xml version="1.0" encoding="UTF-8"?>
            <!DOCTYPE hibernate-mapping PUBLIC
                    "-//Hibernate/Hibernate Mapping DTD 3.0//EN"
                    "http://www.hibernate.org/dtd/hibernate-mapping-3.0.dtd">
            <hibernate-mapping>
                <class name="org_hibernate.hibernate_core.CollectionTest$SortedRecord" table="SORTED_HBM_RECORD">
                    <id name="id" type="long"/>
                    <set name="values"
                         table="SORTED_HBM_VALUE"
                         sort="org_hibernate.hibernate_core.CollectionTest$DescendingComparator">
                        <key column="record_id"/>
                        <element column="sorted_value" type="string"/>
                    </set>
                </class>
            </hibernate-mapping>
            """;

    @Test
    public void instantiatesTheComparatorNamedByHbmXml() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder().build();
        try {
            Metadata metadata = new MetadataSources(registry)
                    .addInputStream(new ByteArrayInputStream(
                            SORTED_COLLECTION_MAPPING.getBytes(StandardCharsets.UTF_8)
                    ))
                    .buildMetadata();
            org.hibernate.mapping.Collection values = metadata.getCollectionBinding(
                    SortedRecord.class.getName() + ".values"
            );

            assertThat(values.getComparator()).isInstanceOf(DescendingComparator.class);
            @SuppressWarnings("unchecked")
            Comparator<String> comparator = (Comparator<String>) values.getComparator();
            assertThat(comparator.compare("alpha", "omega")).isPositive();
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    public static class SortedRecord {
        private Long id;
        private SortedSet<String> values = new TreeSet<>(new DescendingComparator());

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        public SortedSet<String> getValues() {
            return values;
        }

        public void setValues(SortedSet<String> values) {
            this.values = values;
        }
    }

    public static class DescendingComparator implements Comparator<String> {
        @Override
        public int compare(String first, String second) {
            return second.compareTo(first);
        }
    }
}
