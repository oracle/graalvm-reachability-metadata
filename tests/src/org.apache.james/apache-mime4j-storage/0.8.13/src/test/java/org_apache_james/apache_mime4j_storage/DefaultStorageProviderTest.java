/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_james.apache_mime4j_storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;

import org.apache.james.mime4j.storage.DefaultStorageProvider;
import org.apache.james.mime4j.storage.MemoryStorageProvider;
import org.apache.james.mime4j.storage.Storage;
import org.apache.james.mime4j.storage.StorageProvider;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class DefaultStorageProviderTest {
    private static final String DEFAULT_STORAGE_PROVIDER_PROPERTY = "org.apache.james.mime4j.defaultStorageProvider";

    @Test
    public void getInstanceLoadsConfiguredProviderClass() throws Exception {
        String previousProviderClassName = System.getProperty(DEFAULT_STORAGE_PROVIDER_PROPERTY);
        System.setProperty(DEFAULT_STORAGE_PROVIDER_PROPERTY, MemoryStorageProvider.class.getName());
        try {
            try {
                StorageProvider provider = DefaultStorageProvider.getInstance();
                assertThat(provider).isInstanceOf(MemoryStorageProvider.class);

                Storage storage = provider.store(new ByteArrayInputStream("mime4j".getBytes(StandardCharsets.UTF_8)));
                try {
                    assertThat(storage.getInputStream()).hasContent("mime4j");
                } finally {
                    storage.delete();
                }
            } catch (Error error) {
                if (!NativeImageSupport.isUnsupportedFeatureError(error)) {
                    throw error;
                }
            }
        } finally {
            restoreProviderProperty(previousProviderClassName);
        }
    }

    private static void restoreProviderProperty(String previousProviderClassName) {
        if (previousProviderClassName == null) {
            System.clearProperty(DEFAULT_STORAGE_PROVIDER_PROPERTY);
        } else {
            System.setProperty(DEFAULT_STORAGE_PROVIDER_PROPERTY, previousProviderClassName);
        }
    }
}
