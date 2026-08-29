/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.engine.jdbc.batch.internal.BatchBuilderInitiator;
import org.hibernate.engine.jdbc.batch.spi.Batch;
import org.hibernate.engine.jdbc.batch.spi.BatchBuilder;
import org.hibernate.engine.jdbc.batch.spi.BatchKey;
import org.hibernate.engine.jdbc.spi.JdbcCoordinator;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class BatchBuilderInitiatorTest {

    @Test
    public void instantiatesTheConfiguredBatchBuilder() {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(
                        BatchBuilderInitiator.BUILDER,
                        RecordingBatchBuilder.class.getName()
                )
                .build();
        try {
            assertThat(registry.getService(BatchBuilder.class))
                    .isInstanceOf(RecordingBatchBuilder.class);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }

    public static class RecordingBatchBuilder implements BatchBuilder {
        @Override
        public Batch buildBatch(BatchKey key, JdbcCoordinator jdbcCoordinator) {
            return null;
        }
    }
}
