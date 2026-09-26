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
public class MethodReflectiveInvokerTest {
    @Test
    void invokesThePublicSetterForAHeaderCollection() throws Exception {
        HttpHeaders source = new HttpHeaders().set("x-tag-owner", "sdk");

        SetterHeaders decoded = new JacksonAdapter().deserialize(source, SetterHeaders.class);

        assertThat(decoded.tags).containsExactlyEntriesOf(Map.of("owner", "sdk"));
    }

    public static final class SetterHeaders {
        @HeaderCollection("x-tag-")
        private Map<String, String> tags;

        public void setTags(Map<String, String> tags) {
            this.tags = tags;
        }
    }
}
