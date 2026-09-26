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
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(60)
public class HeaderCollectionHandlerTest {
    @Test
    void deserializesPrefixedHeadersIntoCollectionField() throws Exception {
        HttpHeaders headers = new HttpHeaders().set("x-meta-region", "west").set("x-meta-tier", "gold");

        HeaderBag decoded = new JacksonAdapter().deserialize(headers, HeaderBag.class);

        assertThat(decoded.metadata).containsExactlyInAnyOrderEntriesOf(Map.of("region", "west", "tier", "gold"));
    }

    public static final class HeaderBag {
        @HeaderCollection("x-meta-")
        private Map<String, String> metadata;
    }
}
