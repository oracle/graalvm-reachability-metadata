/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_parquet.parquet_jackson;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import shaded.parquet.com.fasterxml.jackson.annotation.JsonAlias;
import shaded.parquet.com.fasterxml.jackson.annotation.JsonProperty;
import shaded.parquet.com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

public class JacksonAnnotationIntrospectorTest {
    private static final JacksonAnnotationIntrospector INTROSPECTOR = new JacksonAnnotationIntrospector();

    @Test
    void findsAnnotatedEnumNamesAndAliases() {
        assertThat(INTROSPECTOR.findEnumValue(EncodingMode.COMPACT)).isEqualTo("compact-mode");

        EncodingMode[] enumValues = EncodingMode.values();
        String[] names = {"COMPACT", "VERBOSE", "PLAIN"};
        String[] resolvedNames = INTROSPECTOR.findEnumValues(EncodingMode.class, enumValues, names);

        assertThat(resolvedNames).containsExactly("compact-mode", "verbose-mode", "PLAIN");

        String[][] aliases = new String[enumValues.length][];
        INTROSPECTOR.findEnumAliases(EncodingMode.class, enumValues, aliases);

        assertThat(aliases[0]).containsExactly("small", "tiny");
        assertThat(aliases[1]).containsExactly("chatty");
        assertThat(aliases[2]).isNull();
    }

    public enum EncodingMode {
        @JsonProperty("compact-mode")
        @JsonAlias({"small", "tiny"})
        COMPACT,

        @JsonProperty("verbose-mode")
        @JsonAlias("chatty")
        VERBOSE,

        PLAIN
    }
}
