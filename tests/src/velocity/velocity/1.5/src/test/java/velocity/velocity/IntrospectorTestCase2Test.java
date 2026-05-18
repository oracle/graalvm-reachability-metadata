/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package velocity.velocity;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;

import org.apache.velocity.runtime.log.Log;
import org.apache.velocity.runtime.log.NullLogChute;
import org.apache.velocity.util.introspection.Introspector;
import org.junit.jupiter.api.Test;

public class IntrospectorTestCase2Test {
    @Test
    void resolvesMostSpecificOverloadAndRejectsAmbiguousMatch() throws Exception {
        final Introspector introspector = new Introspector(new Log(new NullLogChute()));

        final Method specificMethod = introspector.getMethod(
                OverloadedTester.class,
                "find",
                new Object[] {Integer.valueOf(7)});
        final Object specificResult = specificMethod.invoke(new OverloadedTester(), Integer.valueOf(7));

        assertThat(specificMethod.getParameterTypes()).containsExactly(Integer.class);
        assertThat(specificResult).isEqualTo("integer:7");
        assertThat(introspector.getMethod(
                AmbiguousTester.class,
                "match",
                new Object[] {new AmbiguousValue()})).isNull();
    }

    public static final class OverloadedTester {
        public String find(final Number value) {
            return "number:" + value;
        }

        public String find(final Integer value) {
            return "integer:" + value;
        }
    }

    public static final class AmbiguousTester {
        public String match(final CharSequence value) {
            return "chars:" + value;
        }

        public String match(final Appendable value) {
            return "appendable:" + value;
        }
    }

    public static final class AmbiguousValue implements CharSequence, Appendable {
        private final StringBuilder text = new StringBuilder("ambiguous");

        @Override
        public int length() {
            return text.length();
        }

        @Override
        public char charAt(final int index) {
            return text.charAt(index);
        }

        @Override
        public CharSequence subSequence(final int start, final int end) {
            return text.subSequence(start, end);
        }

        @Override
        public Appendable append(final CharSequence value) throws IOException {
            text.append(value);
            return this;
        }

        @Override
        public Appendable append(final CharSequence value, final int start, final int end) throws IOException {
            text.append(value, start, end);
            return this;
        }

        @Override
        public Appendable append(final char value) throws IOException {
            text.append(value);
            return this;
        }

        @Override
        public String toString() {
            return text.toString();
        }
    }
}
