/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.cglib.proxy.Mixin;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class MixinEverythingEmitterTest {
    @Test
    void createsMixinFromAllPublicDelegateMethods() {
        try {
            Mixin.Generator generator = new Mixin.Generator();
            generator.setStyle(Mixin.STYLE_EVERYTHING);
            generator.setDelegates(new Object[] { new NamedDelegate(), new CountingDelegate() });

            Object mixin = generator.create();

            assertThat(mixin).isInstanceOf(NamedOperations.class);
            assertThat(mixin).isInstanceOf(CountingOperations.class);
            assertThat(((NamedOperations) mixin).name()).isEqualTo("Ada");
            assertThat(((CountingOperations) mixin).increment()).isEqualTo(1);
            assertThat(((CountingOperations) mixin).increment()).isEqualTo(2);
        } catch (Error error) {
            if (!isUnsupportedNativeImageDynamicClassLoading(error)) {
                throw error;
            }
        } catch (RuntimeException exception) {
            if (!isUnsupportedNativeImageDynamicClassLoading(exception)) {
                throw exception;
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

    public interface NamedOperations {
        String name();
    }

    public interface CountingOperations {
        int increment();
    }

    public static class NamedDelegate implements NamedOperations {
        public String name() {
            return "Ada";
        }

        public final String ignoredFinalMethod() {
            return "final";
        }

        public static String ignoredStaticMethod() {
            return "static";
        }
    }

    public static class CountingDelegate implements CountingOperations {
        private int value;

        public int increment() {
            value++;
            return value;
        }
    }
}
