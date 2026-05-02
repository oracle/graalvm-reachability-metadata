/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package cglib.cglib_nodep;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import net.sf.cglib.proxy.InterfaceMaker;
import org.graalvm.internal.tck.NativeImageSupport;
import org.junit.jupiter.api.Test;

public class InterfaceMakerTest {
    @Test
    void createsInterfaceFromPublicClassMethods() throws Exception {
        try {
            InterfaceMaker maker = new InterfaceMaker();
            maker.add(GreetingOperations.class);

            Class<?> generatedInterface = maker.create();
            Set<String> generatedMethodNames = methodNames(generatedInterface);

            assertThat(generatedInterface.isInterface()).isTrue();
            assertThat(generatedMethodNames).contains("greet", "count", "reset");
            assertThat(generatedInterface.getMethod("greet", String.class).getReturnType()).isEqualTo(String.class);
            assertThat(generatedInterface.getMethod("count", String.class).getReturnType()).isEqualTo(Integer.TYPE);
            assertThat(generatedInterface.getMethod("reset").getExceptionTypes()).containsExactly(IOException.class);
            assertThat(generatedMethodNames).doesNotContain("toString", "hashCode", "equals", "getClass");
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

    private static Set<String> methodNames(Class<?> type) {
        return Stream.of(type.getMethods())
                .map(Method::getName)
                .collect(Collectors.toSet());
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

    public interface ResettableOperations {
        void reset() throws IOException;
    }

    public static class BaseOperations implements ResettableOperations {
        public void reset() throws IOException {
        }
    }

    public static class GreetingOperations extends BaseOperations {
        public String greet(String name) {
            return "hello " + name;
        }

        public int count(String value) {
            return value.length();
        }
    }
}
