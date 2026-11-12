/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core;

import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import org.hibernate.annotations.Proxy;
import org.junit.jupiter.api.Test;

import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org_hibernate_reactive.hibernate_reactive_core.ReactiveDatabase.await;

public class ReactiveMultiIdEntityLoaderStandardTest {
    @Test
    void bulkFindLoadsCompositeIdentifiersInRequestedOrder() throws Exception {
        try (ReactiveDatabase database = new ReactiveDatabase(Map.of(), CatalogRecord.class)) {
            CatalogRecord first = new CatalogRecord(new CatalogKey("books", 1L), "first title");
            CatalogRecord second = new CatalogRecord(new CatalogKey("books", 2L), "second title");
            await(database.sessions().withTransaction((session, transaction) -> session.persist(first, second)));
            List<CatalogRecord> loaded = await(database.sessions().withSession(session ->
                    session.find(CatalogRecord.class, second.id, new CatalogKey("books", 99L), first.id)));
            assertEquals(3, loaded.size());
            assertEquals(second.id, loaded.get(0).id);
            assertEquals("second title", loaded.get(0).label);
            assertNull(loaded.get(1));
            assertEquals(first.id, loaded.get(2).id);
            assertEquals("first title", loaded.get(2).label);
        }
    }

    @Entity(name = "CatalogRecord")
    @Proxy(lazy = false)
    public static class CatalogRecord {
        @EmbeddedId
        public CatalogKey id;

        public String label;

        public CatalogRecord() {
        }

        public CatalogRecord(CatalogKey id, String label) {
            this.id = id;
            this.label = label;
        }
    }

    @Embeddable
    public static class CatalogKey implements Serializable {
        private static final long serialVersionUID = 1L;

        public String catalog;
        public Long entryNumber;

        public CatalogKey() {
        }

        public CatalogKey(String catalog, Long entryNumber) {
            this.catalog = catalog;
            this.entryNumber = entryNumber;
        }

        @Override
        public boolean equals(Object other) {
            if (!(other instanceof CatalogKey key)) {
                return false;
            }
            return Objects.equals(catalog, key.catalog) && Objects.equals(entryNumber, key.entryNumber);
        }

        @Override
        public int hashCode() {
            return Objects.hash(catalog, entryNumber);
        }
    }
}
