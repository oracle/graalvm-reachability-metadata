/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_oracle_oci_sdk.oci_java_sdk_common_httpclient_jersey3;

import com.fasterxml.jackson.annotation.JsonFilter;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.oracle.bmc.serialization.jackson.JacksonSerializer;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Set;

import static com.oracle.bmc.http.client.internal.ExplicitlySetBmcModel.EXPLICITLY_SET_FILTER_NAME;
import static org.assertj.core.api.Assertions.assertThat;

public class ExplicitlySetFilterTest {
    @Test
    void serializesAnnotatedFieldsAndLegacyExplicitNulls() throws Exception {
        JacksonSerializer serializer = JacksonSerializer.getDefaultSerializer();

        String json = serializer.writeValueAsString(new LegacyExplicitlySetModel());
        @SuppressWarnings("unchecked")
        Map<String, Object> fields = serializer.readValue(json, Map.class);

        assertThat(fields.get("resource_id")).isEqualTo("ocid1.test.oc1..example");
        assertThat(fields).containsKey("legacyValue");
        assertThat(fields.get("legacyValue")).isNull();
        assertThat(fields).doesNotContainKey("explicitlySetFilter");
    }

    @JsonFilter(EXPLICITLY_SET_FILTER_NAME)
    private static final class LegacyExplicitlySetModel {
        @JsonProperty("resource_id")
        private final String identifier = "ocid1.test.oc1..example";

        @JsonProperty("legacyValue")
        private final String legacyValue = null;

        @JsonIgnore
        private final Set<String> explicitlySetFilter = Set.of("legacyValue");
    }
}
