/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_reactive.hibernate_reactive_core;

import org.hibernate.reactive.pool.impl.DefaultSqlClientPool;
import org.hibernate.reactive.provider.Settings;
import org.junit.jupiter.api.Test;
import org_hibernate_reactive.hibernate_reactive_core.ReactiveCollectionBatchLoaderArrayParamTest.BatchRecord;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org_hibernate_reactive.hibernate_reactive_core.ReactiveDatabase.await;

public class ReactiveConnectionPoolInitiatorTest {
    @Test
    void persistsAndLoadsThroughPoolSelectedByClassName() throws Exception {
        Map<String, Object> settings = Map.of(Settings.SQL_CLIENT_POOL, DefaultSqlClientPool.class.getName());
        try (ReactiveDatabase database = new ReactiveDatabase(settings, BatchRecord.class)) {
            BatchRecord record = new BatchRecord(41L, "configured pool");
            await(database.sessions().withTransaction((session, transaction) -> session.persist(record)));
            BatchRecord loaded = await(database.sessions().withSession(session -> session.find(BatchRecord.class, 41L)));
            assertEquals(41L, loaded.id);
            assertEquals("configured pool", loaded.label);
        }
    }
}
