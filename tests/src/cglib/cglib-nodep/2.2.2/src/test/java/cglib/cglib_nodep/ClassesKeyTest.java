/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.cglib.core.ClassesKey;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class ClassesKeyTest {
    @Test
    void createsStableKeyFromRuntimeClassesOfArrayElements() {
        try {
            Object key = ClassesKey.create(new Object[] { "first", Integer.valueOf(1), new SampleValue("alpha") });
            Object matchingKey = ClassesKey.create(new Object[] { "second", Integer.valueOf(2), new SampleValue("beta") });
            Object differentKey = ClassesKey.create(new Object[] { "second", Long.valueOf(2L), new SampleValue("beta") });

            assertThat(key).isEqualTo(matchingKey);
            assertThat(key).hasSameHashCodeAs(matchingKey);
            assertThat(key).isNotEqualTo(differentKey);
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        }
    }

    private static boolean isUnsupportedNativeImageDynamicClassLoading(Throwable throwable) {
        Throwable current = throwable;
        while (current != null) {
            if (current instanceof Error && NativeImageSupport.isUnsupportedFeatureError((Error) current)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }

    public static class SampleValue {
        private final String value;

        public SampleValue(String value) {
            this.value = value;
        }

        public String value() {
            return value;
        }
    }
}
