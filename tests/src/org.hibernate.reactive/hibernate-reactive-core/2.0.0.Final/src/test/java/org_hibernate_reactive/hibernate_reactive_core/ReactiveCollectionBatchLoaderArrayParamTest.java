/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org_hibernate_reactive.hibernate_reactive_core.entity.BatchRecord;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org_hibernate_reactive.hibernate_reactive_core.ReactiveDatabase.await;

public class ReactiveCollectionBatchLoaderArrayParamTest {
    @Test
    void fetchingOneCollectionInitializesOtherQueuedOwners() throws Exception {
        try (ReactiveDatabase database = new ReactiveDatabase(Map.of(), BatchRecord.class)) {
            BatchRecord first = new BatchRecord(1L, "first", "red", "blue");
            BatchRecord second = new BatchRecord(2L, "second", "green");
            await(database.sessions().withTransaction((session, transaction) -> session.persist(first, second)));
            await(database.sessions().withSession(session -> session
                    .createQuery("from BatchRecord order by id", BatchRecord.class).getResultList()
                    .thenCompose(records -> {
                        assertEquals(2, records.size());
                        assertFalse(Hibernate.isInitialized(records.get(0).tags));
                        assertFalse(Hibernate.isInitialized(records.get(1).tags));
                        return session.fetch(records.get(0).tags).thenAccept(tags -> {
                            assertEquals(2, tags.size());
                            assertTrue(tags.containsAll(first.tags));
                            assertTrue(Hibernate.isInitialized(records.get(1).tags));
                            assertEquals(1, records.get(1).tags.size());
                            assertTrue(records.get(1).tags.containsAll(second.tags));
                        });
                    })));
        }
    }
}
