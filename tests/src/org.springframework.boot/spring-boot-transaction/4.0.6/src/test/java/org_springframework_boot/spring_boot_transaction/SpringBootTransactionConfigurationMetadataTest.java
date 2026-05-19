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

import org.junit.jupiter.api.Test;

import org.springframework.boot.transaction.autoconfigure.TransactionProperties;

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

    private static String resourceText(String resourceName) throws IOException {
        ClassLoader classLoader = TransactionProperties.class.getClassLoader();
        try (InputStream inputStream = classLoader.getResourceAsStream(resourceName)) {
            assertThat(inputStream).isNotNull();
            return new String(inputStream.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

}
