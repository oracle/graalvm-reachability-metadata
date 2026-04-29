/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_seata.seata_all;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.seata.config.ConfigurationFactory;
import org.apache.seata.spring.annotation.GlobalTransactionScanner;
import org.apache.seata.spring.annotation.GlobalTransactional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.RootBeanDefinition;
import org.springframework.context.support.GenericApplicationContext;

public class GlobalTransactionScannerTest {
    @Test
    void scansBeanDefinitionsByClassNameBeforeEnhancement() {
        System.setProperty("config.type", "file");
        System.setProperty("config.file.name", "file.conf");
        ConfigurationFactory.reload();

        GenericApplicationContext context = new GenericApplicationContext();
        context.registerBeanDefinition("transactionalService",
                new RootBeanDefinition(ScannerTransactionalService.class));
        TestableGlobalTransactionScanner scanner = new TestableGlobalTransactionScanner();

        try {
            scanner.setApplicationContext(context);
            scanner.afterPropertiesSet();

            assertThat(scanner.isClientInitialized()).isTrue();
        } finally {
            context.close();
        }
    }

    private static final class TestableGlobalTransactionScanner extends GlobalTransactionScanner {
        private boolean clientInitialized;

        private TestableGlobalTransactionScanner() {
            super("scanner-test-application", "scanner-test-group");
        }

        @Override
        protected void initClient() {
            clientInitialized = true;
        }

        private boolean isClientInitialized() {
            return clientInitialized;
        }
    }
}

@GlobalTransactional
class ScannerTransactionalService {
    void invoke() {
    }
}
