/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_springframework_boot.spring_boot_autoconfigure;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.LongSerializationPolicy;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

import org.springframework.boot.autoconfigure.gson.GsonAutoConfiguration;
import org.springframework.boot.autoconfigure.gson.GsonBuilderCustomizer;
import org.springframework.boot.autoconfigure.gson.GsonProperties;

import static org.assertj.core.api.Assertions.assertThat;

@Timeout(120)
public class GsonAutoConfigurationTest {

    @Test
    void standardPropertiesCustomizeJsonSerialization() {
        GsonAutoConfiguration configuration = new GsonAutoConfiguration();
        GsonProperties properties = new GsonProperties();
        properties.setSerializeNulls(true);
        properties.setDisableHtmlEscaping(true);
        properties.setLongSerializationPolicy(LongSerializationPolicy.STRING);
        GsonBuilderCustomizer standard = configuration.standardGsonBuilderCustomizer(properties);
        GsonBuilder builder = configuration.gsonBuilder(List.of(standard));
        Gson gson = configuration.gson(builder);
        Map<String, Object> value = new LinkedHashMap<>();
        value.put("label", "<orchard>");
        value.put("missing", null);
        value.put("count", 12L);

        String json = gson.toJson(value);

        assertThat(json).isEqualTo("{\"label\":\"<orchard>\",\"missing\":null,\"count\":\"12\"}");
        assertThat(gson.fromJson(json, Map.class)).containsEntry("label", "<orchard>")
                .containsEntry("missing", null).containsEntry("count", "12");
    }

    @Test
    void builderCustomizersApplyInSuppliedOrder() {
        GsonAutoConfiguration configuration = new GsonAutoConfiguration();
        GsonBuilder builder = configuration.gsonBuilder(List.of(
                candidate -> candidate.setLongSerializationPolicy(LongSerializationPolicy.STRING),
                candidate -> candidate.setLongSerializationPolicy(LongSerializationPolicy.DEFAULT)));
        Gson gson = configuration.gson(builder);

        assertThat(gson.toJson(Map.of("count", 12L))).isEqualTo("{\"count\":12}");
    }
}
