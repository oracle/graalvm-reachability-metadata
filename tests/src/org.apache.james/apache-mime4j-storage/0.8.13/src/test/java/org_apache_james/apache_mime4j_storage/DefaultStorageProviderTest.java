/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.mime4j.storage.DefaultStorageProvider;
import org.apache.james.mime4j.storage.MemoryStorageProvider;
import org.apache.james.mime4j.storage.Storage;
import org.apache.james.mime4j.storage.StorageProvider;
import org.junit.jupiter.api.Test;

public class DefaultStorageProviderTest {
    private static final String DEFAULT_STORAGE_PROVIDER_PROPERTY =
            "org.apache.james.mime4j.defaultStorageProvider";
    private static final String MEMORY_STORAGE_PROVIDER =
            "org.apache.james.mime4j.storage.MemoryStorageProvider";

    @Test
    void initializesConfiguredStorageProviderThroughSystemProperty() throws Exception {
        System.setProperty(DEFAULT_STORAGE_PROVIDER_PROPERTY, MEMORY_STORAGE_PROVIDER);
        try {
            StorageProvider provider = DefaultStorageProvider.getInstance();

            assertThat(provider).isInstanceOf(MemoryStorageProvider.class);
            byte[] payload = "configured storage provider".getBytes(StandardCharsets.UTF_8);
            Storage storage = provider.store(new ByteArrayInputStream(payload));
            try (InputStream storedContent = storage.getInputStream()) {
                assertThat(storedContent.readAllBytes()).isEqualTo(payload);
            } finally {
                storage.delete();
            }
        } finally {
            System.clearProperty(DEFAULT_STORAGE_PROVIDER_PROPERTY);
        }
    }
}
