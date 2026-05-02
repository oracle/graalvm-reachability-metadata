/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;

import net.sf.cglib.reflect.MethodDelegate;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class MethodDelegateInnerGeneratorTest {
    @Test
    void createsDelegatesForInstanceAndStaticMethods() {
        try {
            GreetingTarget target = new GreetingTarget("Hello");
            GreetingDelegate instanceDelegate = (GreetingDelegate) MethodDelegate.create(
                    target,
                    "formatGreeting",
                    GreetingDelegate.class);

            assertThat(instanceDelegate.greet("Ada", 3)).isEqualTo("Hello Ada #3");
            assertThat(((MethodDelegate) instanceDelegate).getTarget()).isSameAs(target);

            GreetingDelegate staticDelegate = (GreetingDelegate) MethodDelegate.createStatic(
                    GreetingTarget.class,
                    "formatStaticGreeting",
                    GreetingDelegate.class);

            assertThat(staticDelegate.greet("Grace", 2)).isEqualTo("Static Grace #2");
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

    public interface GreetingDelegate {
        String greet(String name, int repetition);
    }

    public static class GreetingTarget {
        private final String prefix;

        GreetingTarget(String prefix) {
            this.prefix = prefix;
        }

        public String formatGreeting(String name, int repetition) {
            return prefix + " " + name + " #" + repetition;
        }

        public static String formatStaticGreeting(String name, int repetition) {
            return "Static " + name + " #" + repetition;
        }
    }
}
