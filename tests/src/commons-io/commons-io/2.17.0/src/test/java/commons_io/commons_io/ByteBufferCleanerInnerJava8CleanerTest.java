/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package commons_io.commons_io;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

public class ByteBufferCleanerInnerJava8CleanerTest {

    @Test
    void invokesConfiguredCleanerMethods() throws Exception {
        final Class<?> cleanerClass = Class.forName("org.apache.commons.io.input.ByteBufferCleaner$Java8Cleaner");
        final Object java8Cleaner = allocateWithoutConstructor(cleanerClass);
        setField(cleanerClass, java8Cleaner, "cleanerMethod", CleanerHooks.class.getMethod("cleaner"));
        setField(cleanerClass, java8Cleaner, "cleanMethod", CleanerProbe.class.getMethod("clean"));
        CleanerHooks.reset();

        final Method clean = cleanerClass.getDeclaredMethod("clean", ByteBuffer.class);
        clean.setAccessible(true);
        clean.invoke(java8Cleaner, ByteBuffer.allocate(1));

        assertThat(CleanerHooks.cleanerInvocations()).isEqualTo(1);
        assertThat(CleanerHooks.cleanInvocations()).isEqualTo(1);
    }

    private static Object allocateWithoutConstructor(final Class<?> type) throws Exception {
        final Class<?> unsafeClass = Class.forName("sun.misc.Unsafe");
        final Field theUnsafe = unsafeClass.getDeclaredField("theUnsafe");
        theUnsafe.setAccessible(true);
        final Object unsafe = theUnsafe.get(null);
        final Method allocateInstance = unsafeClass.getMethod("allocateInstance", Class.class);
        return allocateInstance.invoke(unsafe, type);
    }

    private static void setField(final Class<?> type, final Object target, final String name, final Object value)
            throws ReflectiveOperationException {
        final Field field = type.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    public static final class CleanerHooks {

        private static final AtomicInteger CLEANER_INVOCATIONS = new AtomicInteger();

        private static final CleanerProbe CLEANER = new CleanerProbe();

        private CleanerHooks() {
        }

        public static CleanerProbe cleaner() {
            CLEANER_INVOCATIONS.incrementAndGet();
            return CLEANER;
        }

        private static int cleanerInvocations() {
            return CLEANER_INVOCATIONS.get();
        }

        private static int cleanInvocations() {
            return CLEANER.cleanInvocations();
        }

        private static void reset() {
            CLEANER_INVOCATIONS.set(0);
            CLEANER.reset();
        }
    }

    public static final class CleanerProbe {

        private final AtomicInteger cleanInvocations = new AtomicInteger();

        public void clean() {
            cleanInvocations.incrementAndGet();
        }

        private int cleanInvocations() {
            return cleanInvocations.get();
        }

        private void reset() {
            cleanInvocations.set(0);
        }
    }
}
