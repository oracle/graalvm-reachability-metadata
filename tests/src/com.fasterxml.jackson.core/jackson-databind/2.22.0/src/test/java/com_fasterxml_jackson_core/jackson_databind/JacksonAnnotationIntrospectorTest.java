/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package com_fasterxml_jackson_core.jackson_databind;

import com.fasterxml.jackson.annotation.JsonAlias;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.introspect.JacksonAnnotationIntrospector;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class JacksonAnnotationIntrospectorTest {

    @Test
    @SuppressWarnings("deprecation")
    void findsExplicitNamesDeclaredOnEnumFields() {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Enum<?>[] values = Beverage.values();
        String[] names = new String[] {"TEA", "COFFEE", "WATER"};

        String[] found = introspector.findEnumValues(Beverage.class, values, names);

        assertThat(found).isSameAs(names);
        assertThat(found).containsExactly("tea", "COFFEE", "");
    }

    @Test
    @SuppressWarnings("deprecation")
    void findsAliasesDeclaredOnEnumFields() {
        JacksonAnnotationIntrospector introspector = new JacksonAnnotationIntrospector();
        Enum<?>[] values = Beverage.values();
        String[][] aliases = new String[values.length][];

        introspector.findEnumAliases(Beverage.class, values, aliases);

        assertThat(aliases[Beverage.TEA.ordinal()]).containsExactly("chai", "infusion");
        assertThat(aliases[Beverage.COFFEE.ordinal()]).isNull();
        assertThat(aliases[Beverage.WATER.ordinal()]).containsExactly("aqua");
    }

    private enum Beverage {
        @JsonProperty("tea")
        @JsonAlias({"chai", "infusion"})
        TEA,

        COFFEE,

        @JsonProperty("")
        @JsonAlias("aqua")
        WATER
    }
}
