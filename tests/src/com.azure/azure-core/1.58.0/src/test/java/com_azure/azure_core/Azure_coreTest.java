/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.util.LibraryTelemetryOptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class Azure_coreTest {
    @Test
    void libraryTelemetryOptionsRetainClientIdentityAndSchema() {
        LibraryTelemetryOptions options = new LibraryTelemetryOptions("contoso-storage")
                .setLibraryVersion("client-version")
                .setResourceProviderNamespace("Microsoft.Storage")
                .setSchemaUrl("https://opentelemetry.io/schemas/example");

        assertThat(options.getLibraryName()).isEqualTo("contoso-storage");
        assertThat(options.getLibraryVersion()).isEqualTo("client-version");
        assertThat(options.getResourceProviderNamespace()).isEqualTo("Microsoft.Storage");
        assertThat(options.getSchemaUrl()).isEqualTo("https://opentelemetry.io/schemas/example");
    }
}
