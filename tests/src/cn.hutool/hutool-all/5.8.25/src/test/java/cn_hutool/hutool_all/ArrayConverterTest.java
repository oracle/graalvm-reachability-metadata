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
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayConverterTest {
    @Test
    public void convertsArrayWithDifferentComponentType() {
        ArrayConverter converter = new ArrayConverter(String[].class);

        Object converted = converter.convert(new Integer[] {1, 2, 3}, null);

        assertThat(converted).isInstanceOf(String[].class);
        assertThat((String[]) converted).containsExactly("1", "2", "3");
    }

    @Test
    public void convertsListToArray() {
        ArrayConverter converter = new ArrayConverter(String[].class);
        List<Integer> source = Arrays.asList(4, 5, 6);

        Object converted = converter.convert(source, null);

        assertThat(converted).isInstanceOf(String[].class);
        assertThat((String[]) converted).containsExactly("4", "5", "6");
    }

    @Test
    public void convertsNonListCollectionToArray() {
        ArrayConverter converter = new ArrayConverter(String[].class);
        Collection<Integer> source = new LinkedHashSet<>(Arrays.asList(7, 8, 9));

        Object converted = converter.convert(source, null);

        assertThat(converted).isInstanceOf(String[].class);
        assertThat((String[]) converted).containsExactly("7", "8", "9");
    }

    @Test
    public void convertsIterableToArray() {
        ArrayConverter converter = new ArrayConverter(String[].class);
        Iterable<Integer> source = new NumberIterable(Arrays.asList(10, 11, 12));

        Object converted = converter.convert(source, null);

        assertThat(converted).isInstanceOf(String[].class);
        assertThat((String[]) converted).containsExactly("10", "11", "12");
    }

    @Test
    public void convertsIteratorToArray() {
        ArrayConverter converter = new ArrayConverter(String[].class);
        Iterator<Integer> source = Arrays.asList(13, 14, 15).iterator();

        Object converted = converter.convert(source, null);

        assertThat(converted).isInstanceOf(String[].class);
        assertThat((String[]) converted).containsExactly("13", "14", "15");
    }

    private static final class NumberIterable implements Iterable<Integer> {
        private final List<Integer> values;

        private NumberIterable(List<Integer> values) {
            this.values = values;
        }

        @Override
        public Iterator<Integer> iterator() {
            return values.iterator();
        }
    }
}
