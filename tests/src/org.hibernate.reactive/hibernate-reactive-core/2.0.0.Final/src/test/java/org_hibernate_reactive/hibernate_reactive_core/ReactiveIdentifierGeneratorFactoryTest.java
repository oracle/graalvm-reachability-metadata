/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.Id;
import org.hibernate.annotations.GenericGenerator;
import org.hibernate.annotations.Proxy;
import org.hibernate.reactive.id.ReactiveIdentifierGenerator;
import org.hibernate.reactive.session.ReactiveConnectionSupplier;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org_hibernate_reactive.hibernate_reactive_core.ReactiveDatabase.await;

public class ReactiveIdentifierGeneratorFactoryTest {
    @Test
    void generatesIdentifiersWithAReactiveOnlyCustomStrategy() throws Exception {
        try (ReactiveDatabase database = new ReactiveDatabase(Map.of(), GeneratedRecord.class)) {
            GeneratedRecord record = new GeneratedRecord();
            record.label = "reactive identifier";
            await(database.sessions().withTransaction((session, transaction) -> session.persist(record)));
            assertEquals(701L, record.id);
            GeneratedRecord loaded = await(database.sessions().withSession(session -> session.find(GeneratedRecord.class, record.id)));
            assertEquals(record.id, loaded.id);
            assertEquals("reactive identifier", loaded.label);
        }
    }

    public static class RecordIdentifierGenerator implements ReactiveIdentifierGenerator<Long> {
        public RecordIdentifierGenerator() {
        }

        @Override
        public CompletionStage<Long> generate(ReactiveConnectionSupplier session, Object entity) {
            return CompletableFuture.completedFuture(701L);
        }
    }

    @Entity(name = "GeneratedRecord")
    @Proxy(lazy = false)
    public static class GeneratedRecord {
        @Id
        @GeneratedValue(generator = "record-id")
        @GenericGenerator(name = "record-id", strategy = "org_hibernate_reactive.hibernate_reactive_core.ReactiveIdentifierGeneratorFactoryTest$RecordIdentifierGenerator")
        public Long id;

        public String label;

        public GeneratedRecord() {
        }
    }
}
