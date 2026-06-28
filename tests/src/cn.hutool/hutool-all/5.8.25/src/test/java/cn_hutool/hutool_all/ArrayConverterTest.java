/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cn_hutool.hutool_all;

import cn.hutool.core.convert.Convert;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

public class ArrayConverterTest {
    @Test
    void convertsArrayValuesToTargetComponentType() {
        Integer[] converted = Convert.convert(Integer[].class, new String[] {"1", "2", "3"});

        assertThat(converted).containsExactly(1, 2, 3);
    }

    @Test
    void convertsListValuesToArray() {
        List<String> source = Arrays.asList("4", "5", "6");

        Integer[] converted = Convert.convert(Integer[].class, source);

        assertThat(converted).containsExactly(4, 5, 6);
    }

    @Test
    void convertsCollectionValuesToArray() {
        Set<String> source = new LinkedHashSet<>(Arrays.asList("7", "8", "9"));

        Integer[] converted = Convert.convert(Integer[].class, source);

        assertThat(converted).containsExactly(7, 8, 9);
    }

    @Test
    void convertsIterableValuesToArray() {
        ReusableIterable source = new ReusableIterable(Arrays.asList("10", "11", "12"));

        Integer[] converted = Convert.convert(Integer[].class, source);

        assertThat(converted).containsExactly(10, 11, 12);
    }

    @Test
    void convertsIteratorValuesToArray() {
        Iterator<String> source = Arrays.asList("13", "14", "15").iterator();

        Integer[] converted = Convert.convert(Integer[].class, source);

        assertThat(converted).containsExactly(13, 14, 15);
    }

    private static final class ReusableIterable implements Iterable<String> {
        private final List<String> values;

        private ReusableIterable(List<String> values) {
            this.values = values;
        }

        @Override
        public Iterator<String> iterator() {
            return values.iterator();
        }
    }
}
