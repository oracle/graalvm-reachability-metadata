/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org.immutables.value.internal.$guava$.base;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import sun.misc.Unsafe;

class $ThrowablesTest {
    @Test
    void lazilyReadsStackTraceEntriesThroughInjectedJavaLangAccess() throws Exception {
        final FakeJavaLangAccess fakeJavaLangAccess = new FakeJavaLangAccess();
        final Object originalJavaLangAccess = readStaticField("jla");
        final Method originalGetStackTraceElementMethod = (Method) readStaticField("getStackTraceElementMethod");
        final Method originalGetStackTraceDepthMethod = (Method) readStaticField("getStackTraceDepthMethod");

        try {
            writeStaticFinalField("jla", fakeJavaLangAccess);
            writeStaticFinalField(
                    "getStackTraceElementMethod",
                    FakeJavaLangAccess.class.getMethod("getStackTraceElement", Throwable.class, int.class));
            writeStaticFinalField(
                    "getStackTraceDepthMethod",
                    FakeJavaLangAccess.class.getMethod("getStackTraceDepth", Throwable.class));

            final RuntimeException failure = new RuntimeException("boom");
            final List<StackTraceElement> lazyStackTrace = $Throwables.lazyStackTrace(failure);

            assertThat($Throwables.lazyStackTraceIsLazy()).isTrue();
            assertThat(lazyStackTrace.size()).isEqualTo(failure.getStackTrace().length);
            assertThat(lazyStackTrace.get(0)).isEqualTo(failure.getStackTrace()[0]);
        } finally {
            writeStaticFinalField("jla", originalJavaLangAccess);
            writeStaticFinalField("getStackTraceElementMethod", originalGetStackTraceElementMethod);
            writeStaticFinalField("getStackTraceDepthMethod", originalGetStackTraceDepthMethod);
        }
    }

    private static Object readStaticField(String fieldName) throws ReflectiveOperationException {
        final Field field = $Throwables.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(null);
    }

    @SuppressWarnings("removal")
    private static void writeStaticFinalField(String fieldName, Object value) throws ReflectiveOperationException {
        final Field field = $Throwables.class.getDeclaredField(fieldName);
        final Unsafe unsafe = unsafe();

        unsafe.putObject(unsafe.staticFieldBase(field), unsafe.staticFieldOffset(field), value);
    }

    private static Unsafe unsafe() throws ReflectiveOperationException {
        final Field unsafeField = Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (Unsafe) unsafeField.get(null);
    }
}

final class FakeJavaLangAccess {
    public StackTraceElement getStackTraceElement(Throwable throwable, int index) {
        return throwable.getStackTrace()[index];
    }

    public Integer getStackTraceDepth(Throwable throwable) {
        return throwable.getStackTrace().length;
    }
}
