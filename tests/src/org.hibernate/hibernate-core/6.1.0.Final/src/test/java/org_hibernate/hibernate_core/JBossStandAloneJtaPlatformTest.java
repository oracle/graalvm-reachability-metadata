/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_hibernate.hibernate_core;

import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.cfg.AvailableSettings;
import org.hibernate.engine.transaction.jta.platform.internal.JBossStandAloneJtaPlatform;
import org.hibernate.engine.transaction.jta.platform.spi.JtaPlatform;
import org.junit.jupiter.api.Test;

import jakarta.transaction.Status;
import jakarta.transaction.TransactionManager;
import jakarta.transaction.UserTransaction;

import static org.assertj.core.api.Assertions.assertThat;

public class JBossStandAloneJtaPlatformTest {

    @Test
    public void obtainsNarayanaTransactionServicesThroughTheConfiguredPlatform() throws Exception {
        StandardServiceRegistry registry = new StandardServiceRegistryBuilder()
                .applySetting(AvailableSettings.JTA_PLATFORM, "JBossTS")
                .build();
        try {
            JtaPlatform platform = registry.getService(JtaPlatform.class);
            TransactionManager transactionManager = platform.retrieveTransactionManager();
            UserTransaction userTransaction = platform.retrieveUserTransaction();

            assertThat(platform).isInstanceOf(JBossStandAloneJtaPlatform.class);
            assertThat(transactionManager.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
            assertThat(userTransaction.getStatus()).isEqualTo(Status.STATUS_NO_TRANSACTION);
        } finally {
            StandardServiceRegistryBuilder.destroy(registry);
        }
    }
}
