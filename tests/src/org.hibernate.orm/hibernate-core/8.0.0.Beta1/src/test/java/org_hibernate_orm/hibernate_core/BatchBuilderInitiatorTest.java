/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Supplier;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.BatchSettings;
import org.hibernate.cfg.Configuration;
import org.hibernate.engine.jdbc.batch.internal.BatchBuilderImpl;
import org.hibernate.engine.jdbc.batch.spi.BatchBuilder;
import org.hibernate.engine.jdbc.batch.spi.BatchKey;
import org.hibernate.engine.jdbc.batch.spi.GroupedBatch;
import org.hibernate.engine.jdbc.batch.spi.SingleStatementBatch;
import org.hibernate.engine.jdbc.mutation.group.PreparedStatementGroup;
import org.hibernate.engine.jdbc.spi.JdbcCoordinator;
import org.hibernate.sql.model.PreparableMutationOperation;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BatchBuilderInitiatorTest {

    private static final List<String> BATCHES = new CopyOnWriteArrayList<>();

    @Test
    void configuredBatchBuilderBuildsBatchesForInserts() {
        BATCHES.clear();
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Parcel.class);
        configuration.setProperty(BatchSettings.STATEMENT_BATCH_SIZE, 10);
        configuration.setProperty(BatchSettings.BUILDER, RecordingBatchBuilder.class.getName());

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            sessionFactory.inTransaction(session -> {
                for (long id = 1; id <= 3; id++) {
                    session.persist(new Parcel(id, "parcel-" + id));
                }
            });
            Long count = sessionFactory.fromTransaction(session -> session.createSelectionQuery(
                    "select count(p) from Parcel p", Long.class).getSingleResult());

            assertThat(count).isEqualTo(3L);
        }
        assertThat(BATCHES).isNotEmpty();
    }

    public static class RecordingBatchBuilder implements BatchBuilder {

        private final BatchBuilder delegate = new BatchBuilderImpl(10);

        @Override
        public GroupedBatch buildGroupedBatch(
                BatchKey key,
                Integer batchSize,
                Supplier<PreparedStatementGroup> statementGroupSupplier,
                JdbcCoordinator jdbcCoordinator) {
            BATCHES.add("grouped:" + key);
            return delegate.buildGroupedBatch(key, batchSize, statementGroupSupplier, jdbcCoordinator);
        }

        @Override
        public SingleStatementBatch buildSingleStatementBatch(
                BatchKey key,
                Integer batchSize,
                PreparableMutationOperation mutationOperation,
                JdbcCoordinator jdbcCoordinator) {
            BATCHES.add("single:" + key);
            return delegate.buildSingleStatementBatch(key, batchSize, mutationOperation, jdbcCoordinator);
        }
    }

    @Entity(name = "Parcel")
    public static class Parcel {

        @Id
        private Long id;

        private String label;

        public Parcel() {
        }

        public Parcel(Long id, String label) {
            this.id = id;
            this.label = label;
        }
    }
}
