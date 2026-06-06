/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package io_sundr.sundr_codegen_velocity_nodeps;

import static org.assertj.core.api.Assertions.assertThat;

import io.sundr.deps.org.apache.velocity.runtime.log.Log;
import io.sundr.deps.org.apache.velocity.runtime.log.NullLogChute;
import io.sundr.deps.org.apache.velocity.util.introspection.Info;
import io.sundr.deps.org.apache.velocity.util.introspection.UberspectImpl;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import org.junit.jupiter.api.Test;

public class UberspectImplTest {
    @Test
    void returnsIteratorFromObjectWithPublicIteratorMethod() throws Exception {
        UberspectImpl uberspect = new UberspectImpl();
        uberspect.setLog(new Log(new NullLogChute()));
        Iterator<?> iterator = uberspect.getIterator(
                new IteratorOnlySequence("alpha", "beta", "gamma"),
                new Info("duck-typed-iterator", 1, 1));

        assertThat(iterator).isNotNull();
        assertThat(iterator.next()).isEqualTo("alpha");
        assertThat(iterator.next()).isEqualTo("beta");
        assertThat(iterator.next()).isEqualTo("gamma");
        assertThat(iterator.hasNext()).isFalse();
    }

    public static class IteratorOnlySequence {
        private final List<String> values;

        public IteratorOnlySequence(String... values) {
            this.values = Arrays.asList(values);
        }

        public Iterator<String> iterator() {
            return values.iterator();
        }
    }
}
