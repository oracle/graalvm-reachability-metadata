/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.commons.collections.IteratorUtils;
import java.util.Arrays;
import java.util.Iterator;
import org.junit.jupiter.api.Test;

public class IteratorUtilsTest {

    @Test
    public void toArrayCreatesArrayWithRequestedComponentType() {
        Iterator<String> source = Arrays.asList("alpha", "beta").iterator();

        Object[] result = IteratorUtils.toArray(source, String.class);

        assertThat(result).isExactlyInstanceOf(String[].class);
        assertThat(result).containsExactly("alpha", "beta");
    }

    @Test
    public void getIteratorUsesPublicIteratorMethodWhenObjectIsNotAStandardContainer() {
        Iterator<?> result = IteratorUtils.getIterator(new IteratorMethodContainer("left", "right"));

        assertThat(IteratorUtils.toList(result)).containsExactly("left", "right");
    }

    public static class IteratorMethodContainer {
        private final String[] values;

        public IteratorMethodContainer(String... values) {
            this.values = values;
        }

        public Iterator<String> iterator() {
            return Arrays.asList(values).iterator();
        }
    }
}
