/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_cloud.google_cloud_storage;

import static org.assertj.core.api.Assertions.assertThat;

import com.google.cloud.storage.StorageOptions;
import org.junit.jupiter.api.Test;

public class StorageOptionsTest {
    @Test
    public void versionCanBeReadFromFallbackPomPropertiesResource() {
        assertThat(StorageOptions.version()).isEqualTo("fallback-loaded");
    }
}
