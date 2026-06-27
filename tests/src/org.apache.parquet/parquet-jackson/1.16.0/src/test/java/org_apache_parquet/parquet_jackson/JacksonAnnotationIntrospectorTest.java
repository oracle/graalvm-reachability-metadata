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
import shaded.parquet.com.fasterxml.jackson.databind.ObjectMapper;
import shaded.parquet.com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;

public class JacksonAnnotationIntrospectorTest {
    @Test
    void readsJsonPropertyAndAliasAnnotationsFromEnumConstants() {
        final JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        final AnnotatedEnum[] enumValues = AnnotatedEnum.values();
        final String[] names = {"ALPHA", "BETA"};
        final String[][] aliases = new String[enumValues.length][];

        assertThat(introspector.findEnumValue(AnnotatedEnum.ALPHA)).isEqualTo("alpha-json");
        assertThat(introspector.findEnumValues(AnnotatedEnum.class, enumValues, names))
                .containsExactly("alpha-json", "BETA");

        introspector.findEnumAliases(AnnotatedEnum.class, enumValues, aliases);

        assertThat(aliases[0]).containsExactly("alpha-alias", "first-alias");
        assertThat(aliases[1]).isNull();
    }

    @Test
    void objectMapperUsesAnnotatedEnumNamesAndAliases() throws Exception {
        final ObjectMapper mapper = new ObjectMapper();

        assertThat(mapper.writeValueAsString(AnnotatedEnum.ALPHA)).isEqualTo("\"alpha-json\"");
        assertThat(mapper.readValue("\"alpha-json\"", AnnotatedEnum.class)).isEqualTo(AnnotatedEnum.ALPHA);
        assertThat(mapper.readValue("\"alpha-alias\"", AnnotatedEnum.class)).isEqualTo(AnnotatedEnum.ALPHA);
        assertThat(mapper.readValue("\"BETA\"", AnnotatedEnum.class)).isEqualTo(AnnotatedEnum.BETA);
    }

    public enum AnnotatedEnum {
        @JsonProperty("alpha-json")
        @JsonAlias({"alpha-alias", "first-alias"})
        ALPHA,

        BETA
    }
}
