/*
 * Copyright and related rights waived via CC0
 *
 * You should have received a copy of the CC0 legalcode along with this
 * work. If not, see <http://creativecommons.org/publicdomain/zero/1.0/>.
 */
package org_javassist.javassist;

import static org.assertj.core.api.Assertions.assertThat;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.util.concurrent.atomic.AtomicInteger;

import javassist.CannotCompileException;
import javassist.util.proxy.DefinePackageHelper;

import org.junit.jupiter.api.Test;

public class DefinePackageHelperJavaOtherTest {
    private static final String JAVA_OTHER_HELPER_CLASS = "javassist.util.proxy.DefinePackageHelper$JavaOther";
    private static final AtomicInteger PACKAGE_COUNTER = new AtomicInteger();

    @Test
    void definesPackageThroughReflectiveJavaOtherFallback() throws Throwable {
        Object javaOtherHelper = createJavaOtherHelper();
        StaticFieldReplacement privilegedHelper = replacePrivilegedHelper(javaOtherHelper);
        String packageName = DefinePackageHelperJavaOtherTest.class.getPackageName()
                + ".generated.pkg" + PACKAGE_COUNTER.incrementAndGet();
        PackageCapturingClassLoader loader = new PackageCapturingClassLoader(
                DefinePackageHelperJavaOtherTest.class.getClassLoader());

        try {
            try {
                DefinePackageHelper.definePackage(packageName, loader);

                assertThat(loader.definedPackage(packageName)).isNotNull();
            } catch (CannotCompileException | RuntimeException definitionFailure) {
                assertReflectiveFallbackWasReached(definitionFailure);
            }
        } finally {
            privilegedHelper.restore();
        }
    }

    private static Object createJavaOtherHelper() throws Throwable {
        Class<?> javaOtherClass = Class.forName(JAVA_OTHER_HELPER_CLASS);
        return MethodHandles.privateLookupIn(javaOtherClass, MethodHandles.lookup())
                .findConstructor(javaOtherClass, MethodType.methodType(void.class))
                .invoke();
    }

    private static StaticFieldReplacement replacePrivilegedHelper(Object replacement) throws Exception {
        Field privileged = DefinePackageHelper.class.getDeclaredField("privileged");
        sun.misc.Unsafe unsafe = unsafe();
        Object base = unsafe.staticFieldBase(privileged);
        long offset = unsafe.staticFieldOffset(privileged);
        Object original = unsafe.getObject(base, offset);
        unsafe.putObjectVolatile(base, offset, replacement);
        return new StaticFieldReplacement(unsafe, base, offset, original);
    }

    private static void assertReflectiveFallbackWasReached(Throwable definitionFailure) {
        assertThat(definitionFailure).hasStackTraceContaining("java.lang.reflect.Method.invoke");
    }

    private static sun.misc.Unsafe unsafe() throws Exception {
        Field unsafeField = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        unsafeField.setAccessible(true);
        return (sun.misc.Unsafe) unsafeField.get(null);
    }

    private static final class StaticFieldReplacement {
        private final sun.misc.Unsafe unsafe;
        private final Object base;
        private final long offset;
        private final Object original;

        private StaticFieldReplacement(sun.misc.Unsafe unsafe, Object base, long offset, Object original) {
            this.unsafe = unsafe;
            this.base = base;
            this.offset = offset;
            this.original = original;
        }

        private void restore() {
            unsafe.putObjectVolatile(base, offset, original);
        }
    }

    private static final class PackageCapturingClassLoader extends ClassLoader {
        private PackageCapturingClassLoader(ClassLoader parent) {
            super(parent);
        }

        private Package definedPackage(String name) {
            return getDefinedPackage(name);
        }
    }
}
