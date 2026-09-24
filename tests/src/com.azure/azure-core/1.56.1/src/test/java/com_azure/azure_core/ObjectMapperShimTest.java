/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.annotation.HeaderCollection;
import com.azure.core.http.HttpHeaders;
import com.azure.core.util.serializer.JacksonAdapter;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class ObjectMapperShimTest {
    @Test
    void mapsRegularAndCollectionHeadersToStrongType() throws Exception {
        HttpHeaders headers = new HttpHeaders().set("request-id", "42").set("x-detail-color", "blue");

        StrongHeaders decoded = new JacksonAdapter().deserialize(headers, StrongHeaders.class);

        assertThat(decoded.requestId).isEqualTo("42");
        assertThat(decoded.details).containsEntry("color", "blue");
    }

    public static final class StrongHeaders {
        @JsonProperty("request-id")
        private String requestId;

        @HeaderCollection("x-detail-")
        private Map<String, String> details;

        public void setRequestId(String requestId) {
            this.requestId = requestId;
        }

        public void setDetails(Map<String, String> details) {
            this.details = details;
        }
    }
}
