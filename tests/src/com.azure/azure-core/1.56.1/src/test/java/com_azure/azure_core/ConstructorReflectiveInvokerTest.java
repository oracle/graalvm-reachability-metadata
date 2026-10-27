/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_azure.azure_core;

import static org.assertj.core.api.Assertions.assertThat;

import com.azure.core.http.HttpHeaders;
import com.azure.core.util.serializer.JacksonAdapter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class ConstructorReflectiveInvokerTest {
    @Test
    void deserializesHeadersThroughTheStrongTypeConstructor() throws Exception {
        HttpHeaders source = new HttpHeaders().set("etag", "widget-7");

        ConstructedHeaders decoded = new JacksonAdapter().deserialize(source, ConstructedHeaders.class);

        assertThat(decoded.etag).isEqualTo("widget-7");
    }

    public static final class ConstructedHeaders {
        private final String etag;

        public ConstructedHeaders(HttpHeaders headers) {
            this.etag = headers.getValue("etag");
        }
    }
}
