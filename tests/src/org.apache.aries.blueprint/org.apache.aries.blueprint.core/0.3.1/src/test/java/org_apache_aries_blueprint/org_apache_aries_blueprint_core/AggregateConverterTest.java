/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_apache_aries_blueprint.org_apache_aries_blueprint_core;

import java.util.List;
import java.util.Map;

import org.apache.aries.blueprint.container.AggregateConverter;
import org.apache.aries.blueprint.container.GenericType;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class AggregateConverterTest {
    @Test
    void constructsValuesUsingTheirStringConstructor() throws Exception {
        AggregateConverter converter = new AggregateConverter(RecipeSupport.container());
        Label value = (Label) converter.convert("priority", new GenericType(Label.class));
        assertThat(value.text).isEqualTo("priority");
    }

    @Test
    void convertsCollectionsAndArraysElementByElement() throws Exception {
        AggregateConverter converter = new AggregateConverter(RecipeSupport.container());
        Integer[] array = (Integer[]) converter.convert(List.of("12", "34"), new GenericType(Integer[].class));
        assertThat(array).containsExactly(12, 34);
        int[] primitives = (int[]) converter.convert(new String[]{"5", "8"}, new GenericType(int[].class));
        assertThat(primitives).containsExactly(5, 8);
        Object list = converter.convert(new String[]{"21", "22"},
                new GenericType(List.class, new GenericType(Integer.class)));
        assertThat(list).isEqualTo(List.of(21, 22));
        Object map = converter.convert(Map.of("7", "true"),
                new GenericType(Map.class, new GenericType(Integer.class), new GenericType(Boolean.class)));
        assertThat(map).isEqualTo(Map.of(7, true));
    }

    public static class Label {
        private final String text;

        public Label(String text) {
            this.text = text;
        }
    }
}
