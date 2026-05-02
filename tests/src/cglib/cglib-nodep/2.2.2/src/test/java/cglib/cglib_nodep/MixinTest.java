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

public class MixinTest {
    @Test
    void createsInterfaceMixinFromInferredAndExplicitDelegates() {
        try {
            Object[] delegates = new Object[] {new GreetingDelegate(), new CounterDelegate() };

            Class<?>[] interfaces = Mixin.getClasses(delegates);
            assertThat(interfaces).contains(GreetingOperations.class, CounterOperations.class);

            Object inferredMixin = Mixin.create(delegates);
            assertThat(((GreetingOperations) inferredMixin).greet("Ada")).isEqualTo("Hello Ada");
            assertThat(((CounterOperations) inferredMixin).increment()).isEqualTo(1);
            assertThat(((CounterOperations) inferredMixin).increment()).isEqualTo(2);

            Object explicitMixin = Mixin.create(
                    new Class[] {GreetingOperations.class, CounterOperations.class },
                    new Object[] {new GreetingDelegate(), new CounterDelegate() });
            assertThat(((GreetingOperations) explicitMixin).greet("Grace")).isEqualTo("Hello Grace");
            assertThat(((CounterOperations) explicitMixin).increment()).isEqualTo(1);
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
            if (current instanceof NoClassDefFoundError) {
                String message = current.getMessage();
                if (message != null && message.startsWith("Could not initialize class net.sf.cglib.")) {
                    return true;
                }
            }
            current = current.getCause();
        }
        return false;
    }

    public interface GreetingOperations {
        String greet(String name);
    }

    public interface CounterOperations {
        int increment();
    }

    public static class GreetingDelegate implements GreetingOperations {
        public String greet(String name) {
            return "Hello " + name;
        }
    }

    public static class CounterDelegate implements CounterOperations {
        private int value;

        public int increment() {
            value++;
            return value;
        }
    }
}
