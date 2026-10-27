/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.http.HttpHeaders;
import com.azure.core.http.HttpMethod;
import com.azure.core.http.HttpRequest;
import com.azure.core.http.rest.PagedIterable;
import com.azure.core.http.rest.PagedResponse;
import com.azure.core.http.rest.PagedResponseBase;
import com.azure.core.util.LibraryTelemetryOptions;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
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

    @Test
    void pagedIterableTraversesContinuationTokens() {
        HttpRequest request = new HttpRequest(HttpMethod.GET, "https://example.test/widgets");
        AtomicInteger nextPageCalls = new AtomicInteger();
        PagedIterable<String> widgets = new PagedIterable<>(
                () -> page(request, List.of("alpha", "beta"), "page-2"),
                continuationToken -> {
                    nextPageCalls.incrementAndGet();
                    assertThat(continuationToken).isEqualTo("page-2");
                    return page(request, List.of("gamma"), null);
                });

        assertThat(widgets.stream()).containsExactly("alpha", "beta", "gamma");
        assertThat(nextPageCalls).hasValue(1);
    }

    private static PagedResponse<String> page(
            HttpRequest request, List<String> values, String continuationToken) {
        return new PagedResponseBase<>(request, 200, new HttpHeaders(), values, continuationToken, null);
    }
}
