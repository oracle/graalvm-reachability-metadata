/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_google_protobuf.protobuf_java_util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.google.protobuf.NullValue;
import com.google.protobuf.Struct;
import com.google.protobuf.util.Structs;
import com.google.protobuf.util.Values;
import java.util.List;
import org.junit.jupiter.api.Test;

/**
 * Convenience construction of dynamic protobuf values is the configuration subsystem exercised
 * here.
 *
 * A feature service assembles a schema-free rollout configuration from values received from
 * several application sources and exposes the resulting protobuf structure to its callers.
 */
public class DynamicFeatureConfigTest {

    @Test
    void assemblesFeatureConfigurationFromDynamicValues() {
        Struct rollout =
                Structs.of(
                        "enabled", Values.of(true),
                        "percentage", Values.of(25.0),
                        "audiences",
                                Values.of(
                                        List.of(Values.of("beta"), Values.of("internal"))));
        Struct configuration =
                Structs.of(
                        "feature", Values.of("search-v2"),
                        "rollout", Values.of(rollout),
                        "fallback", Values.ofNull());

        assertEquals("search-v2", configuration.getFieldsOrThrow("feature").getStringValue());

        Struct configuredRollout =
                configuration.getFieldsOrThrow("rollout").getStructValue();
        assertTrue(configuredRollout.getFieldsOrThrow("enabled").getBoolValue());
        assertEquals(
                25.0, configuredRollout.getFieldsOrThrow("percentage").getNumberValue());
        assertEquals(
                List.of("beta", "internal"),
                configuredRollout.getFieldsOrThrow("audiences").getListValue().getValuesList()
                        .stream()
                        .map(value -> value.getStringValue())
                        .toList());
        assertEquals(
                NullValue.NULL_VALUE,
                configuration.getFieldsOrThrow("fallback").getNullValue());
    }
}
