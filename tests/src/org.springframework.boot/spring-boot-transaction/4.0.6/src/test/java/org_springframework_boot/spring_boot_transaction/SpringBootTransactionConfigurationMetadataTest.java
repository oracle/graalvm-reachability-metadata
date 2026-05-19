/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_transaction;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.boot.transaction.autoconfigure.TransactionProperties;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import static org.assertj.core.api.Assertions.assertThat;

public class SpringBootTransactionConfigurationMetadataTest {

    @Test
    void additionalConfigurationMetadataDocumentsJtaEnablementProperty() throws IOException {
        String metadata = resourceText("META-INF/additional-spring-configuration-metadata.json");

        assertThat(metadata)
                .contains("\"name\": \"spring.jta.enabled\"")
                .contains("\"type\": \"java.lang.Boolean\"")
                .contains("\"description\": \"Whether to enable JTA support.\"")
                .contains("\"defaultValue\": true");
    }

    @Test
    void transactionPropertiesCustomizesPlatformTransactionManager() {
        TransactionProperties properties = new TransactionProperties();
        properties.setDefaultTimeout(Duration.ofSeconds(30));
        properties.setRollbackOnCommitFailure(true);
        TestTransactionManager transactionManager = new TestTransactionManager();

        properties.customize(transactionManager);

        assertThat(transactionManager.getDefaultTimeout()).isEqualTo(30);
        assertThat(transactionManager.isRollbackOnCommitFailure()).isTrue();
    }

    private static String resourceText(String resourceName) throws IOException {
        ClassLoader classLoader = TransactionProperties.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(resourceName)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    private static final class TestTransactionManager extends AbstractPlatformTransactionManager {

        @Override
        protected Object doGetTransaction() {
            return new Object();
        }

        @Override
        protected void doBegin(Object transaction, TransactionDefinition definition) {
            throw new UnsupportedOperationException("Transaction execution is not needed for property customization");
        }

        @Override
        protected void doCommit(DefaultTransactionStatus status) {
            throw new UnsupportedOperationException("Transaction execution is not needed for property customization");
        }

        @Override
        protected void doRollback(DefaultTransactionStatus status) {
            throw new UnsupportedOperationException("Transaction execution is not needed for property customization");
        }

    }

}
