/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate_orm.hibernate_core;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import org.hibernate.SessionFactory;
import org.hibernate.cfg.Configuration;
import org.hibernate.cfg.FlushSettings;
import org.hibernate.engine.jdbc.mutation.MutationExecutor;
import org.hibernate.engine.jdbc.mutation.internal.MutationExecutorServiceInitiator;
import org.hibernate.engine.jdbc.mutation.internal.StandardMutationExecutorService;
import org.hibernate.engine.jdbc.mutation.spi.BatchKeyAccess;
import org.hibernate.engine.jdbc.mutation.spi.JdbcValueBindingsFactory;
import org.hibernate.engine.jdbc.mutation.spi.MutationExecutorService;
import org.hibernate.engine.spi.SharedSessionContractImplementor;
import org.hibernate.sql.model.MutationOperationGroup;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class MutationExecutorServiceInitiatorTest {

    private static final List<String> MUTATIONS = new CopyOnWriteArrayList<>();

    @Test
    void configuredMutationExecutorServiceExecutesLegacyQueueMutations() {
        MUTATIONS.clear();
        Configuration configuration = SessionFactoryTestSupport.h2Configuration(Invoice.class);
        configuration.setProperty(FlushSettings.FLUSH_QUEUE_TYPE, "legacy");
        configuration.setProperty(
                MutationExecutorServiceInitiator.EXECUTOR_KEY,
                RecordingMutationExecutorService.class.getName());

        try (SessionFactory sessionFactory = configuration.buildSessionFactory()) {
            sessionFactory.inTransaction(session -> session.persist(new Invoice(1L, 120)));
            sessionFactory.inTransaction(session -> session.find(Invoice.class, 1L).setAmount(150));
            Integer amount = sessionFactory.fromTransaction(session -> session.find(Invoice.class, 1L).getAmount());

            assertThat(amount).isEqualTo(150);
        }
        assertThat(MUTATIONS).contains("INSERT", "UPDATE");
    }

    public static class RecordingMutationExecutorService implements MutationExecutorService {

        private final MutationExecutorService delegate = new StandardMutationExecutorService(1);

        @Override
        public MutationExecutor createExecutor(
                BatchKeyAccess batchKeySupplier,
                MutationOperationGroup operationGroup,
                SharedSessionContractImplementor session) {
            MUTATIONS.add(operationGroup.getMutationType().name());
            return delegate.createExecutor(batchKeySupplier, operationGroup, session);
        }

        @Override
        public MutationExecutor createExecutor(
                BatchKeyAccess batchKeySupplier,
                MutationOperationGroup operationGroup,
                JdbcValueBindingsFactory bindingsFactory,
                SharedSessionContractImplementor session) {
            MUTATIONS.add(operationGroup.getMutationType().name());
            return delegate.createExecutor(batchKeySupplier, operationGroup, bindingsFactory, session);
        }
    }

    @Entity(name = "Invoice")
    public static class Invoice {

        @Id
        private Long id;

        private int amount;

        public Invoice() {
        }

        public Invoice(Long id, int amount) {
            this.id = id;
            this.amount = amount;
        }

        public int getAmount() {
            return amount;
        }

        public void setAmount(int amount) {
            this.amount = amount;
        }
    }
}
