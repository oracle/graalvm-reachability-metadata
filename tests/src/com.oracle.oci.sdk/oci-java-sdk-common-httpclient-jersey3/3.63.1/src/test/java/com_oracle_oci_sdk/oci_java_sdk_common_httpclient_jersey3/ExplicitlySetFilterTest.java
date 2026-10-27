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
import com.oracle.bmc.http.client.internal.ExplicitlySetBmcModel;
import com.oracle.bmc.serialization.jackson.JacksonSerializer;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ExplicitlySetFilterTest {
    @Test
    void serializesNonNullAndExplicitNullPropertiesFromCurrentBmcModel() throws Exception {
        CurrentBmcModel model = new CurrentBmcModel();
        model.setPresent("stored");
        model.setExplicitNull(null);

        Map<String, Object> serialized = serializeToMap(model);

        assertThat(serialized)
                .containsEntry("present", "stored")
                .containsEntry("explicitNull", null)
                .doesNotContainKey("absent")
                .doesNotContainKey(ExplicitlySetBmcModel.EXPLICITLY_SET_PROPERTY_NAME);
    }

    @Test
    void serializesExplicitNullPropertiesFromLegacyExplicitSetField() throws Exception {
        LegacyExplicitSetModel model = new LegacyExplicitSetModel("nullableName");

        Map<String, Object> serialized = serializeToMap(model);

        assertThat(serialized)
                .containsEntry("nullableName", null)
                .doesNotContainKey("implicitNull")
                .doesNotContainKey(ExplicitlySetBmcModel.EXPLICITLY_SET_FILTER_NAME);
    }

    @Test
    void matchesFieldByJsonPropertyAnnotationWhenJavaFieldNameDiffersFromJsonName()
            throws Exception {
        JsonPropertyNamedModel model = new JsonPropertyNamedModel();

        Map<String, Object> serialized = serializeToMap(model);

        assertThat(serialized).containsEntry("endpoint_id", "ocid1.endpoint.oc1..example");
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> serializeToMap(Object model) throws IOException {
        String json = JacksonSerializer.getDefaultSerializer().writeValueAsString(model);
        return JacksonSerializer.getDefaultSerializer().readValue(json, Map.class);
    }

    @JsonFilter(ExplicitlySetBmcModel.EXPLICITLY_SET_FILTER_NAME)
    public static final class CurrentBmcModel extends ExplicitlySetBmcModel {
        private String present;
        private String absent;
        private String explicitNull;

        public String getPresent() {
            return present;
        }

        public void setPresent(String present) {
            this.present = present;
            markPropertyAsExplicitlySet("present");
        }

        public String getAbsent() {
            return absent;
        }

        public void setAbsent(String absent) {
            this.absent = absent;
            markPropertyAsExplicitlySet("absent");
        }

        public String getExplicitNull() {
            return explicitNull;
        }

        public void setExplicitNull(String explicitNull) {
            this.explicitNull = explicitNull;
            markPropertyAsExplicitlySet("explicitNull");
        }
    }

    @JsonFilter(ExplicitlySetBmcModel.EXPLICITLY_SET_FILTER_NAME)
    public static final class LegacyExplicitSetModel {
        @JsonIgnore
        private final Set<String> explicitlySetFilter;
        private String nullableName;
        private String implicitNull;

        LegacyExplicitSetModel(String explicitlySetProperty) {
            this.explicitlySetFilter = new HashSet<>(Collections.singleton(explicitlySetProperty));
        }

        public String getNullableName() {
            return nullableName;
        }

        public void setNullableName(String nullableName) {
            this.nullableName = nullableName;
        }

        public String getImplicitNull() {
            return implicitNull;
        }

        public void setImplicitNull(String implicitNull) {
            this.implicitNull = implicitNull;
        }
    }

    @JsonFilter(ExplicitlySetBmcModel.EXPLICITLY_SET_FILTER_NAME)
    public static final class JsonPropertyNamedModel {
        @JsonProperty("endpoint_id")
        private final String storedEndpoint = "ocid1.endpoint.oc1..example";
    }
}
