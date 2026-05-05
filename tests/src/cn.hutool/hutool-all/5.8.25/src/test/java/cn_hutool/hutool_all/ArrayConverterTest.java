/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.convert.impl.ArrayConverter;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayConverterTest {

    @Test
    void convertsArraysWithDifferentComponentTypes() {
        ArrayConverter converter = new ArrayConverter(Integer[].class);

        Object converted = converter.convert(new String[] {"1", "2", "3"}, null);

        assertThat(converted).isInstanceOf(Integer[].class);
        assertThat((Integer[]) converted).containsExactly(1, 2, 3);
    }

    @Test
    void convertsListToPrimitiveArray() {
        ArrayConverter converter = new ArrayConverter(int[].class);
        List<String> values = Arrays.asList("4", "5", "6");

        Object converted = converter.convert(values, null);

        assertThat(converted).isInstanceOf(int[].class);
        assertThat((int[]) converted).containsExactly(4, 5, 6);
    }

    @Test
    void convertsNonListCollectionToArray() {
        ArrayConverter converter = new ArrayConverter(Long[].class);
        Set<String> values = new LinkedHashSet<>(Arrays.asList("7", "8", "9"));

        Object converted = converter.convert(values, null);

        assertThat(converted).isInstanceOf(Long[].class);
        assertThat((Long[]) converted).containsExactly(7L, 8L, 9L);
    }

    @Test
    void convertsIterableToArray() {
        ArrayConverter converter = new ArrayConverter(Double[].class);
        Iterable<String> values = new FixedIterable("1.5", "2.5");

        Object converted = converter.convert(values, null);

        assertThat(converted).isInstanceOf(Double[].class);
        assertThat((Double[]) converted).containsExactly(1.5D, 2.5D);
    }

    @Test
    void convertsIteratorToArray() {
        ArrayConverter converter = new ArrayConverter(String[].class);
        Iterator<Integer> values = Arrays.asList(10, 11, 12).iterator();

        Object converted = converter.convert(values, null);

        assertThat(converted).isInstanceOf(String[].class);
        assertThat((String[]) converted).containsExactly("10", "11", "12");
    }

    private static final class FixedIterable implements Iterable<String> {
        private final List<String> values;

        FixedIterable(String... values) {
            this.values = Arrays.asList(values);
        }

        @Override
        public Iterator<String> iterator() {
            return values.iterator();
        }
    }
}
